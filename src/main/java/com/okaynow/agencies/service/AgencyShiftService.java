package com.okaynow.agencies.service;

import com.okaynow.agencies.dto.AgencyShiftAssignmentResponse;
import com.okaynow.agencies.dto.AgencyShiftCardResponse;
import com.okaynow.agencies.dto.AssignAgencyShiftRequest;
import com.okaynow.agencies.dto.BroadcastAgencyShiftRequest;
import com.okaynow.agencies.dto.BroadcastAgencyShiftResponse;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.roster.service.AgencyRosterService;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.dto.ShiftResponses;
import com.okaynow.shifts.mapper.ShiftMapper;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgencyShiftService {

    private static final Set<ShiftClaimStatus> ACTIVE_ASSIGNMENTS =
            EnumSet.of(ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED);

    private final AgencyAccessService agencyAccessService;
    private final ShiftRepository shiftRepository;
    private final ShiftMapper shiftMapper;
    private final BookingService bookingService;
    private final AgencyRosterService agencyRosterService;
    private final UserRepository userRepository;
    private final AgencyShiftRoutingService agencyShiftRoutingService;
    private final ShiftClaimRepository shiftClaimRepository;

    @Transactional(readOnly = true)
    public List<AgencyShiftCardResponse> listForAgency(UUID agencyUserId) {
        UUID agencyId = agencyAccessService.requireAgencyForUser(agencyUserId).getId();
        List<Shift> shifts = shiftRepository.findOpenOrAssignedByAgencyId(agencyId, LocalDate.now());
        List<UUID> ids = shifts.stream().map(Shift::getId).toList();
        Map<UUID, List<ShiftClaim>> claimsByShift = ids.isEmpty()
                ? Map.of()
                : shiftClaimRepository.findByShiftIdInAndStatusIn(ids, ACTIVE_ASSIGNMENTS).stream()
                        .collect(Collectors.groupingBy(c -> c.getShift().getId()));
        return shifts.stream()
                .map(shift -> new AgencyShiftCardResponse(
                        ShiftResponses.forViewer(shiftMapper.toResponse(shift), Role.AGENCY_ADMIN),
                        claimsByShift.getOrDefault(shift.getId(), List.of()).stream()
                                .map(AgencyShiftService::toAssignment)
                                .toList()))
                .toList();
    }

    @Transactional
    public ShiftClaimResponse assign(UUID agencyUserId, UUID shiftId, AssignAgencyShiftRequest request) {
        UUID agencyId = agencyAccessService.requireAgencyForUser(agencyUserId).getId();
        agencyAccessService.assertAgencyAllowsWrites(
                agencyAccessService.requireAgencyForUser(agencyUserId));
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getAgencyId() == null || !shift.getAgencyId().equals(agencyId)) {
            throw new ResourceNotFoundException("Shift not found");
        }
        agencyRosterService.assertActiveOnRoster(agencyId, request.caregiverProfileId());
        User actor = userRepository.findById(agencyUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ShiftClaimResponse claim = bookingService.assign(shiftId, request.caregiverProfileId(), actor.getEmail());
        Shift updated = shiftRepository.findById(shiftId).orElseThrow();
        if (updated.getStatus() == ShiftStatus.DRAFT) {
            updated.setStatus(ShiftStatus.CONFIRMED);
            shiftRepository.save(updated);
        }
        return claim;
    }

    @Transactional
    public ShiftClaimResponse unassign(UUID agencyUserId, UUID shiftId, UUID caregiverProfileId) {
        UUID agencyId = agencyAccessService.requireAgencyForUser(agencyUserId).getId();
        agencyAccessService.assertAgencyAllowsWrites(
                agencyAccessService.requireAgencyForUser(agencyUserId));
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getAgencyId() == null || !shift.getAgencyId().equals(agencyId)) {
            throw new ResourceNotFoundException("Shift not found");
        }
        User actor = userRepository.findById(agencyUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return bookingService.unassign(shiftId, caregiverProfileId, actor.getEmail());
    }

    @Transactional
    public BroadcastAgencyShiftResponse broadcast(
            UUID agencyUserId, UUID shiftId, BroadcastAgencyShiftRequest request) {
        return agencyShiftRoutingService.broadcast(agencyUserId, shiftId, request);
    }

    private static AgencyShiftAssignmentResponse toAssignment(ShiftClaim claim) {
        CaregiverProfile caregiver = claim.getCaregiverProfile();
        return new AgencyShiftAssignmentResponse(
                claim.getId(),
                caregiver.getId(),
                caregiver.getFirstName(),
                caregiver.getLastName(),
                claim.getStatus());
    }
}
