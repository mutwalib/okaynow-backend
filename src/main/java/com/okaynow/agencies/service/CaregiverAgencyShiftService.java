package com.okaynow.agencies.service;

import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.GeoUtils;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.roster.repository.AgencyCaregiverRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.mapper.ShiftMapper;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.repository.CaregiverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverAgencyShiftService {

    private final CaregiverProfileRepository caregiverProfileRepository;
    private final AgencyCaregiverRepository agencyCaregiverRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftMapper shiftMapper;
    private final BookingService bookingService;

    @Transactional(readOnly = true)
    public List<ShiftResponse> listOpenRosterShifts(UUID caregiverUserId, UUID filterAgencyId) {
        CaregiverProfile caregiver = caregiverProfileRepository.findByUserId(caregiverUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
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
        return shiftRepository.findOpenRosterBroadcastForAgencies(agencyIds, LocalDate.now()).stream()
                .filter(shift -> isEligibleInArea(caregiver, shift))
                .map(shiftMapper::toResponse)
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
