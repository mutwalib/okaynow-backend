package com.okaynow.reviews.repository;

import com.okaynow.reviews.domain.CaregiverReview;
import com.okaynow.reviews.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverReviewRepository extends JpaRepository<CaregiverReview, UUID> {

    Optional<CaregiverReview> findByShiftId(UUID shiftId);

    boolean existsByShiftId(UUID shiftId);

    List<CaregiverReview> findByCaregiverProfileIdAndStatusOrderByCreatedAtDesc(
            UUID caregiverProfileId, ReviewStatus status);

    Page<CaregiverReview> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);

    Page<CaregiverReview> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<CaregiverReview> findByReviewerUserIdOrderByCreatedAtDesc(UUID reviewerUserId);

    @Query("""
            select avg(r.rating) from CaregiverReview r
            where r.caregiverProfileId = :caregiverId and r.status = :status
            """)
    Double averageRating(
            @Param("caregiverId") UUID caregiverId, @Param("status") ReviewStatus status);

    long countByCaregiverProfileIdAndStatus(UUID caregiverProfileId, ReviewStatus status);
}
