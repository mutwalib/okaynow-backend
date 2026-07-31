package com.okaynow.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 100) String password,
        String phone
) {
}
