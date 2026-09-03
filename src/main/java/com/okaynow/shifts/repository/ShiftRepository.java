package com.okaynow.shifts.repository;

import com.okaynow.shifts.domain.Shift;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.LocalDate;

public interface ShiftRepository extends JpaRepository<Shift, UUID>, JpaSpecificationExecutor<Shift> {

    /**
     * Pessimistic write lock used by booking operations. Always acquired AFTER the
     * caregiver profile lock (consistent lock ordering avoids deadlocks).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shift s where s.id = :id")
    Optional<Shift> findByIdForUpdate(@Param("id") UUID id);

    boolean existsBySeriesIdAndDate(UUID seriesId, LocalDate date);

    Optional<Shift> findFirstBySeriesIdAndOpenEndedTrueOrderByDateDesc(UUID seriesId);

    @Query("""
            select distinct s.seriesId from Shift s
            where s.openEnded = true
              and s.scheduleType = com.okaynow.shifts.domain.ShiftScheduleType.DAILY_ROUTINE
              and s.seriesId is not null
              and (:clientProfileId is null or s.clientProfileId = :clientProfileId)
              and (:facilityProfileId is null or s.facilityProfileId = :facilityProfileId)
            """)
    List<UUID> findOpenEndedSeriesIds(
            @Param("clientProfileId") UUID clientProfileId,
            @Param("facilityProfileId") UUID facilityProfileId);

    @Query("""
            select distinct s.seriesId from Shift s
            where s.openEnded = true
              and s.scheduleType = com.okaynow.shifts.domain.ShiftScheduleType.DAILY_ROUTINE
              and s.seriesId is not null
              and (
                s.facilityProfileId = :facilityProfileId
                or (s.facilityProfileId is null and s.clientProfileId is null and s.createdBy = :facilityUserId)
              )
            """)
    List<UUID> findOpenEndedSeriesIdsForFacility(
            @Param("facilityProfileId") UUID facilityProfileId,
            @Param("facilityUserId") UUID facilityUserId);

    /**
     * Active shifts for a family or facility in a date window (includes ±1 day
     * so overnight windows can be overlap-checked).
     */
    @Query("""
            select s from Shift s
            where s.status not in (
                com.okaynow.shifts.domain.ShiftStatus.CANCELLED,
                com.okaynow.shifts.domain.ShiftStatus.NO_SHOW
              )
              and s.date between :from and :to
              and (
                (:clientProfileId is not null and s.clientProfileId = :clientProfileId)
                or (:facilityProfileId is not null and s.facilityProfileId = :facilityProfileId)
              )
            """)
    List<Shift> findActiveForOwnerBetween(
            @Param("clientProfileId") UUID clientProfileId,
            @Param("facilityProfileId") UUID facilityProfileId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Future/today client shifts that still need caregivers and can accept
     * private assignment (DRAFT / HELD / OPEN).
     */
    @Query("""
            select s from Shift s
            where s.clientProfileId = :clientProfileId
              and s.date >= :fromDate
              and s.status in (
                com.okaynow.shifts.domain.ShiftStatus.DRAFT,
                com.okaynow.shifts.domain.ShiftStatus.HELD,
                com.okaynow.shifts.domain.ShiftStatus.OPEN
              )
              and s.filledSlots < s.requiredHeadcount
            order by s.date asc, s.startTime asc
            """)
    List<Shift> findOpenAssignableForClientFrom(
            @Param("clientProfileId") UUID clientProfileId,
            @Param("fromDate") LocalDate fromDate);

    /**
     * Open facility marketplace seats eligible for surge / radius escalation.
     */
    @Query("""
            select s from Shift s
            where s.status = com.okaynow.shifts.domain.ShiftStatus.OPEN
              and s.marketplacePosted = true
              and s.marketplaceSlots > 0
              and s.facilityProfileId is not null
              and s.date between :from and :to
            """)
    List<Shift> findOpenFacilityMarketplaceBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Shifts from today onward that still need caregivers on the open board or
     * held/claimed partial fill — excludes pure DRAFT private schedule days so
     * open-ended routines do not flood the ops cockpit.
     */
    @Query("""
            select s from Shift s
            where s.date >= :fromDate
              and s.status in (
                com.okaynow.shifts.domain.ShiftStatus.OPEN,
                com.okaynow.shifts.domain.ShiftStatus.HELD,
                com.okaynow.shifts.domain.ShiftStatus.CLAIMED
              )
              and s.filledSlots < s.requiredHeadcount
            order by s.date asc, s.startTime asc
            """)
    List<Shift> findOpenUnfilledFrom(@Param("fromDate") LocalDate fromDate);

    List<Shift> findByAgencyIdOrderByDateDescStartTimeDesc(UUID agencyId);

    /**
     * Staffing board: opened to roster coverage, already assigned, or created
     * from an accepted home request. Private home-schedule drafts stay off this list.
     * Upcoming first (soonest date at the top).
     */
    @Query("""
            select s from Shift s
            where s.agencyId = :agencyId
              and s.status <> com.okaynow.shifts.domain.ShiftStatus.CANCELLED
              and s.date >= :fromDate
              and (
                s.marketplacePosted = true
                or s.filledSlots > 0
                or s.shiftRequestId is not null
              )
            order by s.date asc, s.startTime asc
            """)
    List<Shift> findOpenOrAssignedByAgencyId(
            @Param("agencyId") UUID agencyId,
            @Param("fromDate") LocalDate fromDate);

    @Query("""
            select s from Shift s
            where s.agencyId in :agencyIds
              and s.status = com.okaynow.shifts.domain.ShiftStatus.OPEN
              and s.marketplacePosted = true
              and s.marketplaceSlots > 0
              and s.date >= :fromDate
            order by s.date asc, s.startTime asc
            """)
    List<Shift> findOpenRosterBroadcastForAgencies(
            @Param("agencyIds") java.util.Collection<UUID> agencyIds,
            @Param("fromDate") LocalDate fromDate);
}
