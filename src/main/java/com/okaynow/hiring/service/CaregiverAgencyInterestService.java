package com.okaynow.hiring.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.hiring.domain.CaregiverAgencyInterest;
import com.okaynow.hiring.domain.CaregiverAgencyInterestStatus;
import com.okaynow.hiring.dto.CaregiverAgencyInterestResponse;
import com.okaynow.hiring.dto.ExpressInterestRequest;
import com.okaynow.hiring.repository.CaregiverAgencyInterestRepository;
import com.okaynow.roster.domain.AgencyCaregiver;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import com.okaynow.roster.repository.AgencyCaregiverRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.repository.CaregiverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverAgencyInterestService {

    private final CaregiverAgencyInterestRepository interestRepository;
    private final AgencyRepository agencyRepository;
    private final AgencyAccessService agencyAccessService;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final AgencyCaregiverRepository agencyCaregiverRepository;

    @Transactional(readOnly = true)
    public List<CaregiverAgencyInterestResponse> listForCaregiver(UUID caregiverUserId) {
        CaregiverProfile profile = requireCaregiverProfile(caregiverUserId);
        return interestRepository.findByCaregiverProfileIdWithAgency(profile.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CaregiverAgencyInterestResponse> listForAgency(UUID agencyUserId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        return interestRepository.findByAgencyIdWithCaregiver(agency.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CaregiverAgencyInterestResponse expressInterest(UUID caregiverUserId, ExpressInterestRequest request) {
        if (request.agencyId() == null) {
            throw new BadRequestException("agencyId is required");
        }
        CaregiverProfile profile = requireCaregiverProfile(caregiverUserId);
        Agency agency = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
        if (!agency.isHiringOpen() || !agency.subscriptionAllowsWrites()) {
            throw new BadRequestException("This agency is not currently accepting caregiver applications");
        }
        var roster = agencyCaregiverRepository.findByAgencyIdAndCaregiverProfileId(
                agency.getId(), profile.getId());
        if (roster.isPresent()
                && (roster.get().getStatus() == AgencyCaregiverStatus.ACTIVE
                || roster.get().getStatus() == AgencyCaregiverStatus.INVITED)) {
            throw new ConflictException("You are already on this agency's roster or have a pending invite");
        }

        var existing = interestRepository.findByAgencyIdAndCaregiverProfileId(agency.getId(), profile.getId());
        if (existing.isPresent()) {
            CaregiverAgencyInterest row = existing.get();
            if (row.getStatus() == CaregiverAgencyInterestStatus.PENDING
                    || row.getStatus() == CaregiverAgencyInterestStatus.ACCEPTED) {
                throw new ConflictException("You already expressed interest in this agency");
            }
            row.setStatus(CaregiverAgencyInterestStatus.PENDING);
            row.setMessage(trimOrNull(request.message()));
            row.setRespondedAt(null);
            return toResponse(interestRepository.save(row));
        }

        CaregiverAgencyInterest row = CaregiverAgencyInterest.builder()
                .agency(agency)
                .caregiverProfile(profile)
                .status(CaregiverAgencyInterestStatus.PENDING)
                .message(trimOrNull(request.message()))
                .build();
        return toResponse(interestRepository.save(row));
    }

    @Transactional
    public CaregiverAgencyInterestResponse accept(UUID agencyUserId, UUID interestId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        CaregiverAgencyInterest interest = interestRepository.findById(interestId)
                .orElseThrow(() -> new ResourceNotFoundException("Interest not found"));
        if (!interest.getAgency().getId().equals(agency.getId())) {
            throw new ResourceNotFoundException("Interest not found");
        }
        if (interest.getStatus() != CaregiverAgencyInterestStatus.PENDING) {
            throw new BadRequestException("This application was already handled");
        }
        CaregiverProfile profile = interest.getCaregiverProfile();
        var rosterOpt = agencyCaregiverRepository.findByAgencyIdAndCaregiverProfileId(
                agency.getId(), profile.getId());
        if (rosterOpt.isPresent()) {
            AgencyCaregiver roster = rosterOpt.get();
            roster.setStatus(AgencyCaregiverStatus.ACTIVE);
            roster.setRespondedAt(Instant.now());
            agencyCaregiverRepository.save(roster);
        } else {
            agencyCaregiverRepository.save(AgencyCaregiver.builder()
                    .agency(agency)
                    .caregiverProfile(profile)
                    .status(AgencyCaregiverStatus.ACTIVE)
                    .inviteMessage("Accepted from caregiver interest")
                    .respondedAt(Instant.now())
                    .build());
        }
        interest.setStatus(CaregiverAgencyInterestStatus.ACCEPTED);
        interest.setRespondedAt(Instant.now());
        return toResponse(interestRepository.save(interest));
    }

    @Transactional
    public CaregiverAgencyInterestResponse decline(UUID agencyUserId, UUID interestId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        CaregiverAgencyInterest interest = interestRepository.findById(interestId)
                .orElseThrow(() -> new ResourceNotFoundException("Interest not found"));
        if (!interest.getAgency().getId().equals(agency.getId())) {
            throw new ResourceNotFoundException("Interest not found");
        }
        if (interest.getStatus() != CaregiverAgencyInterestStatus.PENDING) {
            throw new BadRequestException("This application was already handled");
        }
        interest.setStatus(CaregiverAgencyInterestStatus.DECLINED);
        interest.setRespondedAt(Instant.now());
        return toResponse(interestRepository.save(interest));
    }

    private CaregiverProfile requireCaregiverProfile(UUID userId) {
        return caregiverProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
    }

    private CaregiverAgencyInterestResponse toResponse(CaregiverAgencyInterest row) {
        Agency agency = row.getAgency();
        CaregiverProfile cg = row.getCaregiverProfile();
        return new CaregiverAgencyInterestResponse(
                row.getId(),
                agency.getId(),
                agency.getDisplayName(),
                agency.getCity(),
                agency.getState(),
                agency.isHiringOpen(),
                cg.getId(),
                cg.getFirstName(),
                cg.getLastName(),
                cg.getUser().getEmail(),
                new ArrayList<>(cg.getQualifications()),
                row.getStatus(),
                row.getMessage(),
                row.getCreatedAt(),
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
