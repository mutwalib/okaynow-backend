package com.okaynow.users.dto;

import java.util.UUID;

public record ClientOptionResponse(
        UUID id,
        String firstName,
        String lastName
) {
}
