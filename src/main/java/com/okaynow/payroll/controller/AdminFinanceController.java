package com.okaynow.payroll.controller;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.payroll.domain.PaymentStatus;
import com.okaynow.payroll.dto.FinanceSummaryResponse;
import com.okaynow.payroll.dto.SettlementResponse;
import com.okaynow.payroll.dto.UpdatePaymentStatusRequest;
import com.okaynow.payroll.service.FinanceService;
import com.okaynow.payroll.service.SettlementService;
import com.okaynow.users.domain.User;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFinanceController {

    private final FinanceService financeService;
    private final SettlementService settlementService;
    private final UserService userService;

    @GetMapping("/summary")
    public ResponseEntity<FinanceSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return ResponseEntity.ok(financeService.summary(periodStart, periodEnd));
    }

    @GetMapping("/settlements")
    public ResponseEntity<PagedResponse<SettlementResponse>> settlements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(required = false) PaymentStatus clientPaymentStatus,
            @RequestParam(required = false) PaymentStatus caregiverPaymentStatus,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 50, sort = "shiftDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(financeService.listSettlements(
                periodStart, periodEnd, clientPaymentStatus, caregiverPaymentStatus, q, pageable));
    }

    @GetMapping("/settlements/by-shift/{shiftId}")
    public ResponseEntity<SettlementResponse> byShift(@PathVariable UUID shiftId) {
        return ResponseEntity.ok(settlementService.getByShiftId(shiftId));
    }

    @PatchMapping("/settlements/{id}/client-payment")
    public ResponseEntity<SettlementResponse> clientPayment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentStatusRequest request,
            Authentication authentication) {
        User actor = userService.getByEmail(authentication.getName());
        return ResponseEntity.ok(settlementService.markClientPayment(id, request.status(), actor));
    }

    @PatchMapping("/settlements/{id}/caregiver-payment")
    public ResponseEntity<SettlementResponse> caregiverPayment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePaymentStatusRequest request,
            Authentication authentication) {
        User actor = userService.getByEmail(authentication.getName());
        return ResponseEntity.ok(settlementService.markCaregiverPayment(id, request.status(), actor));
    }
}
