package com.okaynow.payroll.repository;

import com.okaynow.payroll.domain.AgencySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencySettingsRepository extends JpaRepository<AgencySettings, Long> {
}
