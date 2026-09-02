package com.okaynow.hiring.repository;

import com.okaynow.hiring.domain.CaregiverAgencyInterest;
import com.okaynow.hiring.domain.CaregiverAgencyInterestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverAgencyInterestRepository extends JpaRepository<CaregiverAgencyInterest, UUID> {

    Optional<CaregiverAgencyInterest> findByAgencyIdAndCaregiverProfileId(UUID agencyId, UUID caregiverProfileId);

    List<CaregiverAgencyInterest> findByCaregiverProfileIdOrderByCreatedAtDesc(UUID caregiverProfileId);

    @Query("""
            SELECT i FROM CaregiverAgencyInterest i
            JOIN FETCH i.caregiverProfile cg
            JOIN FETCH cg.user
            WHERE i.agency.id = :agencyId
            ORDER BY i.createdAt DESC
            """)
    List<CaregiverAgencyInterest> findByAgencyIdWithCaregiver(@Param("agencyId") UUID agencyId);

    @Query("""
            SELECT i FROM CaregiverAgencyInterest i
            JOIN FETCH i.agency
            WHERE i.caregiverProfile.id = :profileId
            ORDER BY i.createdAt DESC
            """)
    List<CaregiverAgencyInterest> findByCaregiverProfileIdWithAgency(@Param("profileId") UUID profileId);

    List<CaregiverAgencyInterest> findByAgencyIdAndStatusOrderByCreatedAtDesc(
            UUID agencyId, CaregiverAgencyInterestStatus status);
}
