package com.okaynow.shiftrequests.repository;

import com.okaynow.shiftrequests.domain.ShiftRequestAgency;
import com.okaynow.shiftrequests.domain.ShiftRequestAgencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRequestAgencyRepository extends JpaRepository<ShiftRequestAgency, UUID> {

    List<ShiftRequestAgency> findByAgencyIdOrderByShiftRequest_CreatedAtDesc(UUID agencyId);

    List<ShiftRequestAgency> findByShiftRequestId(UUID shiftRequestId);

    Optional<ShiftRequestAgency> findByIdAndAgencyId(UUID id, UUID agencyId);

    @Query("""
            SELECT sra FROM ShiftRequestAgency sra
            JOIN FETCH sra.shiftRequest sr
            JOIN FETCH sr.homeUser
            LEFT JOIN FETCH sr.clientProfile
            LEFT JOIN FETCH sr.facilityProfile
            WHERE sra.agency.id = :agencyId
            ORDER BY sr.createdAt DESC
            """)
    List<ShiftRequestAgency> findInboxForAgency(@Param("agencyId") UUID agencyId);

    List<ShiftRequestAgency> findByAgencyIdAndStatusOrderByShiftRequest_CreatedAtDesc(
            UUID agencyId, ShiftRequestAgencyStatus status);
}
