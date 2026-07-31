package com.okaynow.reviews.service;

import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.reviews.domain.CaregiverReview;
import com.okaynow.reviews.domain.ReviewStatus;
import com.okaynow.reviews.dto.CreateReviewRequest;
import com.okaynow.reviews.dto.ModerateReviewRequest;
import com.okaynow.reviews.dto.ReviewResponse;
import com.okaynow.reviews.repository.CaregiverReviewRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final CaregiverReviewRepository reviewRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;

    @Transactional
    public ReviewResponse create(CreateReviewRequest request, User actor) {
        if (actor.getRole() != Role.CLIENT && actor.getRole() != Role.FACILITY) {
            throw new AccessDeniedException("Only family or facility clients can rate caregivers");
        }

        Shift shift = shiftRepository.findById(request.shiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getStatus() != ShiftStatus.COMPLETED) {
            throw new BadRequestException("You can only rate caregivers after a shift is completed");
        }
        if (reviewRepository.existsByShiftId(shift.getId())) {
            throw new BadRequestException("A review already exists for this shift");
        }

        UUID clientProfileId = null;
        UUID facilityProfileId = null;
        String reviewerLabel;
        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            if (!client.getId().equals(shift.getClientProfileId())) {
                throw new AccessDeniedException("You can only review caregivers on your own shifts");
            }
            clientProfileId = client.getId();
            reviewerLabel = displayFamilyName(client.getFirstName(), client.getLastName());
        } else {
            FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            if (!facility.getId().equals(shift.getFacilityProfileId())) {
                throw new AccessDeniedException("You can only review caregivers on your own facility shifts");
            }
            facilityProfileId = facility.getId();
            reviewerLabel = facility.getFacilityName();
        }

        ShiftClaim claim = shiftClaimRepository
                .findFirstByShiftIdAndStatusIn(shift.getId(), EnumSet.of(ShiftClaimStatus.COMPLETED))
                .or(() -> shiftClaimRepository.findFirstByShiftIdAndStatusIn(
                        shift.getId(),
                        EnumSet.of(ShiftClaimStatus.CONFIRMED, ShiftClaimStatus.COMPLETED)))
                .orElseThrow(() -> new BadRequestException("No completed caregiver claim found for this shift"));

        CaregiverProfile caregiver = claim.getCaregiverProfile();
        CaregiverReview review = reviewRepository.save(CaregiverReview.builder()
                .shiftId(shift.getId())
                .shiftClaimId(claim.getId())
                .caregiverProfileId(caregiver.getId())
                .reviewerUserId(actor.getId())
                .clientProfileId(clientProfileId)
                .facilityProfileId(facilityProfileId)
                .rating(request.rating())
                .comment(blankToNull(request.comment()))
                .status(ReviewStatus.PENDING)
                .build());

        return toResponse(review, caregiver, reviewerLabel);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> publishedForCaregiver(UUID caregiverProfileId) {
        return reviewRepository
                .findByCaregiverProfileIdAndStatusOrderByCreatedAtDesc(
                        caregiverProfileId, ReviewStatus.PUBLISHED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> myReviews(User actor) {
        return reviewRepository.findByReviewerUserIdOrderByCreatedAtDesc(actor.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewResponse forShift(UUID shiftId, User actor) {
        CaregiverReview review = reviewRepository.findByShiftId(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        if (actor.getRole() == Role.ADMIN
                || actor.getId().equals(review.getReviewerUserId())
                || isCaregiverOwner(actor, review.getCaregiverProfileId())) {
            return toResponse(review);
        }
        if (review.getStatus() == ReviewStatus.PUBLISHED) {
            return toResponse(review);
        }
        throw new AccessDeniedException("Review is not visible");
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> adminList(ReviewStatus status, Pageable pageable) {
        var page = status == null
                ? reviewRepository.findAllByOrderByCreatedAtDesc(pageable)
                : reviewRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional
    public ReviewResponse moderate(UUID reviewId, ModerateReviewRequest request, User admin) {
        if (request.status() != ReviewStatus.PUBLISHED && request.status() != ReviewStatus.HIDDEN) {
            throw new BadRequestException("Status must be PUBLISHED or HIDDEN");
        }
        CaregiverReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        review.setStatus(request.status());
        review.setModeratedAt(Instant.now());
        review.setModeratedBy(admin.getId());
        refreshCaregiverRating(review.getCaregiverProfileId());
        return toResponse(review);
    }

    private void refreshCaregiverRating(UUID caregiverProfileId) {
        CaregiverProfile caregiver = caregiverProfileRepository.findById(caregiverProfileId)
                .orElse(null);
        if (caregiver == null) {
            return;
        }
        Double avg = reviewRepository.averageRating(caregiverProfileId, ReviewStatus.PUBLISHED);
        long count = reviewRepository.countByCaregiverProfileIdAndStatus(
                caregiverProfileId, ReviewStatus.PUBLISHED);
        caregiver.setRatingAvg(avg == null
                ? null
                : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        caregiver.setRatingCount((int) count);
    }

    private boolean isCaregiverOwner(User actor, UUID caregiverProfileId) {
        if (actor.getRole() != Role.CAREGIVER) {
            return false;
        }
        return caregiverProfileRepository.findByUserId(actor.getId())
                .map(p -> p.getId().equals(caregiverProfileId))
                .orElse(false);
    }

    private ReviewResponse toResponse(CaregiverReview review) {
        CaregiverProfile caregiver = caregiverProfileRepository.findById(review.getCaregiverProfileId())
                .orElse(null);
        return toResponse(review, caregiver, resolveReviewerLabel(review));
    }

    private ReviewResponse toResponse(
            CaregiverReview review, CaregiverProfile caregiver, String reviewerLabel) {
        return new ReviewResponse(
                review.getId(),
                review.getShiftId(),
                review.getShiftClaimId(),
                review.getCaregiverProfileId(),
                caregiver == null ? null : caregiver.getFirstName(),
                caregiver == null ? null : caregiver.getLastName(),
                review.getReviewerUserId(),
                reviewerLabel,
                review.getClientProfileId(),
                review.getFacilityProfileId(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getModeratedAt());
    }

    private String resolveReviewerLabel(CaregiverReview review) {
        if (review.getFacilityProfileId() != null) {
            return facilityProfileRepository.findById(review.getFacilityProfileId())
                    .map(FacilityProfile::getFacilityName)
                    .orElse("Facility client");
        }
        if (review.getClientProfileId() != null) {
            return clientProfileRepository.findById(review.getClientProfileId())
                    .map(c -> displayFamilyName(c.getFirstName(), c.getLastName()))
                    .orElse("Family client");
        }
        return "Client";
    }

    private static String displayFamilyName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        if (last.isEmpty()) {
            return first.isEmpty() ? "Family client" : first;
        }
        return (first + " " + last.charAt(0) + ".").trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
