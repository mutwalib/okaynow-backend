package com.okaynow.payroll.repository;

import com.okaynow.payroll.domain.ClientInvoice;
import com.okaynow.payroll.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClientInvoiceRepository extends JpaRepository<ClientInvoice, UUID> {

    @EntityGraph(attributePaths = {"lines"})
    @Query("select i from ClientInvoice i where i.id = :id")
    Optional<ClientInvoice> findWithLinesById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"lines"})
    Page<ClientInvoice> findByClientProfileIdAndStatusNotOrderByIssuedDateDescCreatedAtDesc(
            UUID clientProfileId, InvoiceStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    Page<ClientInvoice> findByFacilityProfileIdAndStatusNotOrderByIssuedDateDescCreatedAtDesc(
            UUID facilityProfileId, InvoiceStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    Page<ClientInvoice> findByAgencyIdOrderByIssuedDateDescCreatedAtDesc(UUID agencyId, Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    Page<ClientInvoice> findAllByOrderByIssuedDateDescCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    Page<ClientInvoice> findByStatusOrderByIssuedDateDescCreatedAtDesc(
            InvoiceStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"lines"})
    @Query("""
            select i from ClientInvoice i
            left join com.okaynow.users.domain.ClientProfile c on c.id = i.clientProfileId
            left join com.okaynow.users.domain.FacilityProfile f on f.id = i.facilityProfileId
            where (:status is null or i.status = :status)
              and (:clientProfileId is null or i.clientProfileId = :clientProfileId)
              and (:facilityProfileId is null or i.facilityProfileId = :facilityProfileId)
              and (
                :q is null or :q = ''
                or lower(i.invoiceNumber) like lower(concat('%', cast(:q as string), '%'))
                or lower(c.firstName) like lower(concat('%', cast(:q as string), '%'))
                or lower(c.lastName) like lower(concat('%', cast(:q as string), '%'))
                or lower(concat(c.firstName, ' ', c.lastName)) like lower(concat('%', cast(:q as string), '%'))
                or lower(f.facilityName) like lower(concat('%', cast(:q as string), '%'))
              )
            """)
    Page<ClientInvoice> search(
            @Param("status") InvoiceStatus status,
            @Param("clientProfileId") UUID clientProfileId,
            @Param("facilityProfileId") UUID facilityProfileId,
            @Param("q") String q,
            Pageable pageable);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
