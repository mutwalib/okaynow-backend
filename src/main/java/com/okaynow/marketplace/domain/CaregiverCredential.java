package com.okaynow.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Minimal credential vault entry for marketplace gating (upload/UI can follow).
 */
@Entity
@Table(name = "caregiver_credentials", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_caregiver_credentials_type",
                columnNames = {"caregiver_profile_id", "credential_type"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaregiverCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caregiver_profile_id", nullable = false)
    private UUID caregiverProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CredentialType credentialType;

    @Column(length = 128)
    private String licenseNumber;

    private LocalDate issueDate;

    private LocalDate expiryDate;

    @Column(length = 1000)
    private String documentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private CredentialVerificationStatus verificationStatus = CredentialVerificationStatus.PENDING;

    /** Primary-source check result (Nursys / board), when run. */
    @Column(length = 32)
    private String primarySourceStatus;

    private Instant primarySourceCheckedAt;

    @Column(length = 500)
    private String primarySourceNotes;

    private UUID reviewedBy;

    private Instant reviewedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
