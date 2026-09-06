package com.okaynow.reports.controller;

import com.okaynow.reports.domain.ReportFormat;
import com.okaynow.reports.domain.ReportType;
import com.okaynow.reports.dto.GeneratedReport;
import com.okaynow.reports.service.AgencyReportService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/agencies/me/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENCY_ADMIN')")
public class AgencyReportController {

    private final AgencyReportService agencyReportService;
    private final UserService userService;

    @GetMapping("/{type}")
    public ResponseEntity<byte[]> download(
            @PathVariable ReportType type,
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam Map<String, String> allParams,
            Authentication authentication) throws Exception {
        Map<String, String> filters = new LinkedHashMap<>(allParams);
        filters.remove("format");
        var actor = userService.getByEmail(authentication.getName());
        GeneratedReport report = agencyReportService.generate(
                actor.getId(),
                type,
                ReportFormat.from(format),
                actor,
                filters);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.filename() + "\"")
                .contentType(MediaType.parseMediaType(report.contentType()))
                .body(report.bytes());
    }
}
