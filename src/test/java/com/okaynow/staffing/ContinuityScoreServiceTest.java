package com.okaynow.staffing;

import com.okaynow.staffing.domain.AssignmentType;
import com.okaynow.staffing.service.ContinuityScoreService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuityScoreServiceTest {

    @Test
    void primaryOutranksHistoryAlone() {
        int primary = ContinuityScoreService.score(AssignmentType.PRIMARY, 0, null);
        int history = ContinuityScoreService.score(null, 8, new BigDecimal("5.00"));
        assertTrue(primary > history);
    }

    @Test
    void rotationalBeatsColdStart() {
        int rotational = ContinuityScoreService.score(AssignmentType.ROTATIONAL, 0, null);
        int cold = ContinuityScoreService.score(null, 0, null);
        assertTrue(rotational > cold);
        assertEquals(0, cold);
    }

    @Test
    void labelsPreferRosterLanguage() {
        assertEquals("Primary caregiver",
                ContinuityScoreService.label(AssignmentType.PRIMARY, 0));
        assertEquals("Worked here before (3)",
                ContinuityScoreService.label(null, 3));
        assertEquals("New to this client",
                ContinuityScoreService.label(null, 0));
    }
}
