package com.okaynow.legal.dto;

import com.okaynow.legal.domain.LegalDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertLegalDocumentRequest(
        @NotNull LegalDocumentType documentType,
        @NotBlank String title,
        @NotBlank String body,
        /** When true, publishes a new version immediately. */
        boolean publish
) {
}
