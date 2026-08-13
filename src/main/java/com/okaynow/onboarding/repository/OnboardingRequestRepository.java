package com.okaynow.onboarding.repository;

import com.okaynow.onboarding.domain.OnboardingFieldType;
import com.okaynow.onboarding.domain.OnboardingRequest;
import com.okaynow.onboarding.domain.OnboardingRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingRequestRepository extends JpaRepository<OnboardingRequest, UUID> {

    List<OnboardingRequest> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<OnboardingRequest> findByUserIdAndStatusInOrderByCreatedAtAsc(
            UUID userId, Collection<OnboardingRequestStatus> statuses);

    Optional<OnboardingRequest> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndFieldTypeAndStatusIn(
            UUID userId, OnboardingFieldType fieldType, Collection<OnboardingRequestStatus> statuses);

    long countByUserIdAndStatus(UUID userId, OnboardingRequestStatus status);
}
