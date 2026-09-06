package com.okaynow.roster.dto;

import java.time.LocalDate;
import java.util.List;

public record AgencyRosterHubResponse(
        AgencyRosterMemberDetailResponse member,
        LocalDate from,
        LocalDate to,
        AgencyRosterHubSummary summary,
        List<AgencyRosterScheduleItem> schedule,
        List<AgencyRosterTimesheetItem> timesheets
) {
}
