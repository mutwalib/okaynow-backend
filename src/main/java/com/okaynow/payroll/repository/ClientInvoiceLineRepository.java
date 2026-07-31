package com.okaynow.payroll.repository;

import com.okaynow.payroll.domain.ClientInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClientInvoiceLineRepository extends JpaRepository<ClientInvoiceLine, UUID> {

    @Query("""
            select count(l) > 0 from ClientInvoiceLine l
            where l.caregiverProfileId = :caregiverProfileId
              and l.invoice.status <> com.okaynow.payroll.domain.InvoiceStatus.VOID
              and (:clientProfileId is null or l.invoice.clientProfileId = :clientProfileId)
              and (:facilityProfileId is null or l.invoice.facilityProfileId = :facilityProfileId)
            """)
    boolean existsActiveConversionForCaregiver(
            @Param("clientProfileId") UUID clientProfileId,
            @Param("facilityProfileId") UUID facilityProfileId,
            @Param("caregiverProfileId") UUID caregiverProfileId);

    @Query("""
            select distinct l.caregiverProfileId from ClientInvoiceLine l
            where l.caregiverProfileId is not null
              and l.invoice.status <> com.okaynow.payroll.domain.InvoiceStatus.VOID
              and (:clientProfileId is null or l.invoice.clientProfileId = :clientProfileId)
              and (:facilityProfileId is null or l.invoice.facilityProfileId = :facilityProfileId)
            """)
    List<UUID> findReportedConversionCaregiverIds(
            @Param("clientProfileId") UUID clientProfileId,
            @Param("facilityProfileId") UUID facilityProfileId);
}
