package com.okaynow.reports.domain;

public enum ReportFormat {
    PDF,
    XLSX;

    public static ReportFormat from(String raw) {
        if (raw == null || raw.isBlank()) {
            return XLSX;
        }
        return ReportFormat.valueOf(raw.trim().toUpperCase());
    }

    public String contentType() {
        return this == PDF
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    public String extension() {
        return this == PDF ? "pdf" : "xlsx";
    }
}
