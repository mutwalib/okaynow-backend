package com.okaynow.reports.dto;

public record GeneratedReport(
        String filename,
        String contentType,
        byte[] bytes
) {
}
