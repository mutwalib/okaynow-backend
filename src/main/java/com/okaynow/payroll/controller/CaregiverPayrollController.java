package com.okaynow.payroll.controller;

import com.okaynow.common.dto.PagedResponse;
import com.okaynow.payroll.dto.CaregiverPayEntryResponse;
import com.okaynow.payroll.dto.CaregiverPaySummaryResponse;
import com.okaynow.payroll.service.CaregiverPayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/payroll/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverPayrollController {

    private final CaregiverPayrollService caregiverPayrollService;

    @GetMapping("/summary")
    public ResponseEntity<CaregiverPaySummaryResponse> summary(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return ResponseEntity.ok(caregiverPayrollService.summary(
                authentication.getName(), periodStart, periodEnd));
    }

    @GetMapping("/entries")
    public ResponseEntity<PagedResponse<CaregiverPayEntryResponse>> entries(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @PageableDefault(size = 50, sort = "shiftDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(caregiverPayrollService.entries(
                authentication.getName(), periodStart, periodEnd, pageable));
    }
}
