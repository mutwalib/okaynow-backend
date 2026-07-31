package com.okaynow.payroll.controller;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.payroll.domain.InvoiceStatus;
import com.okaynow.payroll.dto.ClientInvoiceResponse;
import com.okaynow.payroll.dto.CreateClientInvoiceRequest;
import com.okaynow.payroll.dto.SettlementResponse;
import com.okaynow.payroll.service.InvoiceService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/finance/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvoiceController {

    private final InvoiceService invoiceService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<PagedResponse<ClientInvoiceResponse>> list(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID clientProfileId,
            @RequestParam(required = false) UUID facilityProfileId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 50, sort = "issuedDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(invoiceService.list(
                status, clientProfileId, facilityProfileId, q, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientInvoiceResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.get(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(
            @PathVariable UUID id, Authentication authentication) throws Exception {
        var report = invoiceService.exportPdf(id, userService.getByEmail(authentication.getName()));
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.filename() + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(report.bytes());
    }

    @GetMapping("/uninvoiced")
    public ResponseEntity<List<SettlementResponse>> uninvoiced(
            @RequestParam(required = false) UUID clientProfileId,
            @RequestParam(required = false) UUID facilityProfileId) {
        if ((clientProfileId == null) == (facilityProfileId == null)) {
            throw new com.okaynow.common.exception.BadRequestException(
                    "Provide exactly one of clientProfileId or facilityProfileId");
        }
        if (clientProfileId != null) {
            return ResponseEntity.ok(invoiceService.uninvoicedPendingForClient(clientProfileId));
        }
        return ResponseEntity.ok(invoiceService.uninvoicedPendingForFacility(facilityProfileId));
    }

    @PostMapping
    public ResponseEntity<ClientInvoiceResponse> create(
            @Valid @RequestBody CreateClientInvoiceRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                invoiceService.create(request, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/generate-outstanding")
    public ResponseEntity<List<ClientInvoiceResponse>> generateOutstanding(
            @RequestParam(defaultValue = "true") boolean sendNow,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                invoiceService.generateOutstandingInvoices(
                        userService.getByEmail(authentication.getName()), sendNow));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<ClientInvoiceResponse> send(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(
                invoiceService.send(id, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<ClientInvoiceResponse> markPaid(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(
                invoiceService.markPaid(id, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<ClientInvoiceResponse> voidInvoice(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(
                invoiceService.voidInvoice(id, userService.getByEmail(authentication.getName())));
    }
}
