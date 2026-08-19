package com.okaynow.marketplace.service;

import com.okaynow.marketplace.domain.CredentialType;
import com.okaynow.marketplace.domain.MatchingMode;
import com.okaynow.marketplace.domain.QualificationRulePack;
import com.okaynow.marketplace.domain.ShiftChannel;
import com.okaynow.marketplace.dto.QualificationRulePackResponse;
import com.okaynow.marketplace.dto.UpdateQualificationRulePackRequest;
import com.okaynow.marketplace.repository.QualificationRulePackRepository;
import com.okaynow.users.domain.Qualification;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QualificationRulePackService implements ApplicationRunner {

    private final QualificationRulePackRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Qualification q : Qualification.values()) {
            getOrCreate(q);
        }
    }

    @Transactional
    public QualificationRulePack getOrCreate(Qualification qualification) {
        return repository.findByQualification(qualification)
                .orElseGet(() -> repository.save(defaultPack(qualification)));
    }

    @Transactional(readOnly = true)
    public List<QualificationRulePackResponse> listAll() {
        for (Qualification q : Qualification.values()) {
            getOrCreate(q);
        }
        return repository.findAll().stream()
                .sorted((a, b) -> a.getQualification().compareTo(b.getQualification()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public QualificationRulePackResponse update(
            Qualification qualification, UpdateQualificationRulePackRequest request) {
        QualificationRulePack pack = getOrCreate(qualification);
        pack.setPreferredChannel(request.preferredChannel());
        pack.setMatchingMode(request.matchingMode());
        pack.setEnforceCredentials(request.enforceCredentials());
        pack.getRequiredCredentials().clear();
        if (request.requiredCredentials() != null) {
            pack.getRequiredCredentials().addAll(request.requiredCredentials());
        }
        pack.setCredentialExpiryBlockDays(request.credentialExpiryBlockDays());
        pack.setCancelNoticeHours(request.cancelNoticeHours());
        pack.setSurgeEligible(request.surgeEligible());
        pack.setEvvRequired(request.evvRequired());
        pack.setMaxDriveMinutes(request.maxDriveMinutes());
        pack.setTravelPayEnabled(request.travelPayEnabled());
        pack.setTravelPayPerMinute(
                request.travelPayPerMinute() != null
                        ? request.travelPayPerMinute()
                        : BigDecimal.ZERO);
        pack.setEscalationTier1Hours(request.escalationTier1Hours());
        pack.setEscalationTier1SurgeBonus(request.escalationTier1SurgeBonus());
        pack.setEscalationTier1RadiusBonusMiles(request.escalationTier1RadiusBonusMiles());
        pack.setEscalationTier2Hours(request.escalationTier2Hours());
        pack.setEscalationTier2SurgeBonus(request.escalationTier2SurgeBonus());
        pack.setEscalationTier2RadiusBonusMiles(request.escalationTier2RadiusBonusMiles());
        pack.setEscalationTier3Hours(request.escalationTier3Hours());
        pack.setEscalationTier3SurgeBonus(request.escalationTier3SurgeBonus());
        pack.setEscalationTier3RadiusBonusMiles(request.escalationTier3RadiusBonusMiles());
        return toResponse(repository.save(pack));
    }

    public QualificationRulePackResponse toResponse(QualificationRulePack pack) {
        return new QualificationRulePackResponse(
                pack.getId(),
                pack.getQualification(),
                pack.getPreferredChannel(),
                pack.getMatchingMode(),
                pack.isEnforceCredentials(),
                Set.copyOf(pack.getRequiredCredentials()),
                pack.getCredentialExpiryBlockDays(),
                pack.getCancelNoticeHours(),
                pack.isSurgeEligible(),
                pack.isEvvRequired(),
                pack.getMaxDriveMinutes(),
                pack.isTravelPayEnabled(),
                pack.getTravelPayPerMinute(),
                pack.getEscalationTier1Hours(),
                pack.getEscalationTier1SurgeBonus(),
                pack.getEscalationTier1RadiusBonusMiles(),
                pack.getEscalationTier2Hours(),
                pack.getEscalationTier2SurgeBonus(),
                pack.getEscalationTier2RadiusBonusMiles(),
                pack.getEscalationTier3Hours(),
                pack.getEscalationTier3SurgeBonus(),
                pack.getEscalationTier3RadiusBonusMiles());
    }

    private QualificationRulePack defaultPack(Qualification q) {
        return switch (q) {
            case RN -> QualificationRulePack.builder()
                    .qualification(q)
                    .preferredChannel(ShiftChannel.FACILITY)
                    .matchingMode(MatchingMode.RADIUS)
                    .enforceCredentials(false)
                    .requiredCredentials(creds(
                            CredentialType.LICENSE, CredentialType.BLS,
                            CredentialType.TB_TEST, CredentialType.CORI))
                    .credentialExpiryBlockDays(30)
                    .cancelNoticeHours(8)
                    .surgeEligible(true)
                    .evvRequired(false)
                    .build();
            case LPN -> QualificationRulePack.builder()
                    .qualification(q)
                    .preferredChannel(ShiftChannel.FACILITY)
                    .matchingMode(MatchingMode.RADIUS)
                    .enforceCredentials(false)
                    .requiredCredentials(creds(
                            CredentialType.LICENSE, CredentialType.BLS,
                            CredentialType.TB_TEST, CredentialType.CORI))
                    .credentialExpiryBlockDays(30)
                    .cancelNoticeHours(6)
                    .surgeEligible(true)
                    .evvRequired(false)
                    .build();
            case CNA -> QualificationRulePack.builder()
                    .qualification(q)
                    .preferredChannel(ShiftChannel.BOTH)
                    .matchingMode(MatchingMode.RADIUS)
                    .enforceCredentials(false)
                    .requiredCredentials(creds(
                            CredentialType.LICENSE, CredentialType.CPR, CredentialType.CORI))
                    .credentialExpiryBlockDays(30)
                    .cancelNoticeHours(4)
                    .surgeEligible(true)
                    .evvRequired(false)
                    .build();
            case HHA -> QualificationRulePack.builder()
                    .qualification(q)
                    .preferredChannel(ShiftChannel.HOME)
                    .matchingMode(MatchingMode.DRIVE_TIME)
                    .enforceCredentials(false)
                    .requiredCredentials(creds(CredentialType.CPR, CredentialType.CORI))
                    .credentialExpiryBlockDays(30)
                    .cancelNoticeHours(2)
                    .surgeEligible(false)
                    .evvRequired(true)
                    .maxDriveMinutes(40)
                    .travelPayEnabled(true)
                    .travelPayPerMinute(new BigDecimal("0.35"))
                    .build();
            case PCA -> QualificationRulePack.builder()
                    .qualification(q)
                    .preferredChannel(ShiftChannel.HOME)
                    .matchingMode(MatchingMode.DRIVE_TIME)
                    .enforceCredentials(false)
                    .requiredCredentials(creds(CredentialType.CPR, CredentialType.CORI))
                    .credentialExpiryBlockDays(30)
                    .cancelNoticeHours(2)
                    .surgeEligible(false)
                    .evvRequired(true)
                    .maxDriveMinutes(45)
                    .travelPayEnabled(true)
                    .travelPayPerMinute(new BigDecimal("0.35"))
                    .build();
            case MAP -> QualificationRulePack.builder()
                    .qualification(q)
                    .preferredChannel(ShiftChannel.HOME)
                    .matchingMode(MatchingMode.RADIUS)
                    .enforceCredentials(false)
                    .requiredCredentials(creds(
                            CredentialType.LICENSE, CredentialType.CPR, CredentialType.CORI))
                    .credentialExpiryBlockDays(30)
                    .cancelNoticeHours(4)
                    .surgeEligible(false)
                    .evvRequired(true)
                    .build();
            case OTHER -> QualificationRulePack.builder()
                    .qualification(q)
                    .preferredChannel(ShiftChannel.BOTH)
                    .matchingMode(MatchingMode.RADIUS)
                    .enforceCredentials(false)
                    .requiredCredentials(creds(CredentialType.CORI))
                    .credentialExpiryBlockDays(30)
                    .cancelNoticeHours(4)
                    .surgeEligible(false)
                    .evvRequired(false)
                    .build();
        };
    }

    private static Set<CredentialType> creds(CredentialType... types) {
        return new LinkedHashSet<>(Arrays.asList(types));
    }
}
