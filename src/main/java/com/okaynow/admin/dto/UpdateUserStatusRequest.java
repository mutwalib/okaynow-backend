package com.okaynow.admin.dto;

import com.okaynow.users.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
