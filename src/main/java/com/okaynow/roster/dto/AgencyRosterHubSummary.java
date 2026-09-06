package com.okaynow.roster.dto;

import java.math.BigDecimal;
import java.util.List;

public record AgencyRosterHubSummary(
        int upcomingShifts,
        int inProgressShifts,
        int completedShifts,
        int unfulfilledShifts,
        int pendingPayCount,
        BigDecimal pendingPayAmount
) {
}
