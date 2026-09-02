package com.okaynow.agencies.service;

import com.okaynow.agencies.domain.PlanCapability;
import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionPlanDefinition;
import com.okaynow.agencies.dto.PlanCapabilityResponse;
import com.okaynow.agencies.dto.SubscriptionPlanCatalogResponse;
import com.okaynow.agencies.dto.UpdateSubscriptionPlanCatalogRequest;
import com.okaynow.agencies.repository.SubscriptionPlanDefinitionRepository;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanCatalogService {

    private final SubscriptionPlanDefinitionRepository repository;

    @Transactional(readOnly = true)
    public SubscriptionPlanDefinition requireDefinition(SubscriptionPlan plan) {
        return repository.findById(plan)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
    }

    @Transactional(readOnly = true)
    public List<PlanCapabilityResponse> listCapabilitiesForPlan(SubscriptionPlan plan) {
        return PlanCapability.orderedForPlan(plan).stream()
                .map(cap -> PlanCapabilityResponse.of(cap, plan))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanCapabilityResponse> listAllCapabilities() {
        List<PlanCapabilityResponse> all = new ArrayList<>();
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            all.addAll(listCapabilitiesForPlan(plan));
        }
        return all;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanCatalogResponse> listForAgencies() {
        return repository.findAllByEnabledTrueOrderBySortOrderAscPlanAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanCatalogResponse> listForSuperAdmin() {
        return repository.findAll().stream()
                .sorted((a, b) -> {
                    int order = Integer.compare(a.getSortOrder(), b.getSortOrder());
                    return order != 0 ? order : a.getPlan().name().compareTo(b.getPlan().name());
                })
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SubscriptionPlanCatalogResponse updateForSuperAdmin(
            SubscriptionPlan plan, UpdateSubscriptionPlanCatalogRequest request) {
        SubscriptionPlanDefinition row = repository.findById(plan)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        row.setDisplayName(request.displayName().trim());
        row.setTagline(trimOrNull(request.tagline()));
        replaceFeatures(row, request.features());
        row.setMonthlyPriceCents(request.monthlyPriceCents());
        if (request.sortOrder() != null) {
            row.setSortOrder(request.sortOrder());
        }
        if (request.enabled() != null) {
            row.setEnabled(request.enabled());
        }
        return toResponse(repository.save(row));
    }

    @Transactional
    public void ensureDefaultsSeeded() {
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            if (repository.existsById(plan)) {
                repository.findById(plan).ifPresent(row -> {
                    if (row.getMonthlyPriceCents() < 50) {
                        row.setMonthlyPriceCents(defaultFor(plan).getMonthlyPriceCents());
                        repository.save(row);
                    }
                });
                continue;
            }
            repository.save(defaultFor(plan));
        }
    }

    private static SubscriptionPlanDefinition defaultFor(SubscriptionPlan plan) {
        return switch (plan) {
            case STARTER -> SubscriptionPlanDefinition.builder()
                    .plan(SubscriptionPlan.STARTER)
                    .displayName("Starter")
                    .tagline("Get listed and connect with homes.")
                    .features(new ArrayList<>(PlanCapability.labelsFromCapabilities(
                            PlanCapability.recommendedFor(SubscriptionPlan.STARTER),
                            SubscriptionPlan.STARTER)))
                    .monthlyPriceCents(29_900)
                    .sortOrder(0)
                    .enabled(true)
                    .build();
            case PROFESSIONAL -> SubscriptionPlanDefinition.builder()
                    .plan(SubscriptionPlan.PROFESSIONAL)
                    .displayName("Professional")
                    .tagline("Run scheduling, roster, and payroll export.")
                    .features(new ArrayList<>(PlanCapability.labelsFromCapabilities(
                            PlanCapability.recommendedFor(SubscriptionPlan.PROFESSIONAL),
                            SubscriptionPlan.PROFESSIONAL)))
                    .monthlyPriceCents(79_900)
                    .sortOrder(1)
                    .enabled(true)
                    .build();
            case FEATURED -> SubscriptionPlanDefinition.builder()
                    .plan(SubscriptionPlan.FEATURED)
                    .displayName("Featured")
                    .tagline("Stand out in the home directory.")
                    .features(new ArrayList<>(PlanCapability.labelsFromCapabilities(
                            PlanCapability.recommendedFor(SubscriptionPlan.FEATURED),
                            SubscriptionPlan.FEATURED)))
                    .monthlyPriceCents(99_900)
                    .sortOrder(2)
                    .enabled(true)
                    .build();
        };
    }

    private void replaceFeatures(SubscriptionPlanDefinition row, List<String> features) {
        row.getFeatures().clear();
        row.getFeatures().addAll(new ArrayList<>(sanitizeFeatures(features)));
    }

    private SubscriptionPlanCatalogResponse toResponse(SubscriptionPlanDefinition row) {
        return new SubscriptionPlanCatalogResponse(
                row.getPlan(),
                row.getDisplayName(),
                row.getTagline(),
                List.copyOf(row.getFeatures()),
                row.getMonthlyPriceCents(),
                formatPriceDisplay(row.getMonthlyPriceCents()),
                row.getSortOrder(),
                row.isEnabled());
    }

    static String formatPriceDisplay(int monthlyPriceCents) {
        if (monthlyPriceCents <= 0) {
            return "Contact us";
        }
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        currency.setMaximumFractionDigits(monthlyPriceCents % 100 == 0 ? 0 : 2);
        return currency.format(monthlyPriceCents / 100.0) + "/mo";
    }

    private static List<String> sanitizeFeatures(List<String> features) {
        if (features == null || features.isEmpty()) {
            throw new BadRequestException("At least one feature is required");
        }
        List<String> cleaned = features.stream()
                .map(f -> f == null ? "" : f.trim())
                .filter(f -> !f.isEmpty())
                .toList();
        if (cleaned.isEmpty()) {
            throw new BadRequestException("At least one feature is required");
        }
        return cleaned;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
