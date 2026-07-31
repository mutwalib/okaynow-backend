package com.okaynow.legal.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AcceptLegalDocumentsRequest(
        @NotEmpty List<UUID> documentIds
) {
}
