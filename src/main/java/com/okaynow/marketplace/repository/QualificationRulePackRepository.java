package com.okaynow.marketplace.repository;

import com.okaynow.marketplace.domain.QualificationRulePack;
import com.okaynow.users.domain.Qualification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QualificationRulePackRepository extends JpaRepository<QualificationRulePack, UUID> {

    Optional<QualificationRulePack> findByQualification(Qualification qualification);
}
