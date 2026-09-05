package com.okaynow.discipline.repository;

import com.okaynow.discipline.domain.CaregiverWarning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverWarningRepository extends JpaRepository<CaregiverWarning, UUID> {

    long countByCaregiverProfileId(UUID caregiverProfileId);

    boolean existsByShiftId(UUID shiftId);

    Optional<CaregiverWarning> findByShiftId(UUID shiftId);

    List<CaregiverWarning> findByCaregiverProfileIdOrderByCreatedAtDesc(UUID caregiverProfileId);
}
