package com.okaynow.legal.dto;

import com.okaynow.legal.domain.LegalDocumentType;

import java.time.Instant;
import java.util.UUID;

public record LegalDocumentResponse(
        UUID id,
        LegalDocumentType documentType,
        int version,
        String title,
        String body,
        boolean published,
        Instant publishedAt,
        Instant createdAt
) {
}
