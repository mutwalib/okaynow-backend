package com.okaynow.agencies.service;

import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionPlanDefinition;
import com.okaynow.agencies.dto.SubscriptionPlanCatalogResponse;
import com.okaynow.agencies.dto.UpdateSubscriptionPlanCatalogRequest;
import com.okaynow.agencies.repository.SubscriptionPlanDefinitionRepository;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanCatalogService {

    private final SubscriptionPlanDefinitionRepository repository;

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
        row.setFeatures(sanitizeFeatures(request.features()));
        row.setPriceLabel(trimOrNull(request.priceLabel()));
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
                    .features(new ArrayList<>(List.of(
                            "Directory listing in the home agency search",
                            "Home connection requests and messaging",
                            "Agency console — roster, connections, and profile")))
                    .priceLabel(null)
                    .sortOrder(0)
                    .enabled(true)
                    .build();
            case PROFESSIONAL -> SubscriptionPlanDefinition.builder()
                    .plan(SubscriptionPlan.PROFESSIONAL)
                    .displayName("Professional")
                    .tagline("Run scheduling, roster, and payroll export.")
                    .features(new ArrayList<>(List.of(
                            "Everything in Starter",
                            "Full shift scheduling and assignments",
                            "Caregiver roster and shift inbox",
                            "Rate cards (pay and bill rates)",
                            "EVV-backed hours export (CSV for payroll)")))
                    .priceLabel(null)
                    .sortOrder(1)
                    .enabled(true)
                    .build();
            case FEATURED -> SubscriptionPlanDefinition.builder()
                    .plan(SubscriptionPlan.FEATURED)
                    .displayName("Featured")
                    .tagline("Stand out in the home directory.")
                    .features(new ArrayList<>(List.of(
                            "Everything in Professional",
                            "Featured placement in the home directory",
                            "Priority ranking in location search",
                            "Verified badge on your public profile")))
                    .priceLabel(null)
                    .sortOrder(2)
                    .enabled(true)
                    .build();
        };
    }

    private SubscriptionPlanCatalogResponse toResponse(SubscriptionPlanDefinition row) {
        return new SubscriptionPlanCatalogResponse(
                row.getPlan(),
                row.getDisplayName(),
                row.getTagline(),
                List.copyOf(row.getFeatures()),
                row.getPriceLabel(),
                row.getSortOrder(),
                row.isEnabled());
    }

    private static List<String> sanitizeFeatures(List<String> features) {
        if (features == null || features.isEmpty()) {
            throw new BadRequestException("At least one feature is required");
        }
        return features.stream()
                .map(f -> f == null ? "" : f.trim())
                .filter(f -> !f.isEmpty())
                .toList();
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
