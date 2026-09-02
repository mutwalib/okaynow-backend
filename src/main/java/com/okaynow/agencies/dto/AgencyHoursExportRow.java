package com.okaynow.agencies.dto;

public record AgencyHoursExportRow(
        String shiftDate,
        String caregiverName,
        String caregiverEmail,
        String clientName,
        String qualification,
        String hours,
        String payRate,
        String caregiverAmount,
        String billRate,
        String clientAmount,
        String shiftId
) {
}
