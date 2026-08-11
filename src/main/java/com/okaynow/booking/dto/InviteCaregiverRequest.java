package com.okaynow.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Invite a specific caregiver to a private (or open) shift; they must accept. */
public record InviteCaregiverRequest(
        @NotNull UUID caregiverProfileId
) {
}
