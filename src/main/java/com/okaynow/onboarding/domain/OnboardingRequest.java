package com.okaynow.onboarding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import java.util.UUID;

@Entity
@Table(name = "onboarding_requests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    /** Null when created by the system (e.g. caregiver profile photo). */
    private UUID requestedByUserId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OnboardingFieldType fieldType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private OnboardingRequestStatus status = OnboardingRequestStatus.OPEN;

    @Column(length = 4000)
    private String responseText;

    @Column(length = 512)
    private String fileUrl;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant submittedAt;

    private Instant resolvedAt;
}
