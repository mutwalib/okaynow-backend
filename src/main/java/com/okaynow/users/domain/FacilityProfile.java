package com.okaynow.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "facility_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Registered facility name — set at registration and not self-editable afterward. */
    @Column(nullable = false)
    private String facilityName;

    @Column(nullable = false)
    private String contactFirstName;

    @Column(nullable = false)
    private String contactLastName;

    @Column(nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    @Builder.Default
    private String state = "MA";

    @Column(nullable = false)
    private String zip;

    private Double lat;

    private Double lng;

    @Column(length = 2000)
    private String notes;
}
