package com.okaynow.staffing.service;

import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.marketplace.service.MarketplaceEligibilityService;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.staffing.domain.AssignmentType;
import com.okaynow.staffing.domain.ClientCaregiverAssignment;
import com.okaynow.staffing.dto.ContinuityCaregiverSuggestion;
import com.okaynow.staffing.repository.ClientCaregiverAssignmentRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.repository.CaregiverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranks caregivers so home shifts prefer known people (PRIMARY → rotational → history)
 * instead of anonymous marketplace fill — OkayNow's differentiator vs gig nursing apps.
 */
@Service
@RequiredArgsConstructor
public class ContinuityScoreService {

    private static final int SCORE_PRIMARY = 1000;
    private static final int SCORE_ROTATIONAL = 500;
    private static final int SCORE_PER_COMPLETED = 25;
    private static final int SCORE_COMPLETED_CAP = 250;
    private static final int SCORE_RATING_SCALE = 20; // rating 5.0 → +100

    private final ShiftRepository shiftRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientCaregiverAssignmentRepository assignmentRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final MarketplaceEligibilityService marketplaceEligibilityService;

    @Transactional(readOnly = true)
    public List<ContinuityCaregiverSuggestion> suggestForShift(UUID shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        Map<UUID, AssignmentType> roster = new HashMap<>();
        Map<UUID, Long> completedByCaregiver = new HashMap<>();

        if (shift.getClientProfileId() != null) {
            for (ClientCaregiverAssignment a : assignmentRepository
                    .findByClientProfileIdAndActiveTrueOrderByCreatedAtAsc(shift.getClientProfileId())) {
                roster.put(a.getCaregiverProfile().getId(), a.getAssignmentType());
            }
            for (Object[] row : shiftClaimRepository.countCompletedByCaregiverForClient(
                    shift.getClientProfileId(), ShiftClaimStatus.COMPLETED)) {
                completedByCaregiver.put((UUID) row[0], (Long) row[1]);
            }
        } else if (shift.getFacilityProfileId() != null) {
            for (Object[] row : shiftClaimRepository.countCompletedByCaregiverForFacility(
                    shift.getFacilityProfileId(), ShiftClaimStatus.COMPLETED)) {
                completedByCaregiver.put((UUID) row[0], (Long) row[1]);
            }
        }

        List<ContinuityCaregiverSuggestion> out = new ArrayList<>();
        for (CaregiverProfile cg : caregiverProfileRepository.findAllWithUser()) {
            boolean eligible;
            try {
                marketplaceEligibilityService.assertCanAssign(cg, shift);
                eligible = true;
            } catch (RuntimeException ex) {
                eligible = false;
            }

            AssignmentType rosterType = roster.get(cg.getId());
            long completed = completedByCaregiver.getOrDefault(cg.getId(), 0L);
            int score = score(rosterType, completed, cg.getRatingAvg());
            // Skip cold strangers with zero continuity unless eligible (still list eligible for fill)
            if (!eligible && score == 0) {
                continue;
            }
            if (!eligible && rosterType == null && completed == 0) {
                continue;
            }

            out.add(new ContinuityCaregiverSuggestion(
                    cg.getId(),
                    cg.getFirstName(),
                    cg.getLastName(),
                    cg.getUser().getEmail(),
                    cg.getQualifications(),
                    score,
                    label(rosterType, completed),
                    rosterType,
                    completed,
                    cg.getRatingAvg(),
                    eligible));
        }

        out.sort(Comparator
                .comparing(ContinuityCaregiverSuggestion::eligible).reversed()
                .thenComparing(ContinuityCaregiverSuggestion::continuityScore).reversed()
                .thenComparing(ContinuityCaregiverSuggestion::lastName)
                .thenComparing(ContinuityCaregiverSuggestion::firstName));
        return out;
    }

    public static int score(AssignmentType rosterType, long completed, BigDecimal ratingAvg) {
        int s = 0;
        if (rosterType == AssignmentType.PRIMARY) {
            s += SCORE_PRIMARY;
        } else if (rosterType == AssignmentType.ROTATIONAL) {
            s += SCORE_ROTATIONAL;
        }
        s += (int) Math.min(SCORE_COMPLETED_CAP, completed * SCORE_PER_COMPLETED);
        if (ratingAvg != null) {
            s += ratingAvg.multiply(BigDecimal.valueOf(SCORE_RATING_SCALE)).intValue();
        }
        return s;
    }

    public static String label(AssignmentType rosterType, long completed) {
        if (rosterType == AssignmentType.PRIMARY) {
            return "Primary caregiver";
        }
        if (rosterType == AssignmentType.ROTATIONAL) {
            return "On client roster";
        }
        if (completed > 0) {
            return "Worked here before (" + completed + ")";
        }
        return "New to this client";
    }
}
