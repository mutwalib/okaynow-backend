package com.okaynow.roster.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.roster.domain.AgencyCaregiver;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.roster.dto.AgencyRosterEntryResponse;
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
    public AgencyRosterEntryResponse suspend(UUID agencyUserId, UUID rosterId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        AgencyCaregiver row = agencyCaregiverRepository.findById(rosterId)
                .orElseThrow(() -> new ResourceNotFoundException("Roster entry not found"));
        if (!row.getAgency().getId().equals(agency.getId())) {
            throw new ResourceNotFoundException("Roster entry not found");
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

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
