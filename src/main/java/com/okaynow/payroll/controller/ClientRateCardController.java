package com.okaynow.payroll.controller;

import com.okaynow.payroll.dto.ClientRateCardResponse;
import com.okaynow.payroll.service.AgencySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only rate card for clients (families and facilities) posting shifts.
 */
@RestController
@RequestMapping("/api/agency")
@RequiredArgsConstructor
public class ClientRateCardController {

    private final AgencySettingsService agencySettingsService;

    @GetMapping("/client-rates")
    @PreAuthorize("hasAnyRole('CLIENT', 'FACILITY', 'ADMIN')")
    public ResponseEntity<ClientRateCardResponse> clientRates() {
        return ResponseEntity.ok(agencySettingsService.clientRateCard());
    }
}
