package com.okaynow.agencies.repository;

import com.okaynow.agencies.domain.AgencyStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgencyStaffRepository extends JpaRepository<AgencyStaff, UUID> {

    Optional<AgencyStaff> findFirstByUserId(UUID userId);

    List<AgencyStaff> findByAgencyId(UUID agencyId);

    boolean existsByAgencyIdAndUserId(UUID agencyId, UUID userId);
}
