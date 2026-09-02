package com.okaynow.admin.dto;

import com.okaynow.agencies.domain.AgencyStaffRole;
import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;
import com.okaynow.onboarding.domain.OnboardingFieldType;
import com.okaynow.onboarding.domain.OnboardingRequestStatus;
import com.okaynow.users.domain.CareRecipientRelationship;
import com.okaynow.users.domain.MedicaidEligibility;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Full applicant dossier for agency KYC / account verification review.
 */
public record AdminUserReviewDetailResponse(
        UUID id,
        String email,
        String phone,
        Role role,
        UserStatus status,
        boolean emailVerified,
        Instant emailVerifiedAt,
        Instant createdAt,
        String displayName,
        boolean pendingReview,
        long openKycRequests,
        long submittedKycRequests,
        CaregiverReviewProfile caregiver,
        ClientReviewProfile client,
        AgencyStaffReviewProfile agencyStaff,
        List<CredentialSummary> credentials,
        List<KycRequestSummary> kycRequests
) {
    public record AgencyStaffReviewProfile(
            UUID agencyId,
            String agencySlug,
            String agencyDisplayName,
            AgencyStaffRole staffRole,
            SubscriptionStatus subscriptionStatus,
            SubscriptionPlan subscriptionPlan,
            boolean directoryListed,
            boolean hiringOpen
    ) {
    }
    public record CaregiverReviewProfile(
            UUID profileId,
            String firstName,
            String lastName,
            Set<Qualification> qualifications,
            String otherQualificationDetail,
            BigDecimal hourlyRateMin,
            BigDecimal hourlyRateMax,
            Integer serviceRadiusMiles,
            String homeAddressLine,
            String homeCity,
            String homeState,
            String homeZip,
            Double homeLat,
            Double homeLng,
            String profilePhotoUrl
    ) {
    }

    public record ClientReviewProfile(
            UUID profileId,
            String firstName,
            String lastName,
            String addressLine,
            String city,
            String state,
            String zip,
            String careNeeds,
            boolean registeringForSelf,
            MedicaidEligibility medicaidEligible,
            CareRecipientRelationship relationshipToCareRecipient
    ) {
    }

    public record CredentialSummary(
            UUID id,
            String credentialType,
            String licenseNumber,
            LocalDate issueDate,
            LocalDate expiryDate,
            String documentUrl,
            String verificationStatus
    ) {
    }

    public record KycRequestSummary(
            UUID id,
            String title,
            String instructions,
            OnboardingFieldType fieldType,
            OnboardingRequestStatus status,
            String responseText,
            String fileUrl,
            Instant createdAt,
            Instant submittedAt
    ) {
    }
}
