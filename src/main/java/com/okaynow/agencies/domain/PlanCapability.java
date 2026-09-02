package com.okaynow.agencies.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Canonical agency subscription capabilities shown on billing cards and edited
 * by super admins as checkboxes.
 */
public enum PlanCapability {
    DIRECTORY_LISTING(
            SubscriptionPlan.STARTER,
            "Directory & homes",
            "Directory listing in the home agency search"),
    HOME_CONNECTIONS(
            SubscriptionPlan.STARTER,
            "Directory & homes",
            "Home connection requests and messaging"),
    AGENCY_CONSOLE(
            SubscriptionPlan.STARTER,
            "Agency console",
            "Agency console — roster, connections, and profile"),

    INCLUDES_STARTER(
            SubscriptionPlan.PROFESSIONAL,
            "Includes",
            "Everything in Starter",
            true),
    SHIFT_SCHEDULING(
            SubscriptionPlan.PROFESSIONAL,
            "Operations",
            "Full shift scheduling and assignments"),
    ROSTER_SHIFT_INBOX(
            SubscriptionPlan.PROFESSIONAL,
            "Operations",
            "Caregiver roster and shift inbox"),
    RATE_CARDS(
            SubscriptionPlan.PROFESSIONAL,
            "Billing & payroll",
            "Rate cards (pay and bill rates)"),
    HOURS_EXPORT(
            SubscriptionPlan.PROFESSIONAL,
            "Billing & payroll",
            "EVV-backed hours export (CSV for payroll)"),

    INCLUDES_PROFESSIONAL(
            SubscriptionPlan.FEATURED,
            "Includes",
            "Everything in Professional",
            true),
    FEATURED_PLACEMENT(
            SubscriptionPlan.FEATURED,
            "Directory boost",
            "Featured placement in the home directory"),
    PRIORITY_RANKING(
            SubscriptionPlan.FEATURED,
            "Directory boost",
            "Priority ranking in location search"),
    VERIFIED_BADGE(
            SubscriptionPlan.FEATURED,
            "Directory boost",
            "Verified badge on your public profile");

    private final SubscriptionPlan introducedIn;
    private final String category;
    private final String label;
    private final boolean inheritSummary;

    PlanCapability(SubscriptionPlan introducedIn, String category, String label) {
        this(introducedIn, category, label, false);
    }

    PlanCapability(SubscriptionPlan introducedIn, String category, String label, boolean inheritSummary) {
        this.introducedIn = introducedIn;
        this.category = category;
        this.label = label;
        this.inheritSummary = inheritSummary;
    }

    public SubscriptionPlan getIntroducedIn() {
        return introducedIn;
    }

    public String getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }

    public boolean isInheritSummary() {
        return inheritSummary;
    }

    public static Set<PlanCapability> recommendedFor(SubscriptionPlan plan) {
        return switch (plan) {
            case STARTER -> EnumSet.of(
                    DIRECTORY_LISTING,
                    HOME_CONNECTIONS,
                    AGENCY_CONSOLE);
            case PROFESSIONAL -> EnumSet.of(
                    INCLUDES_STARTER,
                    SHIFT_SCHEDULING,
                    ROSTER_SHIFT_INBOX,
                    RATE_CARDS,
                    HOURS_EXPORT);
            case FEATURED -> EnumSet.of(
                    INCLUDES_PROFESSIONAL,
                    FEATURED_PLACEMENT,
                    PRIORITY_RANKING,
                    VERIFIED_BADGE);
        };
    }

    public static List<PlanCapability> orderedForPlan(SubscriptionPlan plan) {
        return Arrays.stream(values())
                .filter(cap -> cap.introducedIn == plan)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    public static List<PlanCapability> inheritedFromLowerTiers(SubscriptionPlan plan) {
        return Arrays.stream(values())
                .filter(cap -> !cap.inheritSummary && cap.introducedIn.ordinal() < plan.ordinal())
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    public static List<String> labelsFromCapabilities(Set<PlanCapability> selected, SubscriptionPlan plan) {
        return orderedForPlan(plan).stream()
                .filter(selected::contains)
                .map(PlanCapability::getLabel)
                .toList();
    }

    public static Set<PlanCapability> fromFeatureLabels(List<String> features, SubscriptionPlan plan) {
        if (features == null || features.isEmpty()) {
            return recommendedFor(plan);
        }
        Set<String> normalized = features.stream()
                .map(f -> f == null ? "" : f.trim().toLowerCase(Locale.ROOT))
                .filter(f -> !f.isEmpty())
                .collect(Collectors.toSet());
        EnumSet<PlanCapability> matched = EnumSet.noneOf(PlanCapability.class);
        for (PlanCapability cap : orderedForPlan(plan)) {
            if (normalized.contains(cap.label.toLowerCase(Locale.ROOT))) {
                matched.add(cap);
            }
        }
        if (matched.isEmpty()) {
            return recommendedFor(plan);
        }
        if (plan.ordinal() >= SubscriptionPlan.PROFESSIONAL.ordinal()) {
            matched.add(INCLUDES_STARTER);
        }
        if (plan == SubscriptionPlan.FEATURED) {
            matched.add(INCLUDES_PROFESSIONAL);
        }
        return matched;
    }
}
