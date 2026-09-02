package com.okaynow.agencies.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subscription_plan_definitions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDefinition {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SubscriptionPlan plan;

    @Column(nullable = false, length = 64)
    private String displayName;

    @Column(length = 500)
    private String tagline;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(
            name = "subscription_plan_features",
            joinColumns = @JoinColumn(name = "plan"))
    @OrderColumn(name = "sort_order")
    @Column(name = "feature_text", nullable = false, length = 500)
    @Builder.Default
    private List<String> features = new ArrayList<>();

    /** Optional display label, e.g. "$299/mo" — billing still uses Stripe price IDs. */
    @Column(length = 32)
    private String priceLabel;

    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
