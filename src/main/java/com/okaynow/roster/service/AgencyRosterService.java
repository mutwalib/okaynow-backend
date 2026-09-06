package com.okaynow.roster.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.NotificationService;
import com.okaynow.roster.domain.AgencyCaregiver;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.roster.domain.RosterPayClassification;
import com.okaynow.roster.dto.AgencyRosterEntryResponse;
import com.okaynow.roster.dto.AgencyRosterMemberDetailResponse;
import com.okaynow.roster.dto.CaregiverLookupResponse;
import com.okaynow.roster.dto.InviteRosterCaregiverRequest;
import com.okaynow.roster.dto.UpdateRosterPayOfferRequest;
import com.okaynow.roster.repository.AgencyCaregiverRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencyRosterService {

    private static final EnumSet<AgencyCaregiverStatus> RATE_VISIBLE = EnumSet.of(
            AgencyCaregiverStatus.INVITED,
            AgencyCaregiverStatus.ACTIVE,
            AgencyCaregiverStatus.SUSPENDED);

    private final AgencyCaregiverRepository agencyCaregiverRepository;
    private final AgencyAccessService agencyAccessService;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<AgencyRosterEntryResponse> listForAgency(UUID agencyUserId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        return agencyCaregiverRepository.findByAgencyIdOrderByInvitedAtDesc(agency.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgencyRosterEntryResponse> listMembershipsForCaregiver(UUID caregiverUserId) {
        CaregiverProfile profile = caregiverProfileRepository.findByUserId(caregiverUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        return agencyCaregiverRepository.findByCaregiverProfileIdOrderByInvitedAtDesc(profile.getId()).stream()
                .filter(r -> r.getStatus() == AgencyCaregiverStatus.ACTIVE
                        || r.getStatus() == AgencyCaregiverStatus.SUSPENDED
                        || r.getStatus() == AgencyCaregiverStatus.INVITED)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CaregiverLookupResponse lookupByEmail(UUID agencyUserId, String email) {
        agencyAccessService.requireAgencyForUser(agencyUserId);
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        User caregiverUser = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("No caregiver found for that email"));
        if (caregiverUser.getRole() != Role.CAREGIVER) {
            throw new BadRequestException("That account is not a caregiver profile");
        }
        CaregiverProfile profile = caregiverProfileRepository.findByUserId(caregiverUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        var roster = agencyCaregiverRepository.findByAgencyIdAndCaregiverProfileId(
                agency.getId(), profile.getId());
        AgencyCaregiverStatus rosterStatus = roster.map(AgencyCaregiver::getStatus).orElse(null);
        boolean alreadyOnRoster = rosterStatus == AgencyCaregiverStatus.ACTIVE
                || rosterStatus == AgencyCaregiverStatus.INVITED;
        boolean canReinvite = rosterStatus == AgencyCaregiverStatus.REMOVED;
        return new CaregiverLookupResponse(
                profile.getId(),
                profile.getFirstName(),
                profile.getLastName(),
                caregiverUser.getEmail(),
                new java.util.ArrayList<>(profile.getQualifications()),
                profile.getHomeCity(),
                profile.getHomeState(),
                profile.getServiceRadiusMiles(),
                alreadyOnRoster,
                canReinvite,
                rosterStatus != null ? rosterStatus.name() : null);
    }

    @Transactional(readOnly = true)
    public AgencyRosterMemberDetailResponse getMemberDetail(UUID agencyUserId, UUID rosterId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        AgencyCaregiver row = agencyCaregiverRepository.findById(rosterId)
                .orElseThrow(() -> new ResourceNotFoundException("Roster entry not found"));
        if (!row.getAgency().getId().equals(agency.getId())) {
            throw new ResourceNotFoundException("Roster entry not found");
        }
        return toDetailResponse(row);
    }

    @Transactional(readOnly = true)
    public List<AgencyRosterEntryResponse> listInvitesForCaregiver(UUID caregiverUserId) {
        CaregiverProfile profile = caregiverProfileRepository.findByUserId(caregiverUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        return agencyCaregiverRepository.findByCaregiverProfileIdOrderByInvitedAtDesc(profile.getId()).stream()
                .filter(r -> r.getStatus() == AgencyCaregiverStatus.INVITED)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Agreed pay for an agency↔caregiver relationship when the row is still live.
     * Used so caregivers never see the agency's default/standard shift rate.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> findAgreedPayRate(UUID agencyId, UUID caregiverProfileId) {
        if (agencyId == null || caregiverProfileId == null) {
            return Optional.empty();
        }
        return agencyCaregiverRepository.findByAgencyIdAndCaregiverProfileId(agencyId, caregiverProfileId)
                .filter(r -> RATE_VISIBLE.contains(r.getStatus()))
                .map(AgencyCaregiver::getAgreedPayRate)
                .filter(rate -> rate != null && rate.compareTo(BigDecimal.ZERO) > 0);
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveCaregiverPayRate(
            UUID agencyId, UUID caregiverProfileId, BigDecimal fallback) {
        return findAgreedPayRate(agencyId, caregiverProfileId).orElse(fallback);
    }

    @Transactional
    public AgencyRosterEntryResponse invite(UUID agencyUserId, InviteRosterCaregiverRequest request) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Caregiver email is required");
        }
        BigDecimal payRate = requirePayRate(request.payRate());
        RosterPayClassification classification = requireClassification(request.payClassification());
        User caregiverUser = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException(
                        "No caregiver account exists for that email — they must register as a caregiver first"));
        if (caregiverUser.getRole() != Role.CAREGIVER) {
            throw new BadRequestException("That account is not a caregiver profile");
        }
        CaregiverProfile profile = caregiverProfileRepository.findByUserId(caregiverUser.getId())
                .orElseThrow(() -> new BadRequestException("Caregiver profile not found"));
        if (!profile.isAgencyRosterEnabled()) {
            throw new BadRequestException(
                    "This caregiver is not accepting agency roster invites. Ask them to enable Agency rosters on their profile.");
        }

        Instant now = Instant.now();
        var existing = agencyCaregiverRepository.findByAgencyIdAndCaregiverProfileId(
                agency.getId(), profile.getId());
        AgencyCaregiver saved;
        if (existing.isPresent()) {
            AgencyCaregiverStatus status = existing.get().getStatus();
            if (status == AgencyCaregiverStatus.INVITED || status == AgencyCaregiverStatus.ACTIVE) {
                throw new ConflictException("This caregiver is already on the roster or has a pending invite");
            }
            if (status == AgencyCaregiverStatus.SUSPENDED) {
                throw new BadRequestException(
                        "This caregiver is suspended. Unsuspend them instead of sending a new invite.");
            }
            // REMOVED (or any other non-active state): send a fresh invite that requires accept.
            AgencyCaregiver row = existing.get();
            row.setStatus(AgencyCaregiverStatus.INVITED);
            row.setInviteMessage(trimOrNull(request.message()));
            applyPayOffer(row, payRate, classification, now);
            row.setRespondedAt(null);
            row.setRemovedAt(null);
            row.setInvitedAt(now);
            saved = agencyCaregiverRepository.save(row);
        } else {
            AgencyCaregiver row = AgencyCaregiver.builder()
                    .agency(agency)
                    .caregiverProfile(profile)
                    .status(AgencyCaregiverStatus.INVITED)
                    .inviteMessage(trimOrNull(request.message()))
                    .agreedPayRate(payRate)
                    .payClassification(classification)
                    .payOfferUpdatedAt(now)
                    .build();
            saved = agencyCaregiverRepository.save(row);
        }
        notifyCaregiver(
                caregiverUser,
                NotificationType.ROSTER_INVITE,
                "Roster invite from " + agency.getDisplayName(),
                inviteBody(agency, payRate, classification, trimOrNull(request.message())),
                saved);
        return toResponse(saved);
    }

    @Transactional
    public AgencyRosterEntryResponse updatePayOffer(
            UUID agencyUserId, UUID rosterId, UpdateRosterPayOfferRequest request) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        AgencyCaregiver row = requireAgencyRosterRow(agency, rosterId);
        if (row.getStatus() == AgencyCaregiverStatus.REMOVED) {
            throw new BadRequestException("Cannot revise pay for a removed roster member — invite them again");
        }
        BigDecimal payRate = requirePayRate(request.payRate());
        RosterPayClassification classification = requireClassification(request.payClassification());
        applyPayOffer(row, payRate, classification, Instant.now());
        AgencyCaregiver saved = agencyCaregiverRepository.save(row);
        User caregiverUser = saved.getCaregiverProfile().getUser();
        notifyCaregiver(
                caregiverUser,
                NotificationType.ROSTER_PAY_OFFER_UPDATED,
                "Pay offer updated — " + agency.getDisplayName(),
                offerRevisionBody(agency, payRate, classification),
                saved);
        return toResponse(saved);
    }

    @Transactional
    public AgencyRosterEntryResponse acceptInvite(UUID caregiverUserId, UUID rosterId) {
        CaregiverProfile profile = caregiverProfileRepository.findByUserId(caregiverUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        if (!profile.isAgencyRosterEnabled()) {
            throw new BadRequestException(
                    "Agency rosters are turned off on your profile. Enable them under How you get work first.");
        }
        AgencyCaregiver row = agencyCaregiverRepository.findByIdAndCaregiverProfileId(rosterId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Roster invite not found"));
        if (row.getStatus() != AgencyCaregiverStatus.INVITED) {
            throw new BadRequestException("This invite is no longer pending");
        }
        if (row.getAgreedPayRate() == null || row.getAgreedPayRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(
                    "This invite has no pay offer. Ask the agency to resend with an hourly rate.");
        }
        row.setStatus(AgencyCaregiverStatus.ACTIVE);
        row.setRespondedAt(Instant.now());
        return toResponse(agencyCaregiverRepository.save(row));
    }

    @Transactional
    public AgencyRosterEntryResponse reactivate(UUID agencyUserId, UUID rosterId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        AgencyCaregiver row = requireAgencyRosterRow(agency, rosterId);
        if (row.getStatus() != AgencyCaregiverStatus.SUSPENDED) {
            throw new BadRequestException("Only suspended roster members can be reactivated");
        }
        row.setStatus(AgencyCaregiverStatus.ACTIVE);
        row.setRespondedAt(Instant.now());
        row.setRemovedAt(null);
        return toResponse(agencyCaregiverRepository.save(row));
    }

    @Transactional
    public AgencyRosterEntryResponse remove(UUID agencyUserId, UUID rosterId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        AgencyCaregiver row = requireAgencyRosterRow(agency, rosterId);
        if (row.getStatus() == AgencyCaregiverStatus.REMOVED) {
            throw new BadRequestException("This caregiver is already removed from the roster");
        }
        boolean wasInvite = row.getStatus() == AgencyCaregiverStatus.INVITED;
        row.setStatus(AgencyCaregiverStatus.REMOVED);
        row.setRemovedAt(Instant.now());
        row.setRespondedAt(Instant.now());
        AgencyCaregiver saved = agencyCaregiverRepository.save(row);
        User caregiverUser = saved.getCaregiverProfile().getUser();
        notifyCaregiver(
                caregiverUser,
                NotificationType.ROSTER_REMOVED,
                wasInvite
                        ? "Roster invite cancelled — " + agency.getDisplayName()
                        : "Removed from roster — " + agency.getDisplayName(),
                wasInvite
                        ? agency.getDisplayName() + " cancelled your pending roster invite."
                        : agency.getDisplayName()
                                + " removed you from their roster. You will no longer see their shifts.",
                saved);
        return toResponse(saved);
    }

    @Transactional
    public AgencyRosterEntryResponse suspend(UUID agencyUserId, UUID rosterId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        AgencyCaregiver row = requireAgencyRosterRow(agency, rosterId);
        if (row.getStatus() != AgencyCaregiverStatus.ACTIVE) {
            throw new BadRequestException("Only active roster members can be suspended");
        }
        row.setStatus(AgencyCaregiverStatus.SUSPENDED);
        row.setRespondedAt(Instant.now());
        return toResponse(agencyCaregiverRepository.save(row));
    }

    public void assertActiveOnRoster(UUID agencyId, UUID caregiverProfileId) {
        agencyCaregiverRepository.findByAgencyIdAndCaregiverProfileId(agencyId, caregiverProfileId)
                .filter(r -> r.getStatus() == AgencyCaregiverStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException(
                        "Caregiver must be an active member of the agency roster"));
    }

    /** Apply pay when accepting a caregiver interest / hiring application. */
    public void applyPayOfferOnAccept(
            AgencyCaregiver row, BigDecimal payRate, RosterPayClassification classification) {
        applyPayOffer(row, requirePayRate(payRate), requireClassification(classification), Instant.now());
    }

    public static String offerSentence(BigDecimal payRate, RosterPayClassification classification) {
        StringBuilder sb = new StringBuilder();
        sb.append("The offer is $")
                .append(payRate.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append(" per hour");
        if (classification == RosterPayClassification.W2) {
            sb.append(" as W-2 (agency runs payroll with taxes)");
        } else if (classification == RosterPayClassification.NON_W2) {
            sb.append(" (not W-2)");
        }
        sb.append(".");
        return sb.toString();
    }

    private void applyPayOffer(
            AgencyCaregiver row,
            BigDecimal payRate,
            RosterPayClassification classification,
            Instant at) {
        row.setAgreedPayRate(payRate);
        row.setPayClassification(classification);
        row.setPayOfferUpdatedAt(at);
    }

    private void notifyCaregiver(
            User caregiverUser,
            NotificationType type,
            String title,
            String body,
            AgencyCaregiver row) {
        String payload = "{\"rosterId\":\"" + row.getId()
                + "\",\"agencyId\":\"" + row.getAgency().getId()
                + "\",\"status\":\"" + row.getStatus().name()
                + "\",\"action\":\"" + type.name() + "\"";
        if (row.getAgreedPayRate() != null) {
            payload += ",\"agreedPayRate\":" + row.getAgreedPayRate().toPlainString();
        }
        if (row.getPayClassification() != null) {
            payload += ",\"payClassification\":\"" + row.getPayClassification().name() + "\"";
        }
        payload += "}";
        notificationService.notifyUser(caregiverUser, type, title, body, payload);
    }

    private static String inviteBody(
            Agency agency,
            BigDecimal payRate,
            RosterPayClassification classification,
            String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(agency.getDisplayName()).append(" invited you to their roster. ");
        sb.append(offerSentence(payRate, classification));
        if (message != null) {
            sb.append(" Message: ").append(message);
        }
        return sb.toString();
    }

    private static String offerRevisionBody(
            Agency agency, BigDecimal payRate, RosterPayClassification classification) {
        return agency.getDisplayName() + " updated your pay offer. "
                + offerSentence(payRate, classification);
    }

    private static RosterPayClassification requireClassification(RosterPayClassification value) {
        if (value == null) {
            throw new BadRequestException("Choose whether this offer is W-2 or not W-2");
        }
        return value;
    }

    private static BigDecimal requirePayRate(BigDecimal payRate) {
        if (payRate == null || payRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Hourly pay offer is required (must be greater than 0)");
        }
        return payRate.setScale(2, RoundingMode.HALF_UP);
    }

    private AgencyRosterEntryResponse toResponse(AgencyCaregiver row) {
        CaregiverProfile cg = row.getCaregiverProfile();
        return new AgencyRosterEntryResponse(
                row.getId(),
                row.getAgency().getId(),
                row.getAgency().getDisplayName(),
                cg.getId(),
                cg.getFirstName(),
                cg.getLastName(),
                cg.getUser().getEmail(),
                row.getStatus(),
                row.getInviteMessage(),
                row.getAgreedPayRate(),
                row.getPayClassification(),
                row.getPayOfferUpdatedAt(),
                row.getInvitedAt(),
                row.getRespondedAt());
    }

    private AgencyRosterMemberDetailResponse toDetailResponse(AgencyCaregiver row) {
        CaregiverProfile cg = row.getCaregiverProfile();
        User user = cg.getUser();
        return new AgencyRosterMemberDetailResponse(
                row.getId(),
                row.getStatus(),
                row.getInviteMessage(),
                row.getAgreedPayRate(),
                row.getPayClassification(),
                row.getPayOfferUpdatedAt(),
                row.getInvitedAt(),
                row.getRespondedAt(),
                row.getRemovedAt(),
                cg.getId(),
                user.getId(),
                cg.getFirstName(),
                cg.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                new java.util.ArrayList<>(cg.getQualifications()),
                cg.getOtherQualificationDetail(),
                cg.getHourlyRateMin(),
                cg.getHourlyRateMax(),
                cg.getServiceRadiusMiles(),
                cg.getHomeAddressLine(),
                cg.getHomeCity(),
                cg.getHomeState(),
                cg.getHomeZip(),
                cg.getProfilePhotoUrl(),
                cg.getCvUrl(),
                cg.getCvUploadedAt(),
                cg.getRatingAvg(),
                cg.getRatingCount());
    }

    private AgencyCaregiver requireAgencyRosterRow(Agency agency, UUID rosterId) {
        AgencyCaregiver row = agencyCaregiverRepository.findById(rosterId)
                .orElseThrow(() -> new ResourceNotFoundException("Roster entry not found"));
        if (!row.getAgency().getId().equals(agency.getId())) {
            throw new ResourceNotFoundException("Roster entry not found");
        }
        return row;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
