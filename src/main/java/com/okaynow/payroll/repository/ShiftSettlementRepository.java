package com.okaynow.payroll.repository;

import com.okaynow.payroll.domain.PaymentStatus;
import com.okaynow.payroll.domain.ShiftSettlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftSettlementRepository extends JpaRepository<ShiftSettlement, UUID> {

    Optional<ShiftSettlement> findByShiftId(UUID shiftId);

    boolean existsByShiftId(UUID shiftId);

    /** Settlements for shifts whose work date falls in [dateFrom, dateTo]. */
    @Query("""
            select s from ShiftSettlement s
            left join com.okaynow.users.domain.CaregiverProfile cg on cg.id = s.caregiverProfileId
            left join com.okaynow.users.domain.ClientProfile cl on cl.id = s.clientProfileId
            left join com.okaynow.users.domain.FacilityProfile fp on fp.id = s.facilityProfileId
            where s.shiftDate >= :dateFrom
              and s.shiftDate <= :dateTo
              and (:clientStatus is null or s.clientPaymentStatus = :clientStatus)
              and (:caregiverStatus is null or s.caregiverPaymentStatus = :caregiverStatus)
              and (
                :q is null or :q = ''
                or lower(cg.firstName) like lower(concat('%', cast(:q as string), '%'))
                or lower(cg.lastName) like lower(concat('%', cast(:q as string), '%'))
                or lower(concat(cg.firstName, ' ', cg.lastName)) like lower(concat('%', cast(:q as string), '%'))
                or lower(cl.firstName) like lower(concat('%', cast(:q as string), '%'))
                or lower(cl.lastName) like lower(concat('%', cast(:q as string), '%'))
                or lower(concat(cl.firstName, ' ', cl.lastName)) like lower(concat('%', cast(:q as string), '%'))
                or lower(fp.facilityName) like lower(concat('%', cast(:q as string), '%'))
              )
            """)
    Page<ShiftSettlement> searchByShiftDateRange(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("clientStatus") PaymentStatus clientStatus,
            @Param("caregiverStatus") PaymentStatus caregiverStatus,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            select s from ShiftSettlement s
            where s.shiftDate >= :dateFrom
              and s.shiftDate <= :dateTo
            """)
    List<ShiftSettlement> findAllByShiftDateRange(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    @Query("""
            select s from ShiftSettlement s
            where s.caregiverProfileId = :caregiverProfileId
              and s.shiftDate >= :dateFrom
              and s.shiftDate <= :dateTo
            """)
    Page<ShiftSettlement> findCaregiverByShiftDateRange(
            @Param("caregiverProfileId") UUID caregiverProfileId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);

    @Query("""
            select s from ShiftSettlement s
            where s.caregiverProfileId = :caregiverProfileId
              and s.shiftDate >= :dateFrom
              and s.shiftDate <= :dateTo
            """)
    List<ShiftSettlement> findAllCaregiverByShiftDateRange(
            @Param("caregiverProfileId") UUID caregiverProfileId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    @Query("""
            select s from ShiftSettlement s
            where s.clientProfileId = :clientProfileId
              and s.clientPaymentStatus = :clientStatus
              and s.clientInvoiceId is null
            order by s.shiftDate asc
            """)
    List<ShiftSettlement> findUninvoicedPendingByClient(
            @Param("clientProfileId") UUID clientProfileId,
            @Param("clientStatus") PaymentStatus clientStatus);

    @Query("""
            select s from ShiftSettlement s
            where s.facilityProfileId = :facilityProfileId
              and s.clientPaymentStatus = :clientStatus
              and s.clientInvoiceId is null
            order by s.shiftDate asc
            """)
    List<ShiftSettlement> findUninvoicedPendingByFacility(
            @Param("facilityProfileId") UUID facilityProfileId,
            @Param("clientStatus") PaymentStatus clientStatus);

    @Query("""
            select s from ShiftSettlement s
            where s.clientPaymentStatus = :clientStatus
              and s.clientInvoiceId is null
              and (s.clientProfileId is not null or s.facilityProfileId is not null)
            order by s.clientProfileId asc, s.facilityProfileId asc, s.shiftDate asc
            """)
    List<ShiftSettlement> findAllUninvoicedPending(
            @Param("clientStatus") PaymentStatus clientStatus);

    List<ShiftSettlement> findByClientInvoiceId(UUID clientInvoiceId);

    @Query("""
            select s from ShiftSettlement s
            where s.facilityProfileId is null
              and exists (
                select 1 from com.okaynow.shifts.domain.Shift sh
                where sh.id = s.shiftId and sh.facilityProfileId is not null
              )
            """)
    List<ShiftSettlement> findMissingFacilityBillTo();
}
