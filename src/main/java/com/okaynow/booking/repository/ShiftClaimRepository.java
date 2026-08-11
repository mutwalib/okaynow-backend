package com.okaynow.booking.repository;

import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.users.domain.CaregiverProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ShiftClaimRepository extends JpaRepository<ShiftClaim, UUID> {

    @EntityGraph(attributePaths = {"shift"})
    Page<ShiftClaim> findByCaregiverProfileId(UUID caregiverProfileId, Pageable pageable);

    @EntityGraph(attributePaths = {"shift", "caregiverProfile", "caregiverProfile.user"})
    Page<ShiftClaim> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"shift", "caregiverProfile", "caregiverProfile.user"})
    Page<ShiftClaim> findByStatus(ShiftClaimStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {
            "shift",
            "caregiverProfile",
            "caregiverProfile.user",
            "caregiverProfile.qualifications"
    })
    List<ShiftClaim> findByShiftIdOrderByClaimedAtDesc(UUID shiftId);

    @EntityGraph(attributePaths = {
            "shift",
            "caregiverProfile",
            "caregiverProfile.user",
            "caregiverProfile.qualifications"
    })
    List<ShiftClaim> findByShiftIdInAndStatusIn(
            Collection<UUID> shiftIds, Collection<ShiftClaimStatus> statuses);

    Optional<ShiftClaim> findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
            UUID shiftId, UUID caregiverProfileId, Collection<ShiftClaimStatus> statuses);

    Optional<ShiftClaim> findFirstByShiftIdAndStatusIn(UUID shiftId, Collection<ShiftClaimStatus> statuses);

    long countByShiftIdAndStatusIn(UUID shiftId, Collection<ShiftClaimStatus> statuses);

    @Query("""
            select distinct cp from ShiftClaim c
            join c.caregiverProfile cp
            where c.shift.clientProfileId = :clientProfileId
            order by cp.lastName asc, cp.firstName asc
            """)
    List<CaregiverProfile> findDistinctCaregiversForClient(
            @Param("clientProfileId") UUID clientProfileId);

    @Query("""
            select distinct cp from ShiftClaim c
            join c.caregiverProfile cp
            where c.shift.facilityProfileId = :facilityProfileId
            order by cp.lastName asc, cp.firstName asc
            """)
    List<CaregiverProfile> findDistinctCaregiversForFacility(
            @Param("facilityProfileId") UUID facilityProfileId);

    /**
     * True when the caregiver already holds an active (PENDING/CONFIRMED) claim on another
     * shift whose time window overlaps [startTime, endTime) on the given date.
     */
    @Query("""
            select count(c) > 0 from ShiftClaim c
            where c.caregiverProfile.id = :caregiverProfileId
              and c.status in :activeStatuses
              and c.shift.date = :date
              and c.shift.startTime < :endTime
              and c.shift.endTime > :startTime
            """)
    boolean existsOverlappingClaim(@Param("caregiverProfileId") UUID caregiverProfileId,
                                   @Param("date") LocalDate date,
                                   @Param("startTime") LocalTime startTime,
                                   @Param("endTime") LocalTime endTime,
                                   @Param("activeStatuses") Collection<ShiftClaimStatus> activeStatuses);

    @EntityGraph(attributePaths = {"shift"})
    @Query("""
            select c from ShiftClaim c
            where c.caregiverProfile.id = :caregiverProfileId
              and c.status in :statuses
              and c.shift.id <> :excludeShiftId
            """)
    List<ShiftClaim> findActiveClaimsExcludingShift(
            @Param("caregiverProfileId") UUID caregiverProfileId,
            @Param("excludeShiftId") UUID excludeShiftId,
            @Param("statuses") Collection<ShiftClaimStatus> statuses);

    /**
     * Active claims for a caregiver on a family's future (or today) shifts —
     * used when detaching them from the client roster/schedule.
     */
    @EntityGraph(attributePaths = {"shift", "caregiverProfile", "caregiverProfile.user"})
    @Query("""
            select c from ShiftClaim c
            where c.caregiverProfile.id = :caregiverProfileId
              and c.status in :statuses
              and c.shift.clientProfileId = :clientProfileId
              and c.shift.date >= :fromDate
              and c.shift.status not in (
                com.okaynow.shifts.domain.ShiftStatus.COMPLETED,
                com.okaynow.shifts.domain.ShiftStatus.IN_PROGRESS,
                com.okaynow.shifts.domain.ShiftStatus.CANCELLED,
                com.okaynow.shifts.domain.ShiftStatus.NO_SHOW
              )
            order by c.shift.date asc, c.shift.startTime asc
            """)
    List<ShiftClaim> findActiveFutureClaimsForClientCaregiver(
            @Param("clientProfileId") UUID clientProfileId,
            @Param("caregiverProfileId") UUID caregiverProfileId,
            @Param("fromDate") LocalDate fromDate,
            @Param("statuses") Collection<ShiftClaimStatus> statuses);

    @Query("""
            select c.caregiverProfile.id, count(c) from ShiftClaim c
            where c.shift.clientProfileId = :clientProfileId
              and c.status = :completed
            group by c.caregiverProfile.id
            """)
    List<Object[]> countCompletedByCaregiverForClient(
            @Param("clientProfileId") UUID clientProfileId,
            @Param("completed") ShiftClaimStatus completed);

    @Query("""
            select c.caregiverProfile.id, count(c) from ShiftClaim c
            where c.shift.facilityProfileId = :facilityProfileId
              and c.status = :completed
            group by c.caregiverProfile.id
            """)
    List<Object[]> countCompletedByCaregiverForFacility(
            @Param("facilityProfileId") UUID facilityProfileId,
            @Param("completed") ShiftClaimStatus completed);
}
