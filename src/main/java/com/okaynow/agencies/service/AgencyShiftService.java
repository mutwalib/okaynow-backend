package com.okaynow.agencies.service;

import com.okaynow.agencies.dto.AssignAgencyShiftRequest;
import com.okaynow.agencies.dto.BroadcastAgencyShiftRequest;
import com.okaynow.agencies.dto.BroadcastAgencyShiftResponse;
import com.okaynow.agencies.service.AgencyShiftRoutingService;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.roster.service.AgencyRosterService;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.dto.ShiftResponses;
import com.okaynow.shifts.mapper.ShiftMapper;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencyShiftService {

    private final AgencyAccessService agencyAccessService;
    private final ShiftRepository shiftRepository;
    private final ShiftMapper shiftMapper;
    private final BookingService bookingService;
    private final AgencyRosterService agencyRosterService;
    private final UserRepository userRepository;
    private final AgencyShiftRoutingService agencyShiftRoutingService;

    @Transactional(readOnly = true)
    public List<ShiftResponse> listForAgency(UUID agencyUserId) {
        UUID agencyId = agencyAccessService.requireAgencyForUser(agencyUserId).getId();
        return shiftRepository.findOpenOrAssignedByAgencyId(agencyId).stream()
                .map(shiftMapper::toResponse)
                .map(r -> ShiftResponses.forViewer(r, Role.AGENCY_ADMIN))
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
    public BroadcastAgencyShiftResponse broadcast(
            UUID agencyUserId, UUID shiftId, BroadcastAgencyShiftRequest request) {
        return agencyShiftRoutingService.broadcast(agencyUserId, shiftId, request);
    }
}
