package com.okaynow.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingReviewAccessFilterTest {

    @Test
    void allowsExistingCommitmentApis() {
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview("/api/claims/me", "GET"));
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/shifts/11111111-1111-1111-1111-111111111111", "GET"));
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/shifts/11111111-1111-1111-1111-111111111111/release", "POST"));
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/shifts/11111111-1111-1111-1111-111111111111/accept-invite", "POST"));
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/visits/by-shift/11111111-1111-1111-1111-111111111111/clock-in", "POST"));
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/visits/by-shift/11111111-1111-1111-1111-111111111111", "GET"));
    }

    @Test
    void blocksMarketplaceClaimAndOpenBoard() {
        assertFalse(PendingReviewAccessFilter.isAllowedWhilePendingReview("/api/shifts", "GET"));
        assertFalse(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/shifts/11111111-1111-1111-1111-111111111111/claim", "POST"));
        assertFalse(PendingReviewAccessFilter.isAllowedWhilePendingReview("/api/payroll/me/summary", "GET"));
    }

    @Test
    void allowsCaregiverRosterAndHiringWhilePending() {
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/caregivers/me/rosters", "GET"));
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/caregivers/me/agency-interests", "GET"));
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/caregivers/me/agency-interests", "POST"));
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/agencies/directory", "GET"));
        assertTrue(PendingReviewAccessFilter.isAllowedWhilePendingReview(
                "/api/caregivers/me/roster-invites", "GET"));
    }
}
