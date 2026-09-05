package com.okaynow.shifts.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.connections.service.HomeAgencyConnectionService;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.roster.repository.AgencyCaregiverRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.dto.ScheduleDayResponse;
import com.okaynow.shifts.dto.ScheduleRosterSlotResponse;
import com.okaynow.shifts.dto.ScheduleShiftCardResponse;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.shifts.repository.ShiftSpecifications;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleCalendarService {

    private static final Set<ShiftClaimStatus> ROSTER_STATUSES =
            EnumSet.of(ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED);
    private static final Set<ShiftStatus> TERMINAL =
            EnumSet.of(
                    ShiftStatus.COMPLETED,
                    ShiftStatus.CANCELLED,
                    ShiftStatus.NO_SHOW,
                    ShiftStatus.EXPIRED);

    private final ShiftRepository shiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final ShiftService shiftService;
    private final AgencyAccessService agencyAccessService;
    private final HomeAgencyConnectionService homeAgencyConnectionService;
    private final AgencyCaregiverRepository agencyCaregiverRepository;

    @Transactional
    public List<ScheduleDayResponse> calendar(
            LocalDate from,
            LocalDate to,
            UUID clientProfileIdFilter,
            UUID facilityProfileIdFilter,
            User actor) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("to must be on or after from");
        }
        if (from.plusDays(62).isBefore(to)) {
            throw new BadRequestException("Calendar range cannot exceed 62 days");
        }

        UUID clientProfileId = clientProfileIdFilter;
        UUID facilityProfileId = facilityProfileIdFilter;
        Specification<Shift> ownership = null;

        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            if (!client.isCanViewShifts()) {
                throw new AccessDeniedException("You do not have permission to view shifts");
            }
            clientProfileId = client.getId();
            facilityProfileId = null;
        } else if (actor.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            ownership = ShiftSpecifications.ownedByFacility(facility.getId(), actor.getId());
            clientProfileId = null;
            facilityProfileId = facility.getId();
        } else if (actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Schedule calendar is for clients, facilities, and admins");
        }

        // Roll open-ended daily routines forward (no end date) and fill from roster.
        shiftService.ensureOpenEndedCoverage(
                from, to, clientProfileId, facilityProfileId, actor);

        Specification<Shift> filters = ShiftSpecifications.withFilters(
                null, null, from, to, clientProfileId, facilityProfileId,
                null, null, "billRate", null);
        if (ownership != null) {
            filters = filters.and(ownership);
        }

        List<Shift> shifts = shiftRepository.findAll(
                filters,
                PageRequest.of(0, 500, Sort.by("date", "startTime"))).getContent();

        Map<UUID, List<ShiftClaim>> claimsByShift = Map.of();
        if (!shifts.isEmpty()) {
            List<UUID> ids = shifts.stream().map(Shift::getId).toList();
            claimsByShift = shiftClaimRepository.findByShiftIdInAndStatusIn(ids, ROSTER_STATUSES)
                    .stream()
                    .collect(Collectors.groupingBy(c -> c.getShift().getId()));
        }

        Map<UUID, String> clientLabels = new LinkedHashMap<>();
        if (actor.getRole() == Role.ADMIN) {
            Set<UUID> clientIds = shifts.stream()
                    .map(Shift::getClientProfileId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            for (UUID id : clientIds) {
                clientProfileRepository.findById(id).ifPresent(c ->
                        clientLabels.put(id, c.getFirstName() + " " + c.getLastName()));
            }
        }

        Map<LocalDate, List<ScheduleShiftCardResponse>> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new ArrayList<>());
        }

        for (Shift shift : shifts) {
            List<ShiftClaim> claims = claimsByShift.getOrDefault(shift.getId(), List.of());
            int required = Math.max(1, shift.getRequiredHeadcount());
            int filled = shift.getFilledSlots();
            int open = Math.max(0, required - filled);
            // Coverage is opt-in: client/admin must click to open the marketplace.
            boolean needsCoverage = shift.isMarketplacePosted()
                    && shift.getMarketplaceSlots() > 0
                    && !TERMINAL.contains(shift.getStatus());

            if (shift.getStatus() == ShiftStatus.CANCELLED) {
                continue;
            }

            List<ScheduleRosterSlotResponse> roster = claims.stream()
                    .map(this::toVisibleRosterSlot)
                    .toList();

            ScheduleShiftCardResponse card = new ScheduleShiftCardResponse(
                    shift.getId(),
                    shift.getClientProfileId(),
                    shift.getClientProfileId() != null
                            ? clientLabels.getOrDefault(shift.getClientProfileId(), null)
                            : null,
                    shift.getRequiredQualification(),
                    shift.getStartTime(),
                    shift.getEndTime(),
                    shift.getStatus(),
                    shift.getScheduleType(),
                    shift.getSeriesId(),
                    required,
                    filled,
                    open,
                    shift.isMarketplacePosted(),
                    shift.getMarketplaceSlots(),
                    needsCoverage,
                    shift.getNotes(),
                    roster,
                    false,
                    shift.isAgencyCoverageRequested());

            byDay.computeIfAbsent(shift.getDate(), ignored -> new ArrayList<>()).add(card);
        }

        return byDay.entrySet().stream()
                .map(e -> new ScheduleDayResponse(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional
    public List<ScheduleDayResponse> agencyCalendar(
            LocalDate from,
            LocalDate to,
            UUID clientProfileId,
            UUID facilityProfileId,
            User actor) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to dates are required");
        }
        if (clientProfileId == null && facilityProfileId == null) {
            throw new BadRequestException("clientProfileId or facilityProfileId is required");
        }
        if (clientProfileId != null && facilityProfileId != null) {
            throw new BadRequestException("Provide only one of clientProfileId or facilityProfileId");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("to must be on or after from");
        }
        if (from.plusDays(62).isBefore(to)) {
            throw new BadRequestException("Calendar range cannot exceed 62 days");
        }
        if (actor.getRole() != Role.AGENCY_ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Agency schedule calendar is for agency admins");
        }

        Agency agency = agencyAccessService.requireAgencyForUser(actor.getId());
        if (clientProfileId != null) {
            homeAgencyConnectionService.assertActiveConnectionForClientProfile(
                    agency.getId(), clientProfileId);
        } else {
            homeAgencyConnectionService.assertActiveConnectionForFacilityProfile(
                    agency.getId(), facilityProfileId);
        }

        Set<UUID> agencyCaregiverIds = agencyCaregiverRepository
                .findByAgencyIdOrderByInvitedAtDesc(agency.getId())
                .stream()
                .filter(entry -> entry.getStatus() == AgencyCaregiverStatus.ACTIVE)
                .map(entry -> entry.getCaregiverProfile().getId())
                .collect(Collectors.toSet());

        shiftService.ensureOpenEndedCoverage(from, to, clientProfileId, facilityProfileId, actor);

        Specification<Shift> filters = ShiftSpecifications.withFilters(
                null, null, from, to, clientProfileId, facilityProfileId,
                null, null, "billRate", null);

        List<Shift> shifts = shiftRepository.findAll(
                filters,
                PageRequest.of(0, 500, Sort.by("date", "startTime"))).getContent();

        Map<UUID, List<ShiftClaim>> claimsByShift = Map.of();
        if (!shifts.isEmpty()) {
            List<UUID> ids = shifts.stream().map(Shift::getId).toList();
            claimsByShift = shiftClaimRepository.findByShiftIdInAndStatusIn(ids, ROSTER_STATUSES)
                    .stream()
                    .collect(Collectors.groupingBy(c -> c.getShift().getId()));
        }

        Map<LocalDate, List<ScheduleShiftCardResponse>> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new ArrayList<>());
        }

        for (Shift shift : shifts) {
            List<ShiftClaim> claims = claimsByShift.getOrDefault(shift.getId(), List.of());
            int required = Math.max(1, shift.getRequiredHeadcount());
            int filled = shift.getFilledSlots();
            int open = Math.max(0, required - filled);
            boolean needsCoverage = shift.isMarketplacePosted()
                    && shift.getMarketplaceSlots() > 0
                    && !TERMINAL.contains(shift.getStatus());

            if (shift.getStatus() == ShiftStatus.CANCELLED) {
                continue;
            }

            List<ScheduleRosterSlotResponse> roster = claims.stream()
                    .map(claim -> toAgencyRosterSlot(claim, agencyCaregiverIds))
                    .toList();

            ScheduleShiftCardResponse card = new ScheduleShiftCardResponse(
                    shift.getId(),
                    shift.getClientProfileId(),
                    null,
                    shift.getRequiredQualification(),
                    shift.getStartTime(),
                    shift.getEndTime(),
                    shift.getStatus(),
                    shift.getScheduleType(),
                    shift.getSeriesId(),
                    required,
                    filled,
                    open,
                    shift.isMarketplacePosted(),
                    shift.getMarketplaceSlots(),
                    needsCoverage,
                    shift.getNotes(),
                    roster,
                    shift.getAgencyId() != null && shift.getAgencyId().equals(agency.getId()),
                    shift.isAgencyCoverageRequested());

            byDay.computeIfAbsent(shift.getDate(), ignored -> new ArrayList<>()).add(card);
        }

        return byDay.entrySet().stream()
                .map(e -> new ScheduleDayResponse(e.getKey(), e.getValue()))
                .toList();
    }

    private ScheduleRosterSlotResponse toVisibleRosterSlot(ShiftClaim claim) {
        CaregiverProfile caregiver = claim.getCaregiverProfile();
        return ScheduleRosterSlotResponse.visible(
                claim.getId(),
                caregiver.getId(),
                caregiver.getFirstName(),
                caregiver.getLastName(),
                claim.getStatus(),
                claim.getSource(),
                caregiver.getProfilePhotoUrl());
    }

    private ScheduleRosterSlotResponse toAgencyRosterSlot(ShiftClaim claim, Set<UUID> agencyCaregiverIds) {
        CaregiverProfile caregiver = claim.getCaregiverProfile();
        if (agencyCaregiverIds.contains(caregiver.getId())) {
            return ScheduleRosterSlotResponse.visible(
                    claim.getId(),
                    caregiver.getId(),
                    caregiver.getFirstName(),
                    caregiver.getLastName(),
                    claim.getStatus(),
                    claim.getSource(),
                    caregiver.getProfilePhotoUrl());
        }
        return ScheduleRosterSlotResponse.masked(claim.getId(), claim.getStatus(), claim.getSource());
    }
}
