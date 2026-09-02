package com.okaynow.roster.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.roster.domain.AgencyCaregiver;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.roster.dto.AgencyRosterEntryResponse;
import com.okaynow.roster.dto.AgencyRosterMemberDetailResponse;
import com.okaynow.roster.dto.CaregiverLookupResponse;
import com.okaynow.roster.dto.InviteRosterCaregiverRequest;
import com.okaynow.roster.repository.AgencyCaregiverRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencyRosterService {

    private final AgencyCaregiverRepository agencyCaregiverRepository;
    private final AgencyAccessService agencyAccessService;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final UserRepository userRepository;

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
        return new CaregiverLookupResponse(
                profile.getId(),
                profile.getFirstName(),
                profile.getLastName(),
                caregiverUser.getEmail(),
                new java.util.ArrayList<>(profile.getQualifications()),
                profile.getHomeCity(),
                profile.getHomeState(),
                profile.getServiceRadiusMiles(),
                roster.isPresent(),
                roster.map(r -> r.getStatus().name()).orElse(null));
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

    @Transactional
    public AgencyRosterEntryResponse invite(UUID agencyUserId, InviteRosterCaregiverRequest request) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Caregiver email is required");
        }
        User caregiverUser = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException(
                        "No caregiver account exists for that email — they must register as a caregiver first"));
        if (caregiverUser.getRole() != Role.CAREGIVER) {
            throw new BadRequestException("That account is not a caregiver profile");
        }
        CaregiverProfile profile = caregiverProfileRepository.findByUserId(caregiverUser.getId())
                .orElseThrow(() -> new BadRequestException("Caregiver profile not found"));

        var existing = agencyCaregiverRepository.findByAgencyIdAndCaregiverProfileId(
                agency.getId(), profile.getId());
        if (existing.isPresent()) {
            AgencyCaregiverStatus status = existing.get().getStatus();
            if (status == AgencyCaregiverStatus.INVITED || status == AgencyCaregiverStatus.ACTIVE) {
                throw new ConflictException("This caregiver is already on the roster or has a pending invite");
            }
            AgencyCaregiver row = existing.get();
            row.setStatus(AgencyCaregiverStatus.INVITED);
            row.setInviteMessage(trimOrNull(request.message()));
            row.setRespondedAt(null);
            row.setRemovedAt(null);
            return toResponse(agencyCaregiverRepository.save(row));
        }

        AgencyCaregiver row = AgencyCaregiver.builder()
                .agency(agency)
                .caregiverProfile(profile)
                .status(AgencyCaregiverStatus.INVITED)
                .inviteMessage(trimOrNull(request.message()))
                .build();
        return toResponse(agencyCaregiverRepository.save(row));
    }

    @Transactional
    public AgencyRosterEntryResponse acceptInvite(UUID caregiverUserId, UUID rosterId) {
        CaregiverProfile profile = caregiverProfileRepository.findByUserId(caregiverUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        AgencyCaregiver row = agencyCaregiverRepository.findByIdAndCaregiverProfileId(rosterId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Roster invite not found"));
        if (row.getStatus() != AgencyCaregiverStatus.INVITED) {
            throw new BadRequestException("This invite is no longer pending");
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
        if (row.getStatus() == AgencyCaregiverStatus.INVITED) {
            throw new BadRequestException("Cancel a pending invite instead of removing");
        }
        row.setStatus(AgencyCaregiverStatus.REMOVED);
        row.setRemovedAt(Instant.now());
        row.setRespondedAt(Instant.now());
        return toResponse(agencyCaregiverRepository.save(row));
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
