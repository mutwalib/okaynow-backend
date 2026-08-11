package com.okaynow.marketplace.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.EntityListeners;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-qualification marketplace policy: matching mode, credentials, surge, EVV, travel.
 */
@Entity
@Table(name = "qualification_rule_packs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_qualification_rule_packs_qual", columnNames = "qualification")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationRulePack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Qualification qualification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ShiftChannel preferredChannel = ShiftChannel.BOTH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private MatchingMode matchingMode = MatchingMode.RADIUS;

    /** When false, required credentials are recorded but not enforced on claim. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean enforceCredentials = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "qualification_rule_pack_credentials",
            joinColumns = @JoinColumn(name = "rule_pack_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", length = 32)
    @Builder.Default
    private Set<CredentialType> requiredCredentials = new LinkedHashSet<>();

    /** Block claim if a required credential expires within this many days. */
    @Column(nullable = false, columnDefinition = "integer default 30")
    @Builder.Default
    private int credentialExpiryBlockDays = 30;

    /** Minimum notice (hours) before shift start for caregiver cancellation without flag. */
    @Column(nullable = false, columnDefinition = "integer default 4")
    @Builder.Default
    private int cancelNoticeHours = 4;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean surgeEligible = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean evvRequired = false;

    /** Max one-way drive minutes when matchingMode is DRIVE_TIME. */
    private Integer maxDriveMinutes;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean travelPayEnabled = false;

    @Column(precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal travelPayPerMinute = BigDecimal.ZERO;

    // --- Facility escalation thresholds (hours before shift start) ---

    @Column(nullable = false, columnDefinition = "integer default 4")
    @Builder.Default
    private int escalationTier1Hours = 4;

    @Column(nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal escalationTier1SurgeBonus = new BigDecimal("3.00");

    @Column(nullable = false, columnDefinition = "integer default 5")
    @Builder.Default
    private int escalationTier1RadiusBonusMiles = 5;

    @Column(nullable = false, columnDefinition = "integer default 2")
    @Builder.Default
    private int escalationTier2Hours = 2;

    @Column(nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal escalationTier2SurgeBonus = new BigDecimal("6.00");

    @Column(nullable = false, columnDefinition = "integer default 10")
    @Builder.Default
    private int escalationTier2RadiusBonusMiles = 10;

    @Column(nullable = false, columnDefinition = "integer default 1")
    @Builder.Default
    private int escalationTier3Hours = 1;

    @Column(nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal escalationTier3SurgeBonus = new BigDecimal("12.00");

    @Column(nullable = false, columnDefinition = "integer default 15")
    @Builder.Default
    private int escalationTier3RadiusBonusMiles = 15;

    @LastModifiedDate
    private Instant updatedAt;
}
