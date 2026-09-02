package com.okaynow.agencies.controller;

import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.dto.SubscriptionPlanCatalogResponse;
import com.okaynow.agencies.dto.UpdateSubscriptionPlanCatalogRequest;
import com.okaynow.agencies.service.SubscriptionPlanCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/super/subscription-plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SuperAdminSubscriptionPlanController {

    private final SubscriptionPlanCatalogService catalogService;

    @GetMapping
    public ResponseEntity<List<SubscriptionPlanCatalogResponse>> list() {
        return ResponseEntity.ok(catalogService.listForSuperAdmin());
    }

    @PutMapping("/{plan}")
    public ResponseEntity<SubscriptionPlanCatalogResponse> update(
            @PathVariable SubscriptionPlan plan,
            @Valid @RequestBody UpdateSubscriptionPlanCatalogRequest request) {
        return ResponseEntity.ok(catalogService.updateForSuperAdmin(plan, request));
    }
}
