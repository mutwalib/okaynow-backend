package com.okaynow.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CorrectLegalNameRequest(
        @NotBlank String firstName,
        @NotBlank String lastName
) {
}
