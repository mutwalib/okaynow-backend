package com.okaynow.admin.dto;

import java.util.List;

/**
 * Single-screen attention feed for agency schedulers — not a KPI vanity dashboard.
 */
public record OpsAttentionResponse(
        int openUnfilledShifts,
        int openWithoutKnownCaregiver,
        int credentialsExpiringSoon,
        int sentUnpaidInvoices,
        int evvExceptions,
        List<OpsAttentionItem> items
) {
    public record OpsAttentionItem(
            String key,
            String title,
            String detail,
            String href,
            int count,
            String severity
    ) {
    }
}
