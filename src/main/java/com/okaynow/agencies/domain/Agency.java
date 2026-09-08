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

    /**
     * Operational access (approval / suspend / block). Independent of Stripe subscription.
     * Existing tenants default to ACTIVE; new registrations set PENDING_APPROVAL explicitly.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, columnDefinition = "varchar(24) not null default 'ACTIVE'")
    @Builder.Default
    private AgencyAccessStatus accessStatus = AgencyAccessStatus.ACTIVE;

    /** Optional admin note for the latest access decision (e.g. why suspended). */
    @Column(length = 1000)
    private String accessStatusNote;

    /** When the agency was first approved (PENDING_APPROVAL → ACTIVE). */
    private Instant approvedAt;

    private Instant accessStatusUpdatedAt;

    private String stripeCustomerId;

    private String stripeSubscriptionId;

    private Instant subscriptionPeriodStart;

    private Instant subscriptionPeriodEnd;

    /** Stripe Connect Express account id (acct_...) for collecting home invoices. */
    private String stripeConnectAccountId;

    /** Cached from Stripe account.updated — charges_enabled. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean stripeConnectChargesEnabled = false;

    /** Cached from Stripe account.updated — payouts_enabled. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean stripeConnectPayoutsEnabled = false;

    /** When true and subscription is active, agency appears in the public directory. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean directoryListed = false;

    /** When true, caregivers can express interest / apply to join this agency's roster. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean hiringOpen = false;

    /** Short note shown to caregivers when hiring is open. */
    @Column(length = 1000)
    private String hiringNote;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public boolean accessIsActive() {
        return accessStatus == AgencyAccessStatus.ACTIVE;
    }

    public boolean accessIsPendingApproval() {
        return accessStatus == AgencyAccessStatus.PENDING_APPROVAL;
    }

    /** Billing/subscription window allows writes (ignores accessStatus). */
    public boolean subscriptionAllowsWrites() {
        return subscriptionStatus == SubscriptionStatus.ACTIVE
                || subscriptionStatus == SubscriptionStatus.TRIAL
                || subscriptionStatus == SubscriptionStatus.PAST_DUE;
    }

    public boolean allowsOperationalWrites() {
        return accessIsActive() && subscriptionAllowsWrites();
    }

    public boolean subscriptionAllowsDirectoryListing() {
        return accessIsActive()
                && directoryListed
                && (subscriptionStatus == SubscriptionStatus.ACTIVE
                || subscriptionStatus == SubscriptionStatus.TRIAL
                || subscriptionStatus == SubscriptionStatus.PAST_DUE);
    }
}
