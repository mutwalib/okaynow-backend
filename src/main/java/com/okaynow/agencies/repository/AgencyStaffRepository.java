package com.okaynow.agencies.repository;

import com.okaynow.agencies.domain.AgencyStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgencyStaffRepository extends JpaRepository<AgencyStaff, UUID> {

    Optional<AgencyStaff> findFirstByUserId(UUID userId);

    @Query("""
            SELECT s FROM AgencyStaff s
            JOIN FETCH s.agency a
            WHERE s.user.id = :userId
            """)
    Optional<AgencyStaff> findFirstByUserIdWithAgency(@Param("userId") UUID userId);

    List<AgencyStaff> findByAgencyId(UUID agencyId);

    @Query("""
            SELECT s FROM AgencyStaff s
            JOIN FETCH s.user u
            JOIN FETCH s.agency a
            WHERE a.id = :agencyId
            ORDER BY s.createdAt ASC
            """)
    List<AgencyStaff> findByAgencyIdWithUsers(@Param("agencyId") UUID agencyId);

    long countByAgencyId(UUID agencyId);

    boolean existsByAgencyIdAndUserId(UUID agencyId, UUID userId);
}
