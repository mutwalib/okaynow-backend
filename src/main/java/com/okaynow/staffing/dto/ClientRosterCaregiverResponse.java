package com.okaynow.staffing.dto;

import com.okaynow.staffing.domain.AssignmentType;
import com.okaynow.users.domain.Qualification;

import java.util.Set;
import java.util.UUID;

/** Client-facing roster row (no email / internal notes). */
public record ClientRosterCaregiverResponse(
        UUID assignmentId,
        UUID caregiverProfileId,
        String firstName,
        String lastName,
        Set<Qualification> qualifications,
        AssignmentType assignmentType
) {
}
