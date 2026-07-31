package com.okaynow.users.domain;

import jakarta.persistence.Column;
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

import java.util.UUID;

@Entity
@Table(name = "client_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfile {

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

    private String addressLine;

    private String city;

    @Builder.Default
    private String state = "MA";

    private String zip;

    private Double lat;

    private Double lng;

    @Column(length = 2000)
    private String careNeeds;

    /** True when the account holder is the care recipient; false when registering for someone else. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean registeringForSelf = true;

    @Enumerated(EnumType.STRING)
    private MedicaidEligibility medicaidEligible;

    @Enumerated(EnumType.STRING)
    private CareRecipientRelationship relationshipToCareRecipient;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean canViewShifts = true;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean canCreateShifts = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean canUpdateShifts = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean canDeleteShifts = false;

    /** Public URL path for profile photo (e.g. /uploads/profiles/…). */
    private String profilePhotoUrl;
}
