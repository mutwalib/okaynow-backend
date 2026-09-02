package com.okaynow.agencies.domain;

import com.okaynow.users.domain.Qualification;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "agencies")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false)
    private String legalName;

    @Column(nullable = false)
    private String displayName;

    private String licenseNumber;

    private String addressLine;

    private String city;

    @Builder.Default
    private String state = "MA";

    private String zip;

    private Double lat;

    private Double lng;

    /** Miles from agency HQ used for directory geo filter when no explicit radius set. */
    @Builder.Default
    private Integer serviceRadiusMiles = 50;

    @Column(length = 4000)
    private String publicDescription;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agency_qualifications", joinColumns = @JoinColumn(name = "agency_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "qualification", nullable = false)
    @Builder.Default
    private Set<Qualification> qualificationsSupported = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.STARTER;

    private String stripeCustomerId;

    private String stripeSubscriptionId;

    private Instant subscriptionPeriodStart;

    private Instant subscriptionPeriodEnd;

    /** When true and subscription is active, agency appears in the public directory. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean directoryListed = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public boolean subscriptionAllowsWrites() {
        return subscriptionStatus == SubscriptionStatus.ACTIVE
                || subscriptionStatus == SubscriptionStatus.TRIAL
                || subscriptionStatus == SubscriptionStatus.PAST_DUE;
    }

    public boolean subscriptionAllowsDirectoryListing() {
        return directoryListed
                && (subscriptionStatus == SubscriptionStatus.ACTIVE
                || subscriptionStatus == SubscriptionStatus.TRIAL);
    }
}
