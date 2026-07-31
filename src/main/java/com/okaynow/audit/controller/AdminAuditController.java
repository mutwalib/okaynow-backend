package com.okaynow.audit.controller;

import com.okaynow.audit.dto.AuditLogResponse;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<PagedResponse<AuditLogResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditLogService.list(PageRequest.of(
                page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}
