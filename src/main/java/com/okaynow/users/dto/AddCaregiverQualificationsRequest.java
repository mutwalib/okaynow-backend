package com.okaynow.users.dto;

import com.okaynow.users.domain.Qualification;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AddCaregiverQualificationsRequest(
        @NotEmpty Set<Qualification> qualifications,
        /** Required when qualifications includes OTHER. */
        String otherQualificationDetail
) {
}
