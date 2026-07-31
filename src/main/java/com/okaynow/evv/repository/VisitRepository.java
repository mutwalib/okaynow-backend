package com.okaynow.evv.repository;

import com.okaynow.evv.domain.Visit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    Optional<Visit> findByShiftId(UUID shiftId);

    boolean existsByShiftId(UUID shiftId);

    List<Visit> findByShiftIdIn(Collection<UUID> shiftIds);
}
