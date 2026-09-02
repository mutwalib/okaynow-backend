package com.okaynow.payroll.controller;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.payroll.dto.ClientInvoiceResponse;
import com.okaynow.payroll.service.InvoicePaymentService;
import com.okaynow.payroll.service.InvoiceService;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/clients/me/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
public class ClientInvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePaymentService invoicePaymentService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<PagedResponse<ClientInvoiceResponse>> listMine(
            Authentication authentication,
            @PageableDefault(size = 50, sort = "issuedDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(invoiceService.listForClientUser(
                userService.getByEmail(authentication.getName()), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientInvoiceResponse> getMine(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(invoiceService.getForClientUser(
                id, userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<com.okaynow.agencies.dto.CheckoutSessionResponse> checkout(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(invoicePaymentService.createCheckoutForClient(
                id, userService.getByEmail(authentication.getName())));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdfMine(
            @PathVariable UUID id, Authentication authentication) throws Exception {
        var report = invoiceService.exportPdfForClient(
                id, userService.getByEmail(authentication.getName()));
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.filename() + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(report.bytes());
    }
}
