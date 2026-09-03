package com.okaynow.agencies.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.okaynow.shifts.dto.ShiftResponse;

import java.util.List;

public record AgencyShiftCardResponse(
        @JsonUnwrapped ShiftResponse shift,
        List<AgencyShiftAssignmentResponse> assignments
) {
}
