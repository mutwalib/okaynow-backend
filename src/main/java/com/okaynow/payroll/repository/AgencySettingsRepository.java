package com.okaynow.payroll.repository;

import com.okaynow.payroll.domain.AgencySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AgencySettingsRepository extends JpaRepository<AgencySettings, Long> {

    Optional<AgencySettings> findByAgencyId(UUID agencyId);

    @Query("select coalesce(max(s.id), 0) from AgencySettings s")
    long findMaxId();
}
