package com.okaynow.users.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "caregiver_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaregiverProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "caregiver_qualifications", joinColumns = @JoinColumn(name = "caregiver_profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "qualification")
    @Builder.Default
    private Set<Qualification> qualifications = new LinkedHashSet<>();

    private BigDecimal hourlyRateMin;

    private BigDecimal hourlyRateMax;

    private Integer serviceRadiusMiles;

    private Double homeLat;

    private Double homeLng;

    /** Public URL path for profile photo (e.g. /uploads/profiles/…). */
    private String profilePhotoUrl;

    /** Average of published client reviews; null until first published review. */
    @Column(precision = 4, scale = 2)
    private BigDecimal ratingAvg;

    @Builder.Default
    private Integer ratingCount = 0;
}
