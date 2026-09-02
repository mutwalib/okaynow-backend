package com.okaynow.agencies.service;

import com.okaynow.agencies.domain.ShiftRoutingMode;
import com.okaynow.agencies.dto.BroadcastAgencyShiftRequest;
import com.okaynow.agencies.dto.BroadcastAgencyShiftResponse;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.GeoUtils;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.ShiftEventPublisher;
import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.service.AgencySettingsService;
import com.okaynow.roster.domain.AgencyCaregiver;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.roster.repository.AgencyCaregiverRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencyShiftRoutingService {

    private static final java.util.Set<ShiftClaimStatus> ACTIVE =
            EnumSet.of(ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED);

    private final AgencyAccessService agencyAccessService;
    private final AgencySettingsService agencySettingsService;
    private final ShiftRepository shiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final AgencyCaregiverRepository agencyCaregiverRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final UserRepository userRepository;
    private final BookingService bookingService;
    private final ShiftEventPublisher shiftEventPublisher;
    private final CaregiverStaffingConstraintService staffingConstraintService;

    @Transactional
    public void routeAfterHomeRequestAccepted(UUID agencyId, Shift shift) {
        AgencySettings settings = agencySettingsService.getOrCreateForAgency(agencyId);
        if (settings.getShiftRoutingMode() == ShiftRoutingMode.AUTO_BROADCAST) {
            broadcastInternal(agencyId, shift.getId(), null, resolveActor(shift.getCreatedBy(), null));
        }
    }

    @Transactional
    public BroadcastAgencyShiftResponse broadcast(
            UUID agencyUserId, UUID shiftId, BroadcastAgencyShiftRequest request) {
        UUID agencyId = agencyAccessService.requireAgencyForUser(agencyUserId).getId();
        agencyAccessService.assertAgencyAllowsWrites(
                agencyAccessService.requireAgencyForUser(agencyUserId));
        User actor = userRepository.findById(agencyUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return broadcastInternal(agencyId, shiftId, request.caregiverProfileIds(), actor);
    }

    private BroadcastAgencyShiftResponse broadcastInternal(
            UUID agencyId,
            UUID shiftId,
            List<UUID> caregiverProfileIds,
            User actor) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getAgencyId() == null || !shift.getAgencyId().equals(agencyId)) {
            throw new ResourceNotFoundException("Shift not found");
        }
        if (shift.getStatus() == ShiftStatus.CANCELLED
                || shift.getStatus() == ShiftStatus.COMPLETED) {
            throw new BadRequestException("This shift is no longer active");
        }
        int headcount = Math.max(1, shift.getRequiredHeadcount());
        long active = shiftClaimRepository.countByShiftIdAndStatusIn(shift.getId(), ACTIVE);
        if (active >= headcount) {
            throw new BadRequestException("This shift is already fully staffed");
        }

        if (caregiverProfileIds != null && !caregiverProfileIds.isEmpty()) {
            int invited = 0;
            for (UUID caregiverProfileId : caregiverProfileIds) {
                assertActiveRoster(agencyId, caregiverProfileId);
                CaregiverProfile caregiver = caregiverProfileRepository.findById(caregiverProfileId)
                        .orElseThrow(() -> new ResourceNotFoundException("Caregiver not found"));
                staffingConstraintService.assertAgencyStaffingRules(agencyId, caregiverProfileId, shift);
                if (!isInServiceArea(caregiver, shift)) {
                    throw new BadRequestException(
                            caregiver.getFirstName() + " is outside the service area for this shift");
                }
                bookingService.invite(shiftId, caregiverProfileId, actor);
                invited++;
            }
            return new BroadcastAgencyShiftResponse("INVITED", invited, shift.getId());
        }

        int openSlots = (int) (headcount - active);
        shift.setMarketplacePosted(true);
        shift.setMarketplaceSlots(openSlots);
        shift.setStatus(ShiftStatus.OPEN);
        shiftRepository.save(shift);

        List<AgencyCaregiver> roster = agencyCaregiverRepository.findByAgencyIdOrderByInvitedAtDesc(agencyId)
                .stream()
                .filter(r -> r.getStatus() == AgencyCaregiverStatus.ACTIVE)
                .toList();
        int notified = 0;
        for (AgencyCaregiver member : roster) {
            CaregiverProfile caregiver = member.getCaregiverProfile();
            if (!isInServiceArea(caregiver, shift)) {
                continue;
            }
            shiftEventPublisher.publish(
                    NotificationType.SHIFT_POSTED,
                    shift,
                    caregiver.getUser().getId(),
                    "New shift available",
                    "A " + shift.getDate() + " shift in " + shift.getCity()
                            + " is open for roster caregivers in your area.");
            notified++;
        }
        return new BroadcastAgencyShiftResponse("ROSTER_OPEN", notified, shift.getId());
    }

    private User resolveActor(UUID createdBy, User fallback) {
        if (fallback != null) {
            return fallback;
        }
        return userRepository.findById(createdBy)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void assertActiveRoster(UUID agencyId, UUID caregiverProfileId) {
        agencyCaregiverRepository.findByAgencyIdAndCaregiverProfileId(agencyId, caregiverProfileId)
                .filter(r -> r.getStatus() == AgencyCaregiverStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("Caregiver is not active on your roster"));
    }

    private boolean isInServiceArea(CaregiverProfile caregiver, Shift shift) {
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
