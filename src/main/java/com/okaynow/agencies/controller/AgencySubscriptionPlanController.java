package com.okaynow.agencies.controller;

import com.okaynow.agencies.dto.SubscriptionPlanCatalogResponse;
import com.okaynow.agencies.service.SubscriptionPlanCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agencies/subscription-plans")
@RequiredArgsConstructor
public class AgencySubscriptionPlanController {

    private final SubscriptionPlanCatalogService catalogService;

    /** Public catalog for marketing and agency billing — features managed by platform admin. */
    @GetMapping
    public ResponseEntity<List<SubscriptionPlanCatalogResponse>> list() {
        return ResponseEntity.ok(catalogService.listForAgencies());
    }
}
