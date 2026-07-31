package com.okaynow.staffing.dto;

import com.okaynow.staffing.domain.AssignmentType;
import com.okaynow.users.domain.Qualification;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ClientCaregiverAssignmentResponse(
        UUID id,
        UUID clientProfileId,
        UUID caregiverProfileId,
        String caregiverFirstName,
        String caregiverLastName,
        String caregiverEmail,
        Set<Qualification> qualifications,
        Integer serviceRadiusMiles,
        AssignmentType assignmentType,
        boolean active,
        String notes,
        Instant createdAt
) {
}
