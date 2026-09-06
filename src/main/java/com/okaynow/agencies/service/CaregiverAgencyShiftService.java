package com.okaynow.agencies.service;

import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.GeoUtils;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.roster.repository.AgencyCaregiverRepository;
import com.okaynow.evv.support.ShiftWindows;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.dto.ShiftResponses;
import com.okaynow.shifts.mapper.ShiftMapper;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.shifts.service.ShiftAgencyLabelService;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.repository.CaregiverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverAgencyShiftService {

    private final CaregiverProfileRepository caregiverProfileRepository;
    private final AgencyCaregiverRepository agencyCaregiverRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftMapper shiftMapper;
    private final ShiftAgencyLabelService shiftAgencyLabelService;
    private final BookingService bookingService;

    @Transactional(readOnly = true)
    public List<ShiftResponse> listOpenRosterShifts(UUID caregiverUserId, UUID filterAgencyId) {
        CaregiverProfile caregiver = caregiverProfileRepository.findByUserId(caregiverUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        if (!caregiver.isAgencyRosterEnabled()) {
            return List.of();
        }
        List<UUID> agencyIds = agencyCaregiverRepository
                .findByCaregiverProfileIdOrderByInvitedAtDesc(caregiver.getId()).stream()
                .filter(r -> r.getStatus() == AgencyCaregiverStatus.ACTIVE)
                .map(r -> r.getAgency().getId())
                .filter(id -> filterAgencyId == null || filterAgencyId.equals(id))
                .distinct()
                .toList();
        if (agencyIds.isEmpty()) {
            return List.of();
        }
        // MA "today", plus yesterday so overnight shifts (e.g. 22:00–09:00) stay visible
        // until their window ends. Server UTC midnight must not hide same-evening opens.
        LocalDate fromDate = LocalDate.now(ShiftWindows.ZONE).minusDays(1);
        Instant now = Instant.now();
        List<Shift> open = shiftRepository.findOpenRosterBroadcastForAgencies(agencyIds, fromDate)
                .stream()
                .filter(shift -> !ShiftWindows.endInstant(shift).isBefore(now))
                .filter(shift -> isEligibleInArea(caregiver, shift))
                .toList();
        Map<UUID, String> names = shiftAgencyLabelService.namesFor(
                open.stream().map(Shift::getAgencyId).filter(Objects::nonNull).toList());
        return open.stream()
                .map(shift -> {
                    ShiftResponse labeled = shiftAgencyLabelService.label(
                            shift, shiftMapper.toResponse(shift), names);
                    return ShiftResponses.forViewer(labeled, Role.CAREGIVER);
                })
                .toList();
    }

    @Transactional
    public ShiftClaimResponse claimOpenRosterShift(UUID caregiverUserId, UUID shiftId) {
        CaregiverProfile caregiver = caregiverProfileRepository.findByUserId(caregiverUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        return bookingService.claimAgencyRosterShift(shiftId, caregiver.getUser().getEmail());
    }

    private boolean isEligibleInArea(CaregiverProfile caregiver, Shift shift) {
        if (shift.getLat() == null || shift.getLng() == null) {
            return true;
        }
        if (caregiver.getHomeLat() == null || caregiver.getHomeLng() == null) {
            return false;
        }
        int radius = caregiver.getServiceRadiusMiles() != null ? caregiver.getServiceRadiusMiles() : 15;
        return GeoUtils.withinRadiusMiles(
                caregiver.getHomeLat(), caregiver.getHomeLng(),
                shift.getLat(), shift.getLng(), radius);
    }
}
