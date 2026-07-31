package com.okaynow.legal.dto;

import java.util.List;

public record LegalAcceptanceStatusResponse(
        boolean upToDate,
        List<LegalDocumentResponse> pending
) {
}
