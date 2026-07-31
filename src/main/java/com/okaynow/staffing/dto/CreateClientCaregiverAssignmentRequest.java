package com.okaynow.staffing.dto;

import com.okaynow.staffing.domain.AssignmentType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateClientCaregiverAssignmentRequest(
        @NotNull UUID caregiverProfileId,
        @NotNull AssignmentType assignmentType,
        String notes,
        /**
         * When true, assign this caregiver onto the client's existing open/draft/held
         * shifts that still need staff (does not create new shifts).
         */
        Boolean fillOpenShifts
) {
}
