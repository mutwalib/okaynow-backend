package com.okaynow.roster.repository;

import com.okaynow.roster.domain.AgencyCaregiver;
import com.okaynow.roster.domain.AgencyCaregiverStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgencyCaregiverRepository extends JpaRepository<AgencyCaregiver, UUID> {

    List<AgencyCaregiver> findByAgencyIdOrderByInvitedAtDesc(UUID agencyId);

    List<AgencyCaregiver> findByCaregiverProfileIdOrderByInvitedAtDesc(UUID caregiverProfileId);

    Optional<AgencyCaregiver> findByAgencyIdAndCaregiverProfileId(UUID agencyId, UUID caregiverProfileId);

    Optional<AgencyCaregiver> findByIdAndCaregiverProfileId(UUID id, UUID caregiverProfileId);

    boolean existsByAgencyIdAndCaregiverProfileIdAndStatus(
            UUID agencyId, UUID caregiverProfileId, AgencyCaregiverStatus status);
}
