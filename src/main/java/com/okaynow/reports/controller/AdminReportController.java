package com.okaynow.reports.controller;

import com.okaynow.reports.domain.ReportFormat;
import com.okaynow.reports.domain.ReportType;
import com.okaynow.reports.dto.GeneratedReport;
import com.okaynow.reports.service.AdminReportService;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final UserService userService;

    @GetMapping("/{type}")
    public ResponseEntity<byte[]> download(
            @PathVariable ReportType type,
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam Map<String, String> allParams,
            Authentication authentication) throws Exception {
        Map<String, String> filters = new LinkedHashMap<>(allParams);
        filters.remove("format");

        GeneratedReport report = adminReportService.generate(
                type,
                ReportFormat.from(format),
                userService.getByEmail(authentication.getName()),
                filters);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.filename() + "\"")
                .contentType(MediaType.parseMediaType(report.contentType()))
                .body(report.bytes());
    }
}
