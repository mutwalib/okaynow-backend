package com.okaynow.booking.service;

import com.okaynow.agencies.service.CaregiverStaffingConstraintService;
import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.booking.domain.ClaimSource;
import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.dto.ClientRejectCaregiverResponse;
import com.okaynow.booking.dto.PlatformConnectedCaregiverResponse;
import com.okaynow.booking.dto.ShiftClaimResponse;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.discipline.dto.NoShowDisciplineResult;
import com.okaynow.discipline.service.CaregiverDisciplineService;
import com.okaynow.evv.service.VisitService;
import com.okaynow.evv.support.ShiftWindows;
import com.okaynow.marketplace.domain.QualificationRulePack;
import com.okaynow.marketplace.service.DriveTimeService;
import com.okaynow.marketplace.service.MarketplaceEligibilityService;
import com.okaynow.marketplace.service.QualificationRulePackService;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.ShiftEventPublisher;
import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.dto.ClientInvoiceResponse;
import com.okaynow.payroll.service.AgencySettingsService;
import com.okaynow.payroll.service.InvoiceService;
import com.okaynow.payroll.service.SettlementService;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.roster.repository.AgencyCaregiverRepository;
import com.okaynow.staffing.service.ClientStaffingService;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.dto.ShiftResponses;
import com.okaynow.shifts.mapper.ShiftMapper;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.shifts.service.ShiftAgencyLabelService;
import com.okaynow.shifts.service.ShiftLocationService;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Set<ShiftClaimStatus> ACTIVE_CLAIM_STATUSES =
            EnumSet.of(ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED);

    private final ShiftClaimRepository shiftClaimRepository;
    private final ShiftRepository shiftRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final UserService userService;
    private final ShiftMapper shiftMapper;
    private final AuditLogService auditLogService;
    private final SettlementService settlementService;
    private final InvoiceService invoiceService;
    private final VisitService visitService;
    private final ShiftEventPublisher shiftEventPublisher;
    private final AgencySettingsService agencySettingsService;
    private final ClientStaffingService clientStaffingService;
    private final MarketplaceEligibilityService marketplaceEligibilityService;
    private final QualificationRulePackService qualificationRulePackService;
    private final DriveTimeService driveTimeService;
    private final CaregiverStaffingConstraintService staffingConstraintService;
    private final AgencyCaregiverRepository agencyCaregiverRepository;
    private final ShiftAgencyLabelService shiftAgencyLabelService;
    private final ShiftLocationService shiftLocationService;
    private final PastShiftExpiryService pastShiftExpiryService;
    private final CaregiverDisciplineService caregiverDisciplineService;

    /**
     * Caregiver claims an OPEN shift. Pessimistic locks are taken on the caregiver profile
     * first, then the shift (consistent lock order across all booking operations) so two
     * concurrent claims on the same shift, or the same caregiver claiming two overlapping
     * shifts at once, serialize instead of double-booking.
     */
    @Transactional
    public ShiftClaimResponse claim(UUID shiftId, String caregiverEmail) {
        CaregiverProfile caregiver = lockCaregiverByEmail(caregiverEmail);
        assertActiveForNewMarketplaceClaim(caregiver);
        if (!caregiver.isIndependentShiftsEnabled()) {
            throw new BadRequestException(
                    "Independent marketplace shifts are turned off on your profile. "
                            + "Enable them under How you get work, or join an agency roster instead.");
        }
        Shift shift = lockShift(shiftId);

        if (shift.getAgencyId() != null) {
            throw new ConflictException(
                    "Agency-managed shifts are assigned from the agency roster, not the open marketplace");
        }

        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new ConflictException("Shift is not open for claiming");
        }
        if (shift.getDate() != null && shift.getDate().isBefore(LocalDate.now(ShiftWindows.ZONE))) {
            throw new BadRequestException("Cannot claim a shift on a past date");
        }
        if (!shift.isMarketplacePosted() || shift.getMarketplaceSlots() < 1) {
            throw new ConflictException("No marketplace slots are open on this shift");
        }
        if (activeClaimCount(shift.getId()) >= requiredHeadcount(shift)) {
            throw new ConflictException("This shift is already fully staffed");
        }
        if (shiftClaimRepository.findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                shiftId, caregiver.getId(), ACTIVE_CLAIM_STATUSES).isPresent()) {
            throw new ConflictException("You already have an active claim on this shift");
        }
        marketplaceEligibilityService.assertCanClaim(caregiver, shift);
        assertNoOverlappingClaim(caregiver.getId(), shift);

        ShiftClaim claim = ShiftClaim.builder()
                .shift(shift)
                .caregiverProfile(caregiver)
                .status(ShiftClaimStatus.PENDING)
                .source(ClaimSource.MARKETPLACE)
                .build();
        attachTravelEconomics(claim, caregiver, shift);
        shiftClaimRepository.save(claim);
        // Remaining headcount stays on the marketplace (refreshShiftFill syncs slots).
        refreshShiftFill(shift);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_CLAIMED,
                shift,
                caregiver.getUser().getId(),
                "Shift claimed",
                caregiver.getFirstName() + " " + caregiver.getLastName()
                        + " claimed the " + shift.getDate() + " shift"
                        + " (" + shift.getFilledSlots() + "/" + requiredHeadcount(shift) + ").");
        return toResponse(claim, true);
    }

    /**
     * Roster caregiver picks an agency-open shift (tenant board, not global marketplace).
     */
    @Transactional
    public ShiftClaimResponse claimAgencyRosterShift(UUID shiftId, String caregiverEmail) {
        CaregiverProfile caregiver = lockCaregiverByEmail(caregiverEmail);
        assertActiveForNewMarketplaceClaim(caregiver);
        if (!caregiver.isAgencyRosterEnabled()) {
            throw new BadRequestException(
                    "Agency roster shifts are turned off on your profile. "
                            + "Enable Agency rosters under How you get work.");
        }
        Shift shift = lockShift(shiftId);
        if (shift.getAgencyId() == null) {
            throw new ConflictException("This is not an agency roster shift");
        }
        if (shift.getStatus() != ShiftStatus.OPEN || !shift.isMarketplacePosted()) {
            throw new ConflictException("This shift is not open for roster caregivers");
        }
        if (shift.getDate() != null && shift.getDate().isBefore(LocalDate.now(ShiftWindows.ZONE))) {
            throw new BadRequestException("Cannot claim a shift on a past date");
        }
        if (shift.getMarketplaceSlots() < 1) {
            throw new ConflictException("No open slots remain on this shift");
        }
        assertActiveOnAgencyRoster(shift.getAgencyId(), caregiver.getId());
        if (shiftClaimRepository.findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                shiftId, caregiver.getId(), ACTIVE_CLAIM_STATUSES).isPresent()) {
            throw new ConflictException("You already have an active claim on this shift");
        }
        marketplaceEligibilityService.assertCanClaim(caregiver, shift);
        assertNoOverlappingClaim(caregiver.getId(), shift);
        staffingConstraintService.assertAgencyStaffingRules(shift.getAgencyId(), caregiver.getId(), shift);

        ShiftClaim claim = ShiftClaim.builder()
                .shift(shift)
                .caregiverProfile(caregiver)
                .status(ShiftClaimStatus.CONFIRMED)
                .source(ClaimSource.ROSTER_OPEN)
                .build();
        attachTravelEconomics(claim, caregiver, shift);
        shiftClaimRepository.save(claim);
        refreshShiftFill(shift);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_CLAIMED,
                shift,
                caregiver.getUser().getId(),
                "Shift claimed",
                caregiver.getFirstName() + " " + caregiver.getLastName()
                        + " picked up the " + shift.getDate() + " shift.");
        return toResponse(claim, true);
    }

    /**
     * Agency assigns a caregiver without requiring a marketplace claim.
     * From DRAFT or HELD: private assignment (not visible on the open board).
     * From OPEN: fills a slot; stays open until all slots are filled.
     * <p>
     * Business validation failures do not roll back an outer transaction so roster
     * auto-fill can try the next caregiver (schedule calendar materialization).
     */
    @Transactional(noRollbackFor = {
            BadRequestException.class,
            ConflictException.class
    })
    public ShiftClaimResponse assign(UUID shiftId, UUID caregiverProfileId, String adminEmail) {
        CaregiverProfile caregiver = caregiverProfileRepository.findById(caregiverProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver not found"));
        // Lock caregiver then shift (same order as claim)
        caregiver = caregiverProfileRepository.findByUserIdForUpdate(caregiver.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        assertNotRestricted(caregiver);
        Shift shift = lockShift(shiftId);

        if (shift.getStatus() != ShiftStatus.OPEN
                && shift.getStatus() != ShiftStatus.DRAFT
                && shift.getStatus() != ShiftStatus.HELD) {
            throw new ConflictException(
                    "Only DRAFT, HELD, or OPEN shifts can be assigned; free a slot first if the shift is full");
        }
        if (activeClaimCount(shift.getId()) >= requiredHeadcount(shift)) {
            throw new ConflictException("This shift is already fully staffed");
        }
        if (shiftClaimRepository.findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                shiftId, caregiver.getId(), ACTIVE_CLAIM_STATUSES).isPresent()) {
            throw new ConflictException("This caregiver is already assigned to this shift");
        }
        marketplaceEligibilityService.assertCanAssign(caregiver, shift);
        assertNoOverlappingClaim(caregiver.getId(), shift);
        if (shift.getAgencyId() != null) {
            staffingConstraintService.assertAgencyStaffingRules(
                    shift.getAgencyId(), caregiver.getId(), shift);
        }

        ShiftClaim claim = ShiftClaim.builder()
                .shift(shift)
                .caregiverProfile(caregiver)
                .status(ShiftClaimStatus.CONFIRMED)
                .source(ClaimSource.ASSIGNED)
                .build();
        attachTravelEconomics(claim, caregiver, shift);
        claim = shiftClaimRepository.save(claim);
        refreshShiftFill(shift);

        User actor = userService.getByEmail(adminEmail);
        String visibility = shift.isMarketplacePosted()
                ? "Assigned (marketplace shift; remaining slots stay open if any)"
                : "Assigned privately without marketplace release";
        auditLogService.record(actor, AuditAction.CAREGIVER_ASSIGNED_TO_SHIFT, "SHIFT",
                shift.getId(), shift.getClientProfileId(),
                "caregiver=%s claim=%s %s filled=%s/%s".formatted(
                        caregiver.getId(), claim.getId(), visibility,
                        shift.getFilledSlots(), requiredHeadcount(shift)));
        shiftEventPublisher.publish(
                NotificationType.SHIFT_ASSIGNED,
                shift,
                caregiver.getUser().getId(),
                "You were assigned a shift",
                "The agency assigned you the " + shift.getDate() + " shift in " + shift.getCity()
                        + ".");
        return toResponse(claim);
    }

    /**
     * Invite a specific caregiver privately (or onto an OPEN shift). Creates a PENDING
     * INVITE claim — the caregiver must accept or decline. Does not auto-confirm.
     * Overlap at invite time fails and notifies the client/admin inviter side.
     */
    @Transactional
    public ShiftClaimResponse invite(UUID shiftId, UUID caregiverProfileId, User actor) {
        CaregiverProfile caregiver = caregiverProfileRepository.findById(caregiverProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver not found"));
        caregiver = caregiverProfileRepository.findByUserIdForUpdate(caregiver.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        assertNotRestricted(caregiver);
        Shift shift = lockShift(shiftId);
        assertCanInvite(actor, shift, caregiverProfileId);

        if (shift.getStatus() != ShiftStatus.OPEN
                && shift.getStatus() != ShiftStatus.DRAFT
                && shift.getStatus() != ShiftStatus.HELD
                && shift.getStatus() != ShiftStatus.CLAIMED) {
            throw new ConflictException(
                    "Only DRAFT, HELD, CLAIMED, or OPEN shifts can receive invitations");
        }
        if (activeClaimCount(shift.getId()) >= requiredHeadcount(shift)) {
            throw new ConflictException("This shift is already fully staffed");
        }
        if (shiftClaimRepository.findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                shiftId, caregiver.getId(), ACTIVE_CLAIM_STATUSES).isPresent()) {
            throw new ConflictException("This caregiver already has an active claim on this shift");
        }
        marketplaceEligibilityService.assertCanClaim(caregiver, shift);
        try {
            assertNoOverlappingClaim(caregiver.getId(), shift);
            if (shift.getAgencyId() != null) {
                staffingConstraintService.assertAgencyStaffingRules(
                        shift.getAgencyId(), caregiver.getId(), shift);
            }
        } catch (ConflictException overlap) {
            notifyInviteFailed(shift, caregiver, overlap.getMessage());
            throw overlap;
        }

        ShiftClaim claim = ShiftClaim.builder()
                .shift(shift)
                .caregiverProfile(caregiver)
                .status(ShiftClaimStatus.PENDING)
                .source(ClaimSource.INVITE)
                .build();
        attachTravelEconomics(claim, caregiver, shift);
        claim = shiftClaimRepository.save(claim);
        refreshShiftFill(shift);

        auditLogService.record(actor, AuditAction.CAREGIVER_INVITED_TO_SHIFT, "SHIFT",
                shift.getId(), shift.getClientProfileId(),
                "caregiver=%s claim=%s invite pending filled=%s/%s".formatted(
                        caregiver.getId(), claim.getId(),
                        shift.getFilledSlots(), requiredHeadcount(shift)));
        shiftEventPublisher.publish(
                NotificationType.SHIFT_INVITED,
                shift,
                caregiver.getUser().getId(),
                "You're invited to a shift",
                "You've been invited to the " + shift.getDate() + " shift in " + shift.getCity()
                        + ". Accept or decline from My shifts.");
        return toResponse(claim);
    }

    /**
     * Caregiver accepts a PENDING INVITE. Confirms the seat; fails on overlap and
     * notifies the inviter (client + admins via fanout).
     */
    @Transactional
    public ShiftClaimResponse acceptInvite(UUID shiftId, String caregiverEmail) {
        CaregiverProfile caregiver = lockCaregiverByEmail(caregiverEmail);
        Shift shift = lockShift(shiftId);
        ShiftClaim claim = shiftClaimRepository
                .findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                        shiftId, caregiver.getId(), EnumSet.of(ShiftClaimStatus.PENDING))
                .orElseThrow(() -> new ResourceNotFoundException("No pending invitation on this shift"));
        if (claim.getSource() != ClaimSource.INVITE) {
            throw new ConflictException("This claim is not a private invitation");
        }
        if (shift.getDate() != null && shift.getDate().isBefore(LocalDate.now(ShiftWindows.ZONE))) {
            throw new BadRequestException("Cannot accept an invite for a past-dated shift");
        }
        try {
            assertNoOverlappingClaim(caregiver.getId(), shift);
            if (shift.getAgencyId() != null) {
                staffingConstraintService.assertAgencyStaffingRules(
                        shift.getAgencyId(), caregiver.getId(), shift);
            }
        } catch (ConflictException overlap) {
            claim.setStatus(ShiftClaimStatus.CANCELLED);
            claim.setReleasedAt(Instant.now());
            claim.setCancelReason("Invite failed: overlapping shift");
            refreshShiftFill(shift);
            notifyInviteFailed(shift, caregiver, overlap.getMessage());
            throw new ConflictException(
                    "You already have another shift overlapping this time. The inviter has been notified.");
        }

        claim.setStatus(ShiftClaimStatus.CONFIRMED);
        refreshShiftFill(shift);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_INVITE_ACCEPTED,
                shift,
                caregiver.getUser().getId(),
                "Invitation accepted",
                caregiver.getFirstName() + " " + caregiver.getLastName()
                        + " accepted the invite for the " + shift.getDate() + " shift.");
        shiftEventPublisher.publish(
                NotificationType.SHIFT_CONFIRMED,
                shift,
                caregiver.getUser().getId(),
                "Shift confirmed",
                "You accepted the " + shift.getDate() + " shift in " + shift.getCity() + ".");
        return toResponse(claim, true);
    }

    /**
     * Caregiver declines a PENDING INVITE; notifies the inviter.
     */
    @Transactional
    public ShiftClaimResponse declineInvite(UUID shiftId, String caregiverEmail) {
        CaregiverProfile caregiver = lockCaregiverByEmail(caregiverEmail);
        Shift shift = lockShift(shiftId);
        ShiftClaim claim = shiftClaimRepository
                .findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                        shiftId, caregiver.getId(), EnumSet.of(ShiftClaimStatus.PENDING))
                .orElseThrow(() -> new ResourceNotFoundException("No pending invitation on this shift"));
        if (claim.getSource() != ClaimSource.INVITE) {
            throw new ConflictException("This claim is not a private invitation");
        }

        claim.setStatus(ShiftClaimStatus.CANCELLED);
        claim.setReleasedAt(Instant.now());
        claim.setCancelReason("Declined invitation");
        refreshShiftFill(shift);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_INVITE_DECLINED,
                shift,
                caregiver.getUser().getId(),
                "Invitation declined",
                caregiver.getFirstName() + " " + caregiver.getLastName()
                        + " declined the invite for the " + shift.getDate() + " shift.");
        return toResponse(claim, true);
    }

    private void assertCanInvite(User actor, Shift shift, UUID caregiverProfileId) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            if (shift.getClientProfileId() == null
                    || !shift.getClientProfileId().equals(client.getId())) {
                throw new AccessDeniedException("Not your shift");
            }
            if (!clientStaffingService.isOnClientRoster(client.getId(), caregiverProfileId)) {
                throw new BadRequestException("That caregiver is not on your roster");
            }
            return;
        }
        if (actor.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            if (shift.getFacilityProfileId() == null
                    || !shift.getFacilityProfileId().equals(facility.getId())) {
                throw new AccessDeniedException("Not your shift");
            }
            return;
        }
        throw new AccessDeniedException("Not allowed to invite caregivers");
    }

    private void notifyInviteFailed(Shift shift, CaregiverProfile caregiver, String reason) {
        shiftEventPublisher.publish(
                NotificationType.SHIFT_INVITE_FAILED,
                shift,
                caregiver.getUser().getId(),
                "Invitation could not be completed",
                caregiver.getFirstName() + " " + caregiver.getLastName()
                        + " could not take the " + shift.getDate() + " shift: " + reason);
    }

    /**
     * Caregiver releases their own PENDING claim; frees a slot on the shift.
     */
    @Transactional
    public ShiftClaimResponse release(UUID shiftId, String caregiverEmail) {
        CaregiverProfile caregiver = lockCaregiverByEmail(caregiverEmail);
        Shift shift = lockShift(shiftId);

        ShiftClaim claim = shiftClaimRepository
                .findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(shiftId, caregiver.getId(), ACTIVE_CLAIM_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("No active claim by this caregiver on this shift"));
        if (claim.getStatus() != ShiftClaimStatus.PENDING) {
            throw new ConflictException("Only PENDING claims can be released; contact the agency to cancel");
        }
        // Private invites use the dedicated decline path for clearer inviter messaging.
        if (claim.getSource() == ClaimSource.INVITE) {
            return declineInvite(shiftId, caregiverEmail);
        }

        claim.setStatus(ShiftClaimStatus.CANCELLED);
        claim.setReleasedAt(Instant.now());
        claim.setCancelReason("Released by caregiver");
        if (claim.getSource() == ClaimSource.MARKETPLACE || shift.isMarketplacePosted()) {
            shift.setMarketplacePosted(true);
        }
        refreshShiftFill(shift);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_RELEASED,
                shift,
                caregiver.getUser().getId(),
                "Shift released",
                "A caregiver released the " + shift.getDate() + " shift — a slot is open again.");
        return toResponse(claim, true);
    }

    @Transactional
    public PagedResponse<ShiftClaimResponse> myClaims(String caregiverEmail, Pageable pageable) {
        pastShiftExpiryService.expireDueShifts();
        User user = userService.getByEmail(caregiverEmail);
        CaregiverProfile caregiver = caregiverProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        return PagedResponse.from(
                shiftClaimRepository.findByCaregiverProfileId(caregiver.getId(), pageable)
                        .map(c -> toResponse(pastShiftExpiryService.expireClaimIfPast(c), true)));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ShiftClaimResponse> allClaims(ShiftClaimStatus status, Pageable pageable) {
        var claims = status == null
                ? shiftClaimRepository.findAllBy(pageable)
                : shiftClaimRepository.findByStatus(status, pageable);
        return PagedResponse.from(claims.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public java.util.List<ShiftClaimResponse> claimsForShift(UUID shiftId) {
        if (!shiftRepository.existsById(shiftId)) {
            throw new ResourceNotFoundException("Shift not found");
        }
        return shiftClaimRepository.findByShiftIdOrderByClaimedAtDesc(shiftId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Admin confirms a PENDING claim. Shift becomes CONFIRMED only when every
     * required slot is filled with CONFIRMED claims.
     */
    @Transactional
    public ShiftClaimResponse confirm(UUID claimId) {
        ShiftClaim claim = findClaim(claimId);
        if (claim.getStatus() != ShiftClaimStatus.PENDING) {
            throw new ConflictException("Only PENDING claims can be confirmed");
        }
        Shift shift = lockShift(claim.getShift().getId());
        claim.setStatus(ShiftClaimStatus.CONFIRMED);
        refreshShiftFill(shift);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_CONFIRMED,
                shift,
                claim.getCaregiverProfile().getUser().getId(),
                "Shift confirmed",
                "Your claim for the " + shift.getDate() + " shift was confirmed.");
        return toResponse(claim);
    }

    /**
     * Client assigns a caregiver from their own roster onto their shift
     * (private assignment; same as agency assign but roster-scoped).
     */
    @Transactional
    public ShiftClaimResponse assignFromClientRoster(
            UUID shiftId, UUID caregiverProfileId, User actor) {
        Shift shift = lockShift(shiftId);
        if (actor.getRole() != Role.CLIENT) {
            throw new AccessDeniedException("Only family clients can assign from their roster");
        }
        ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
        if (shift.getClientProfileId() == null
                || !shift.getClientProfileId().equals(client.getId())) {
            throw new AccessDeniedException("Not your shift");
        }
        if (!clientStaffingService.isOnClientRoster(client.getId(), caregiverProfileId)) {
            throw new BadRequestException("That caregiver is not on your roster");
        }
        return assign(shiftId, caregiverProfileId, actor.getEmail());
    }

    /**
     * Client/facility rejects a PENDING or CONFIRMED caregiver on their shift.
     * Applies the agency-configured rejection fee (if &gt; 0) as a client invoice,
     * then frees the slot (marketplace reopen when appropriate).
     */
    @Transactional
    public ClientRejectCaregiverResponse rejectCaregiverByClient(
            UUID shiftId,
            UUID claimId,
            String reason,
            User actor) {
        Shift shift = lockShift(shiftId);
        authorizeReplacementActor(shift, actor);

        if (shift.getStatus() == ShiftStatus.COMPLETED
                || shift.getStatus() == ShiftStatus.CANCELLED
                || shift.getStatus() == ShiftStatus.NO_SHOW
                || shift.getStatus() == ShiftStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Cannot reject a caregiver once the shift is " + shift.getStatus());
        }

        ShiftClaim claim = findClaim(claimId);
        if (!claim.getShift().getId().equals(shiftId)) {
            throw new BadRequestException("Claim does not belong to this shift");
        }
        if (!ACTIVE_CLAIM_STATUSES.contains(claim.getStatus())) {
            throw new ConflictException("Only PENDING or CONFIRMED caregivers can be rejected");
        }

        String cancelReason = (reason == null || reason.isBlank())
                ? "Rejected by client"
                : reason.trim();
        String caregiverName = claim.getCaregiverProfile().getFirstName() + " "
                + claim.getCaregiverProfile().getLastName();

        claim.setStatus(ShiftClaimStatus.CANCELLED);
        claim.setReleasedAt(Instant.now());
        claim.setCancelReason(cancelReason);

        if (shift.isMarketplacePosted() || claim.getSource() == ClaimSource.MARKETPLACE) {
            shift.setMarketplacePosted(true);
            shift.setMarketplaceSlots(Math.max(1, shift.getMarketplaceSlots() + 1));
        }
        refreshShiftFill(shift);

        AgencySettings settings = agencySettingsService.getOrCreate();
        java.math.BigDecimal fee = settings.getClientCaregiverRejectionFee() != null
                ? settings.getClientCaregiverRejectionFee()
                : java.math.BigDecimal.ZERO;

        ClientInvoiceResponse feeInvoice = null;
        java.math.BigDecimal feeCharged = java.math.BigDecimal.ZERO;
        if (fee.compareTo(java.math.BigDecimal.ZERO) > 0) {
            if (shift.getClientProfileId() == null) {
                // Facility shifts have no family billing profile — reject without fee.
                feeCharged = java.math.BigDecimal.ZERO;
            } else {
                feeCharged = fee;
                feeInvoice = invoiceService.createCaregiverRejectionFeeInvoice(
                        shift.getClientProfileId(),
                        shift.getId(),
                        shift.getDate(),
                        fee,
                        caregiverName,
                        cancelReason,
                        actor,
                        true);
            }
        }

        auditLogService.record(actor, AuditAction.CLIENT_CAREGIVER_REJECTED, "SHIFT",
                shift.getId(), shift.getClientProfileId(),
                "claim=%s caregiver=%s fee=%s reason=%s".formatted(
                        claim.getId(),
                        claim.getCaregiverProfile().getId(),
                        feeCharged,
                        cancelReason));

        shiftEventPublisher.publish(
                NotificationType.CAREGIVER_REJECTED_BY_CLIENT,
                shift,
                claim.getCaregiverProfile().getUser().getId(),
                "Client rejected caregiver",
                caregiverName + " was rejected for the " + shift.getDate() + " shift"
                        + (feeCharged.compareTo(java.math.BigDecimal.ZERO) > 0
                                ? " (client fee $" + feeCharged + ")"
                                : "")
                        + ": " + cancelReason);

        if (shift.getStatus() == ShiftStatus.OPEN) {
            shiftEventPublisher.publish(
                    NotificationType.SHIFT_POSTED,
                    shift,
                    null,
                    "Open shift — slot available",
                    "A slot on the " + shift.getDate() + " shift is open again after a client rejection.");
        }

        return new ClientRejectCaregiverResponse(
                toResponse(claim, actor.getRole() == Role.CAREGIVER),
                feeCharged,
                feeInvoice != null ? feeInvoice.id() : null,
                feeInvoice != null ? feeInvoice.invoiceNumber() : null);
    }

    /**
     * Mark a confirmed assignment as caregiver no-show.
     * Blocked when EVV/attendance evidence exists (clock-in or client arrival confirm),
     * or when the shift is already in progress / completed — closes the false no-show loophole.
     */
    @Transactional
    public ShiftResponse markNoShow(UUID shiftId, String reason, User actor) {
        Shift shift = lockShift(shiftId);
        authorizeReplacementActor(shift, actor);

        if (shift.getStatus() == ShiftStatus.IN_PROGRESS
                || shift.getStatus() == ShiftStatus.COMPLETED) {
            throw new ConflictException(
                    "Cannot mark no-show after the shift has started or been completed. "
                            + "Attendance evidence shows the visit progressed.");
        }
        if (shift.getStatus() == ShiftStatus.CANCELLED || shift.getStatus() == ShiftStatus.NO_SHOW) {
            throw new ConflictException("This shift is already " + shift.getStatus());
        }
        if (shift.getStatus() != ShiftStatus.CONFIRMED && shift.getStatus() != ShiftStatus.CLAIMED) {
            throw new ConflictException("Only claimed/confirmed shifts can be marked no-show");
        }
        if (Instant.now().isBefore(ShiftWindows.startInstant(shift))) {
            throw new BadRequestException(
                    "Cannot mark no-show before the scheduled start time");
        }
        if (visitService.hasAttendanceEvidence(shiftId)) {
            throw new ConflictException(
                    "Cannot mark no-show: caregiver clock-in or client arrival confirmation is already on record. "
                            + "Contact the agency if you need to dispute attendance.");
        }

        String cancelReason = (reason == null || reason.isBlank())
                ? "Marked no-show by client"
                : reason.trim();

        UUID caregiverUserId = null;
        var claimOpt = shiftClaimRepository.findFirstByShiftIdAndStatusIn(shiftId, ACTIVE_CLAIM_STATUSES);
        if (claimOpt.isEmpty()) {
            throw new ConflictException("No active caregiver assignment to mark as no-show");
        }
        ShiftClaim claim = claimOpt.get();
        claim.setStatus(ShiftClaimStatus.CANCELLED);
        claim.setReleasedAt(Instant.now());
        claim.setCancelReason(cancelReason);
        CaregiverProfile caregiver = claim.getCaregiverProfile();
        caregiverUserId = caregiver.getUser().getId();

        shift.setStatus(ShiftStatus.NO_SHOW);
        shift.setMarketplacePosted(false);
        shift.setMarketplaceSlots(0);
        shift.setFilledSlots(0);

        auditLogService.record(actor, AuditAction.SHIFT_MARKED_NO_SHOW, "SHIFT",
                shift.getId(),
                shift.getClientProfileId() != null ? shift.getClientProfileId() : shift.getFacilityProfileId(),
                "claim=%s reason=%s".formatted(claim.getId(), cancelReason));

        NoShowDisciplineResult discipline = caregiverDisciplineService.recordNoShow(
                caregiver, shift, cancelReason, actor);

        String noShowBody = "The " + shift.getDate() + " shift was marked no-show: " + cancelReason
                + ". Caregiver warning " + discipline.warningNumber() + " of "
                + discipline.maxWarnings()
                + (discipline.restricted()
                ? ". Account automatically restricted."
                : ".");

        shiftEventPublisher.publish(
                NotificationType.SHIFT_NO_SHOW,
                shift,
                caregiverUserId,
                discipline.restricted()
                        ? "Marked as no-show — caregiver restricted"
                        : "Marked as no-show (warning " + discipline.warningNumber()
                        + "/" + discipline.maxWarnings() + ")",
                noShowBody);

        return shiftAgencyLabelService.label(shift, shiftMapper.toResponse(shift));
    }

    /**
     * Family/facility reports that they hired a caregiver found via OkayNow for ongoing
     * private care. Charges the configured platform conversion fee (Terms of Service).
     */
    @Transactional
    public ClientInvoiceResponse reportPlatformConversion(
            UUID caregiverProfileId,
            String notes,
            User actor) {
        if (actor.getRole() != Role.CLIENT && actor.getRole() != Role.FACILITY
                && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only clients, facilities, or admins can report conversions");
        }
        CaregiverProfile caregiver = caregiverProfileRepository.findById(caregiverProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver not found"));

        AgencySettings settings = agencySettingsService.getOrCreate();
        BigDecimal fee = settings.getPlatformConversionFee() != null
                ? settings.getPlatformConversionFee()
                : BigDecimal.ZERO;
        if (fee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Platform conversion fee is not enabled");
        }

        UUID clientProfileId = null;
        UUID facilityProfileId = null;
        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            clientProfileId = client.getId();
        } else if (actor.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            facilityProfileId = facility.getId();
        } else {
            throw new BadRequestException("Admins must use the admin conversion endpoint with a bill-to party");
        }

        String caregiverName = caregiver.getFirstName() + " " + caregiver.getLastName();
        ClientInvoiceResponse invoice = invoiceService.createPlatformConversionFeeInvoice(
                clientProfileId,
                facilityProfileId,
                caregiverProfileId,
                fee,
                caregiverName,
                notes,
                actor,
                true);

        auditLogService.record(actor, AuditAction.PLATFORM_CONVERSION_REPORTED, "INVOICE",
                invoice != null ? invoice.id() : null,
                clientProfileId != null ? clientProfileId : facilityProfileId,
                "caregiver=%s fee=%s".formatted(caregiverProfileId, fee));

        return invoice;
    }

    @Transactional(readOnly = true)
    public List<UUID> listReportedConversionCaregiverIds(User actor) {
        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            return invoiceService.listReportedConversionCaregiverIds(client.getId(), null);
        }
        if (actor.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            return invoiceService.listReportedConversionCaregiverIds(null, facility.getId());
        }
        throw new AccessDeniedException("Only clients or facilities can list reported conversions");
    }

    /**
     * Caregivers this client/facility has connected with (roster and/or past shift claims),
     * for the platform-conversion self-report picker.
     */
    @Transactional(readOnly = true)
    public List<PlatformConnectedCaregiverResponse> listConnectedCaregiversForConversion(User actor) {
        Map<UUID, PlatformConnectedCaregiverResponse> byId = new LinkedHashMap<>();

        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            for (var row : clientStaffingService.listMineForClientUser(actor)) {
                byId.putIfAbsent(row.caregiverProfileId(), new PlatformConnectedCaregiverResponse(
                        row.caregiverProfileId(), row.firstName(), row.lastName()));
            }
            for (CaregiverProfile cp : shiftClaimRepository.findDistinctCaregiversForClient(client.getId())) {
                byId.putIfAbsent(cp.getId(), new PlatformConnectedCaregiverResponse(
                        cp.getId(), cp.getFirstName(), cp.getLastName()));
            }
        } else if (actor.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            for (CaregiverProfile cp : shiftClaimRepository.findDistinctCaregiversForFacility(facility.getId())) {
                byId.putIfAbsent(cp.getId(), new PlatformConnectedCaregiverResponse(
                        cp.getId(), cp.getFirstName(), cp.getLastName()));
            }
        } else {
            throw new AccessDeniedException("Only clients or facilities can list connected caregivers");
        }

        List<PlatformConnectedCaregiverResponse> list = new ArrayList<>(byId.values());
        list.sort(Comparator
                .comparing(PlatformConnectedCaregiverResponse::lastName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PlatformConnectedCaregiverResponse::firstName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /**
     * Admin declines/cancels an active claim with a required reason; frees a slot.
     */
    @Transactional
    public ShiftClaimResponse cancel(UUID claimId, String cancelReason) {
        ShiftClaim claim = findClaim(claimId);
        if (!ACTIVE_CLAIM_STATUSES.contains(claim.getStatus())) {
            throw new ConflictException("Only active (PENDING or CONFIRMED) claims can be cancelled");
        }
        if (cancelReason == null || cancelReason.isBlank()) {
            throw new BadRequestException("A decline reason is required");
        }
        Shift shift = lockShift(claim.getShift().getId());
        claim.setStatus(ShiftClaimStatus.CANCELLED);
        claim.setReleasedAt(Instant.now());
        claim.setCancelReason(cancelReason.trim());
        if (shift.getStatus() != ShiftStatus.CANCELLED) {
            if (shift.isMarketplacePosted() || claim.getSource() == ClaimSource.MARKETPLACE) {
                shift.setMarketplacePosted(true);
            }
            refreshShiftFill(shift);
        }
        shiftEventPublisher.publish(
                NotificationType.SHIFT_RELEASED,
                shift,
                claim.getCaregiverProfile().getUser().getId(),
                "Claim declined",
                "Your claim on the " + shift.getDate() + " shift was declined: " + claim.getCancelReason());
        return toResponse(claim);
    }

    /** @deprecated use {@link #cancel(UUID, String)} */
    @Transactional
    public ShiftClaimResponse cancel(UUID claimId) {
        return cancel(claimId, "Cancelled by agency");
    }

    /**
     * Admin cancels an entire shift. Any active claim is closed rather than
     * reopened, and the shift becomes terminally CANCELLED.
     */
    @Transactional
    public void cancelShift(UUID shiftId) {
        Shift shift = lockShift(shiftId);
        if (shift.getStatus() == ShiftStatus.COMPLETED
                || shift.getStatus() == ShiftStatus.CANCELLED
                || shift.getStatus() == ShiftStatus.NO_SHOW) {
            throw new ConflictException("This shift can no longer be cancelled");
        }
        UUID caregiverUserId = null;
        var activeClaim = shiftClaimRepository.findFirstByShiftIdAndStatusIn(shiftId, ACTIVE_CLAIM_STATUSES);
        if (activeClaim.isPresent()) {
            ShiftClaim claim = activeClaim.get();
            claim.setStatus(ShiftClaimStatus.CANCELLED);
            claim.setReleasedAt(Instant.now());
            claim.setCancelReason("Shift cancelled by agency");
            caregiverUserId = claim.getCaregiverProfile().getUser().getId();
        }
        shift.setStatus(ShiftStatus.CANCELLED);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_CANCELLED,
                shift,
                caregiverUserId,
                "Shift cancelled",
                "The " + shift.getDate() + " shift was cancelled by the agency.");
    }

    /**
     * Admin marks a CONFIRMED shift as started (caregiver on site).
     * Creates a MANUAL visit if the caregiver has not clocked in yet.
     */
    @Transactional
    public void startShift(UUID shiftId, User admin) {
        Shift shift = lockShift(shiftId);
        if (shift.getStatus() != ShiftStatus.CONFIRMED) {
            throw new ConflictException("Only CONFIRMED shifts can be started");
        }
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        visitService.ensureManualStart(shiftId, admin);
        UUID caregiverUserId = shiftClaimRepository
                .findFirstByShiftIdAndStatusIn(shiftId, ACTIVE_CLAIM_STATUSES)
                .map(c -> c.getCaregiverProfile().getUser().getId())
                .orElse(null);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_STARTED,
                shift,
                caregiverUserId,
                "Shift started",
                "The " + shift.getDate() + " shift is now in progress.");
    }

    /**
     * Admin marks an IN_PROGRESS shift as completed; the active claim becomes COMPLETED.
     * Not allowed until the scheduled shift window has ended (America/New_York).
     */
    @Transactional
    public void completeShift(UUID shiftId) {
        Shift shift = lockShift(shiftId);
        if (shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            throw new ConflictException("Only IN_PROGRESS shifts can be completed");
        }
        Instant scheduledEnd = ShiftWindows.endInstant(shift);
        if (Instant.now().isBefore(scheduledEnd)) {
            throw new BadRequestException(
                    "This shift is still in the future or in progress — it cannot be marked completed until "
                            + "its scheduled end (" + ShiftWindows.endLocal(shift) + " ET)");
        }
        shift.setStatus(ShiftStatus.COMPLETED);
        UUID caregiverUserId = null;
        var claimOpt = shiftClaimRepository.findFirstByShiftIdAndStatusIn(shiftId, ACTIVE_CLAIM_STATUSES);
        if (claimOpt.isPresent()) {
            ShiftClaim claim = claimOpt.get();
            claim.setStatus(ShiftClaimStatus.COMPLETED);
            caregiverUserId = claim.getCaregiverProfile().getUser().getId();
        }
        settlementService.createForCompletedShift(shiftId);
        invoiceService.autoInvoiceForCompletedShift(shiftId);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_COMPLETED,
                shift,
                caregiverUserId,
                "Shift completed",
                "The " + shift.getDate() + " shift was marked completed.");
    }

    /**
     * Client/admin call-out or empty-day coverage request: open a chosen number of
     * slots on the marketplace. Existing assignments stay unless this is a full
     * call-out (no remaining seats), in which case that many claims are released.
     */
    @Transactional
    public ShiftResponse requestReplacement(UUID shiftId, String reason, Integer slots, User actor) {
        Shift shift = lockShift(shiftId);
        authorizeReplacementActor(shift, actor);

        if (shift.getStatus() == ShiftStatus.COMPLETED
                || shift.getStatus() == ShiftStatus.CANCELLED
                || shift.getStatus() == ShiftStatus.NO_SHOW) {
            throw new ConflictException("Cannot request replacement for a " + shift.getStatus() + " shift");
        }
        if (shift.getStatus() == ShiftStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Shift is already in progress — finish or contact the agency to reassign");
        }
        if (!Instant.now().isBefore(ShiftWindows.endInstant(shift))) {
            throw new ConflictException(
                    "Cannot request coverage after the shift's scheduled end ("
                            + ShiftWindows.endLocal(shift) + " ET)");
        }

        String cancelReason = (reason == null || reason.isBlank())
                ? "Replacement requested"
                : reason.trim();

        int required = requiredHeadcount(shift);
        List<ShiftClaim> active = shiftClaimRepository.findByShiftIdOrderByClaimedAtDesc(shiftId)
                .stream()
                .filter(c -> ACTIVE_CLAIM_STATUSES.contains(c.getStatus()))
                .toList();
        int filled = active.size();
        int remaining = Math.max(0, required - filled);
        int alreadyOpen = Math.max(0, shift.getMarketplaceSlots());

        int openCount;
        if (remaining > 0) {
            // Open some or all remaining seats — keep current caregivers.
            int maxNew = Math.max(0, remaining - alreadyOpen);
            if (maxNew < 1) {
                throw new ConflictException(
                        "All remaining seats are already open on the marketplace");
            }
            openCount = slots == null ? maxNew : slots;
            if (openCount < 1 || openCount > maxNew) {
                throw new BadRequestException(
                        "slots must be between 1 and " + maxNew
                                + " (remaining caregivers needed for marketplace)");
            }
        } else {
            // Fully staffed call-out: release N assignments and open that many seats.
            if (filled < 1) {
                throw new ConflictException("No caregivers to replace and no open seats");
            }
            openCount = slots == null ? 1 : slots;
            if (openCount < 1 || openCount > filled) {
                throw new BadRequestException(
                        "slots must be between 1 and " + filled
                                + " (assigned caregivers to release)");
            }
            // Prefer releasing PENDING marketplace claims first, then newest assignments.
            List<ShiftClaim> toRelease = active.stream()
                    .sorted((a, b) -> {
                        boolean aPending = a.getStatus() == ShiftClaimStatus.PENDING;
                        boolean bPending = b.getStatus() == ShiftClaimStatus.PENDING;
                        if (aPending != bPending) {
                            return aPending ? -1 : 1;
                        }
                        return b.getClaimedAt().compareTo(a.getClaimedAt());
                    })
                    .limit(openCount)
                    .toList();
            for (ShiftClaim claim : toRelease) {
                claim.setStatus(ShiftClaimStatus.CANCELLED);
                claim.setReleasedAt(Instant.now());
                claim.setCancelReason(cancelReason);
                shiftEventPublisher.publish(
                        NotificationType.SHIFT_RELEASED,
                        shift,
                        claim.getCaregiverProfile().getUser().getId(),
                        "Shift coverage released",
                        "You were released from the " + shift.getDate()
                                + " shift: " + cancelReason);
            }
        }

        shift.setMarketplacePosted(true);
        shift.setMarketplaceSlots(Math.max(shift.getMarketplaceSlots(), 0) + openCount);
        // Cap at remaining seats after releases.
        refreshShiftFill(shift);
        int maxOpen = Math.max(0, requiredHeadcount(shift) - shift.getFilledSlots());
        if (shift.getMarketplaceSlots() > maxOpen) {
            shift.setMarketplaceSlots(maxOpen);
        }

        auditLogService.record(actor, AuditAction.SHIFT_REPLACEMENT_REQUESTED, "SHIFT",
                shift.getId(), shift.getClientProfileId(),
                "Opened %s marketplace slot(s). %s".formatted(openCount, cancelReason));
        shiftEventPublisher.publish(
                NotificationType.SHIFT_REPLACEMENT_REQUESTED,
                shift,
                null,
                "Coverage needed",
                "Replacement requested for " + shift.getDate() + " ("
                        + shift.getStartTime() + "–" + shift.getEndTime() + "): "
                        + openCount + " slot(s) — " + cancelReason);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_POSTED,
                shift,
                null,
                "Open shift — coverage needed",
                "A " + shift.getRequiredQualification() + " shift on " + shift.getDate()
                        + " has " + shift.getMarketplaceSlots()
                        + " open marketplace slot(s).");

        return ShiftResponses.forViewer(
                shiftAgencyLabelService.label(shift, shiftMapper.toResponse(shift)),
                actor.getRole());
    }

    /**
     * Withdraw unclaimed marketplace openings. Blocked once any caregiver has
     * claimed a marketplace slot (reject that claim first if needed).
     */
    @Transactional
    public ShiftResponse closeMarketplace(UUID shiftId, User actor) {
        Shift shift = lockShift(shiftId);
        authorizeReplacementActor(shift, actor);

        if (shift.getStatus() == ShiftStatus.COMPLETED
                || shift.getStatus() == ShiftStatus.CANCELLED
                || shift.getStatus() == ShiftStatus.NO_SHOW
                || shift.getStatus() == ShiftStatus.IN_PROGRESS) {
            throw new ConflictException("Cannot close marketplace for a " + shift.getStatus() + " shift");
        }
        if (!shift.isMarketplacePosted() || shift.getMarketplaceSlots() < 1) {
            throw new ConflictException("Marketplace is not open for this shift");
        }

        // Block once a marketplace-sourced claim exists (someone picked a slot).
        boolean picked = shiftClaimRepository.findByShiftIdOrderByClaimedAtDesc(shiftId)
                .stream()
                .anyMatch(c -> ACTIVE_CLAIM_STATUSES.contains(c.getStatus())
                        && c.getSource() == ClaimSource.MARKETPLACE);
        if (picked) {
            throw new ConflictException(
                    "A caregiver has already claimed a marketplace slot — reject them first, or keep the opening");
        }

        int closed = shift.getMarketplaceSlots();
        shift.setMarketplacePosted(false);
        shift.setMarketplaceSlots(0);
        refreshShiftFill(shift);

        auditLogService.record(actor, AuditAction.SHIFT_MARKETPLACE_CLOSED, "SHIFT",
                shift.getId(), shift.getClientProfileId(),
                "Closed %s unclaimed marketplace slot(s)".formatted(closed));
        shiftEventPublisher.publish(
                NotificationType.SHIFT_REPLACEMENT_REQUESTED,
                shift,
                null,
                "Marketplace closed",
                "Marketplace openings for " + shift.getDate()
                        + " were withdrawn before a caregiver claimed them.");

        return ShiftResponses.forViewer(
                shiftAgencyLabelService.label(shift, shiftMapper.toResponse(shift)),
                actor.getRole());
    }

    private void authorizeReplacementActor(Shift shift, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            if (!client.isCanCreateShifts() && !client.isCanViewShifts()) {
                throw new AccessDeniedException("You do not have permission to manage this shift");
            }
            if (shift.getClientProfileId() == null
                    || !shift.getClientProfileId().equals(client.getId())) {
                throw new AccessDeniedException("Not your shift");
            }
            return;
        }
        if (actor.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            boolean owns = shift.getFacilityProfileId() != null
                    && shift.getFacilityProfileId().equals(facility.getId());
            boolean legacy = shift.getFacilityProfileId() == null
                    && shift.getClientProfileId() == null
                    && actor.getId().equals(shift.getCreatedBy());
            if (!owns && !legacy) {
                throw new AccessDeniedException("Not your shift");
            }
            return;
        }
        throw new AccessDeniedException("Only the client, facility, or admin can request replacement");
    }

    /**
     * Remove the active caregiver assignment. Shift returns to OPEN (if ever posted)
     * or DRAFT (private assignment never released).
     */
    @Transactional
    public ShiftClaimResponse unassign(UUID shiftId, String actorEmail) {
        return unassign(shiftId, null, actorEmail);
    }

    @Transactional
    public ShiftClaimResponse unassign(UUID shiftId, UUID caregiverProfileId, String actorEmail) {
        Shift shift = lockShift(shiftId);
        if (shift.getStatus() == ShiftStatus.IN_PROGRESS
                || shift.getStatus() == ShiftStatus.COMPLETED
                || shift.getStatus() == ShiftStatus.CANCELLED
                || shift.getStatus() == ShiftStatus.NO_SHOW) {
            throw new ConflictException("This shift can no longer be unassigned");
        }
        ShiftClaim claim;
        if (caregiverProfileId != null) {
            claim = shiftClaimRepository
                    .findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                            shiftId, caregiverProfileId, ACTIVE_CLAIM_STATUSES)
                    .orElseThrow(() -> new ConflictException(
                            "That caregiver is not assigned to this shift"));
        } else {
            claim = shiftClaimRepository
                    .findFirstByShiftIdAndStatusIn(shiftId, ACTIVE_CLAIM_STATUSES)
                    .orElseThrow(() -> new ConflictException(
                            "No active caregiver assignment to remove"));
        }
        User actor = userService.getByEmail(actorEmail);
        ShiftClaimResponse response = cancel(claim.getId(), "Unassigned by agency");
        auditLogService.record(actor, AuditAction.CAREGIVER_ASSIGNED_TO_SHIFT, "SHIFT",
                shiftId, lockShift(shiftId).getClientProfileId(),
                "Unassigned claim " + claim.getId());
        return response;
    }

    /**
     * Detach a caregiver from a family's upcoming schedule: cancel their PENDING/CONFIRMED
     * claims on that client's future/today shifts (leaves past and in-progress alone).
     */
    @Transactional
    public int releaseCaregiverFromClientSchedule(
            UUID clientProfileId, UUID caregiverProfileId, String adminEmail) {
        User actor = userService.getByEmail(adminEmail);
        List<ShiftClaim> claims = shiftClaimRepository.findActiveFutureClaimsForClientCaregiver(
                clientProfileId,
                caregiverProfileId,
                LocalDate.now(),
                ACTIVE_CLAIM_STATUSES);
        int released = 0;
        for (ShiftClaim claim : claims) {
            cancel(claim.getId(), "Removed from client roster/schedule");
            released++;
        }
        if (released > 0) {
            auditLogService.record(actor, AuditAction.CLIENT_CAREGIVER_UNASSIGNED, "CLIENT_PROFILE",
                    clientProfileId, clientProfileId,
                    "Released %s future schedule claim(s) for caregiver=%s"
                            .formatted(released, caregiverProfileId));
        }
        return released;
    }

    /**
     * Assign a caregiver onto existing open/draft/held client shifts that still need
     * staff. Does not create new shifts. Skips days where qualification/jurisdiction/
     * overlap rules fail.
     */
    @Transactional
    public int fillOpenClientShifts(
            UUID clientProfileId, UUID caregiverProfileId, String adminEmail) {
        List<Shift> opens = shiftRepository.findOpenAssignableForClientFrom(
                clientProfileId, LocalDate.now());
        int filled = 0;
        for (Shift shift : opens) {
            try {
                assign(shift.getId(), caregiverProfileId, adminEmail);
                filled++;
            } catch (ConflictException | BadRequestException | AccessDeniedException ex) {
                // Overlap, already full, wrong qual, etc. — try the next open day.
            }
        }
        return filled;
    }

    /** Undo Start: IN_PROGRESS → CONFIRMED. */
    @Transactional
    public void revertStart(UUID shiftId, String adminEmail) {
        Shift shift = lockShift(shiftId);
        if (shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            throw new ConflictException("Only IN_PROGRESS shifts can revert to CONFIRMED");
        }
        shift.setStatus(ShiftStatus.CONFIRMED);
        User actor = userService.getByEmail(adminEmail);
        auditLogService.record(actor, AuditAction.SHIFT_START_REVERTED, "SHIFT",
                shift.getId(), shift.getClientProfileId(), "IN_PROGRESS → CONFIRMED");
        UUID caregiverUserId = shiftClaimRepository
                .findFirstByShiftIdAndStatusIn(shiftId, ACTIVE_CLAIM_STATUSES)
                .map(c -> c.getCaregiverProfile().getUser().getId())
                .orElse(null);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_CONFIRMED,
                shift,
                caregiverUserId,
                "Shift start undone",
                "The " + shift.getDate() + " shift was returned to confirmed (not yet started).");
    }

    /**
     * Undo Complete: COMPLETED → IN_PROGRESS. Blocked if settlement payments were marked paid.
     */
    @Transactional
    public void revertComplete(UUID shiftId, String adminEmail) {
        Shift shift = lockShift(shiftId);
        if (shift.getStatus() != ShiftStatus.COMPLETED) {
            throw new ConflictException("Only COMPLETED shifts can revert to IN_PROGRESS");
        }
        settlementService.deleteIfUnpaidForShift(shiftId);
        shift.setStatus(ShiftStatus.IN_PROGRESS);
        shift.setPlatformPaid(false);
        var claimOpt = shiftClaimRepository.findFirstByShiftIdAndStatusIn(
                shiftId, EnumSet.of(ShiftClaimStatus.COMPLETED));
        if (claimOpt.isPresent()) {
            claimOpt.get().setStatus(ShiftClaimStatus.CONFIRMED);
        }
        User actor = userService.getByEmail(adminEmail);
        auditLogService.record(actor, AuditAction.SHIFT_COMPLETE_REVERTED, "SHIFT",
                shift.getId(), shift.getClientProfileId(), "COMPLETED → IN_PROGRESS");
        UUID caregiverUserId = claimOpt
                .map(c -> c.getCaregiverProfile().getUser().getId())
                .orElse(null);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_STARTED,
                shift,
                caregiverUserId,
                "Shift completion undone",
                "The " + shift.getDate() + " shift was returned to in progress.");
    }

    /** Undo Cancel: CANCELLED → DRAFT (or OPEN if it had been marketplace-posted). */
    @Transactional
    public void reopenCancelled(UUID shiftId, String adminEmail) {
        Shift shift = lockShift(shiftId);
        if (shift.getStatus() != ShiftStatus.CANCELLED) {
            throw new ConflictException("Only CANCELLED shifts can be reopened");
        }
        User actor = userService.getByEmail(adminEmail);
        if (shift.isMarketplacePosted()) {
            shift.setStatus(ShiftStatus.OPEN);
        } else {
            shift.setStatus(ShiftStatus.DRAFT);
        }
        auditLogService.record(actor, AuditAction.SHIFT_REOPENED, "SHIFT",
                shift.getId(), shift.getClientProfileId(),
                "CANCELLED → " + shift.getStatus());
        shiftEventPublisher.publish(
                NotificationType.SHIFT_POSTED,
                shift,
                null,
                "Shift reopened",
                "The " + shift.getDate() + " shift was reopened as " + shift.getStatus() + ".");
    }

    /**
     * Extends a confirmed or in-progress assignment. Shortening is deliberately
     * rejected so this endpoint cannot silently reduce scheduled caregiver pay.
     */
    @Transactional
    public void extendShift(UUID shiftId, java.time.LocalTime newEndTime, String adminEmail) {
        Shift shift = lockShift(shiftId);
        if (shift.getStatus() != ShiftStatus.CONFIRMED
                && shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            throw new ConflictException("Only CONFIRMED or IN_PROGRESS shifts can be extended");
        }
        if (!newEndTime.isAfter(shift.getEndTime())) {
            throw new BadRequestException("The new end time must be later than the current end time");
        }
        var previousEndTime = shift.getEndTime();
        shift.setEndTime(newEndTime);
        User actor = userService.getByEmail(adminEmail);
        auditLogService.record(actor, AuditAction.SHIFT_TIME_EXTENDED, "SHIFT",
                shift.getId(), shift.getClientProfileId(),
                "endTime=%s -> %s".formatted(previousEndTime, newEndTime));
        UUID caregiverUserId = shiftClaimRepository
                .findFirstByShiftIdAndStatusIn(shiftId, ACTIVE_CLAIM_STATUSES)
                .map(c -> c.getCaregiverProfile().getUser().getId())
                .orElse(null);
        shiftEventPublisher.publish(
                NotificationType.SHIFT_EXTENDED,
                shift,
                caregiverUserId,
                "Shift extended",
                "The " + shift.getDate() + " shift end time was extended to " + newEndTime + ".");
    }

    private void assertNoOverlappingClaim(UUID caregiverProfileId, Shift candidate) {
        for (ShiftClaim existing : shiftClaimRepository.findActiveClaimsExcludingShift(
                caregiverProfileId, candidate.getId(), ACTIVE_CLAIM_STATUSES)) {
            if (ShiftWindows.overlaps(candidate, existing.getShift())) {
                throw new ConflictException(
                        "Caregiver already has an active claim overlapping this shift's time window");
            }
        }
    }

    private void assertActiveOnAgencyRoster(UUID agencyId, UUID caregiverProfileId) {
        agencyCaregiverRepository.findByAgencyIdAndCaregiverProfileId(agencyId, caregiverProfileId)
                .filter(r -> r.getStatus() == AgencyCaregiverStatus.ACTIVE)
                .orElseThrow(() -> new ConflictException(
                        "You must be on this agency's active roster to pick up shifts"));
    }

    private int requiredHeadcount(Shift shift) {
        return Math.max(1, shift.getRequiredHeadcount());
    }

    private long activeClaimCount(UUID shiftId) {
        return shiftClaimRepository.countByShiftIdAndStatusIn(shiftId, ACTIVE_CLAIM_STATUSES);
    }

    private long confirmedClaimCount(UUID shiftId) {
        return shiftClaimRepository.countByShiftIdAndStatusIn(
                shiftId, EnumSet.of(ShiftClaimStatus.CONFIRMED));
    }

    /**
     * Sync filledSlots and status from active claims.
     * OPEN while any required seats remain and the shift is (or was) on the marketplace;
     * marketplaceSlots tracks remaining headcount so partial fills stay claimable.
     * CLAIMED when privately staffed (no marketplace) but not fully confirmed;
     * CONFIRMED when every required seat is CONFIRMED;
     * DRAFT/HELD only when there are no active claims.
     */
    private void refreshShiftFill(Shift shift) {
        if (shift.getStatus() == ShiftStatus.CANCELLED
                || shift.getStatus() == ShiftStatus.COMPLETED
                || shift.getStatus() == ShiftStatus.IN_PROGRESS
                || shift.getStatus() == ShiftStatus.NO_SHOW
                || shift.getStatus() == ShiftStatus.EXPIRED) {
            shift.setFilledSlots((int) activeClaimCount(shift.getId()));
            return;
        }

        int required = requiredHeadcount(shift);
        int filled = (int) activeClaimCount(shift.getId());
        int confirmed = (int) confirmedClaimCount(shift.getId());
        shift.setFilledSlots(filled);
        shift.setRequiredHeadcount(required);

        if (filled >= required) {
            shift.setMarketplacePosted(false);
            shift.setMarketplaceSlots(0);
            shift.setStatus(confirmed >= required ? ShiftStatus.CONFIRMED : ShiftStatus.CLAIMED);
            return;
        }

        int remaining = required - filled;
        boolean keepMarketplaceOpen = shift.isMarketplacePosted()
                || shift.getMarketplaceSlots() > 0
                || hasActiveMarketplaceClaim(shift.getId());
        if (keepMarketplaceOpen) {
            // Partial fill: keep remaining seats on the open board.
            shift.setMarketplacePosted(true);
            shift.setMarketplaceSlots(remaining);
            shift.setStatus(ShiftStatus.OPEN);
            return;
        }

        if (filled > 0) {
            // Privately staffed (assignments only) — never demote to DRAFT.
            shift.setMarketplacePosted(false);
            shift.setMarketplaceSlots(0);
            shift.setStatus(ShiftStatus.CLAIMED);
            return;
        }

        if (shift.getStatus() == ShiftStatus.HELD) {
            shift.setMarketplacePosted(false);
            shift.setMarketplaceSlots(0);
            return;
        }

        shift.setMarketplacePosted(false);
        shift.setMarketplaceSlots(0);
        shift.setStatus(ShiftStatus.DRAFT);
    }

    private void attachTravelEconomics(ShiftClaim claim, CaregiverProfile caregiver, Shift shift) {
        QualificationRulePack pack =
                qualificationRulePackService.getOrCreate(shift.getRequiredQualification());
        int minutes = driveTimeService.estimateDriveMinutes(
                caregiver.getHomeLat(), caregiver.getHomeLng(),
                shift.getLat(), shift.getLng());
        claim.setTravelMinutesEstimate(minutes > 0 ? minutes : null);
        BigDecimal travelPay = driveTimeService.travelPayAmount(pack, minutes);
        claim.setTravelPayAmount(travelPay.signum() > 0 ? travelPay : null);
    }

    private boolean hasActiveMarketplaceClaim(UUID shiftId) {
        return shiftClaimRepository.findByShiftIdOrderByClaimedAtDesc(shiftId).stream()
                .anyMatch(c -> ACTIVE_CLAIM_STATUSES.contains(c.getStatus())
                        && c.getSource() == ClaimSource.MARKETPLACE);
    }

    private CaregiverProfile lockCaregiverByEmail(String email) {
        User user = userService.getByEmail(email);
        return caregiverProfileRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
    }

    /**
     * New open-board claims require an ACTIVE account. Pending-review caregivers may still
     * fulfill existing claims (clock, release, accept/decline invites) via the access filter.
     * Restricted accounts cannot take on new marketplace work.
     */
    private static void assertActiveForNewMarketplaceClaim(CaregiverProfile caregiver) {
        User user = caregiver.getUser();
        if (user != null && user.getStatus() == UserStatus.RESTRICTED) {
            throw new BadRequestException(
                    "Your account is restricted after repeated no-show warnings. "
                            + "You cannot claim new shifts until OkayNow lifts the restriction.");
        }
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException(
                    "Your account is pending OkayNow review. You can manage upcoming shifts you already have, "
                            + "but cannot claim new open shifts until approved.");
        }
    }

    /** Blocks assigning / inviting caregivers who are platform-restricted. */
    private static void assertNotRestricted(CaregiverProfile caregiver) {
        User user = caregiver.getUser();
        if (user != null && user.getStatus() == UserStatus.RESTRICTED) {
            throw new BadRequestException(
                    "This caregiver is restricted after repeated no-show warnings and cannot "
                            + "receive new shift assignments until a platform admin lifts the restriction.");
        }
    }

    private Shift lockShift(UUID shiftId) {
        return shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
    }

    private ShiftClaim findClaim(UUID claimId) {
        return shiftClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
    }

    private ShiftClaimResponse toResponse(ShiftClaim claim) {
        return toResponse(claim, false);
    }

    private ShiftClaimResponse toResponse(ShiftClaim claim, boolean redactForCaregiver) {
        CaregiverProfile caregiver = claim.getCaregiverProfile();
        Shift entity = claim.getShift();
        if (entity.getLat() == null || entity.getLng() == null) {
            try {
                shiftLocationService.ensureCoordinates(entity);
            } catch (RuntimeException ignored) {
                // Leave pin empty; clock-in will surface a clear geocode error.
            }
        }
        var shift = shiftAgencyLabelService.label(entity, shiftMapper.toResponse(entity));
        if (redactForCaregiver) {
            shift = ShiftResponses.forViewer(shift, Role.CAREGIVER);
        }
        return new ShiftClaimResponse(
                claim.getId(),
                caregiver.getId(),
                caregiver.getFirstName(),
                caregiver.getLastName(),
                caregiver.getUser().getEmail(),
                claim.getStatus(),
                claim.getSource() != null ? claim.getSource() : ClaimSource.MARKETPLACE,
                claim.getClaimedAt(),
                claim.getReleasedAt(),
                claim.getCancelReason(),
                shift
        );
    }
}
