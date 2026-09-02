package com.okaynow.roster.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record InviteRosterCaregiverRequest(
        @Email String email,
        @Size(max = 500) String message
) {
}
