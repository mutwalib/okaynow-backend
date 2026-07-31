package com.okaynow.reports.dto;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ReportMeta(
        String title,
        String generatedFor,
        Instant generatedAt,
        Map<String, String> filters
) {
    public static ReportMeta of(String title, String generatedFor, Map<String, String> filters) {
        Map<String, String> clean = new LinkedHashMap<>();
        if (filters != null) {
            filters.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    clean.put(k, v);
                }
            });
        }
        if (clean.isEmpty()) {
            clean.put("Filters", "None (all matching records)");
        }
        return new ReportMeta(title, generatedFor, Instant.now(), clean);
    }
}
