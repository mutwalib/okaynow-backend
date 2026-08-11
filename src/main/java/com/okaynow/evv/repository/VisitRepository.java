package com.okaynow.evv.repository;

import com.okaynow.evv.domain.ClockMethod;
import com.okaynow.evv.domain.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    Optional<Visit> findByShiftId(UUID shiftId);

    boolean existsByShiftId(UUID shiftId);

    List<Visit> findByShiftIdIn(Collection<UUID> shiftIds);

    @Query("""
            select count(v) from Visit v
            where v.clockInAt >= :since
              and (
                v.method = :manual
                or (v.clockOutAt is not null and v.clientArrivalConfirmed = false)
              )
            """)
    long countExceptionsSince(
            @Param("since") Instant since,
            @Param("manual") ClockMethod manual);
}
