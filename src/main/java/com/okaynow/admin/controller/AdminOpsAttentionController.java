package com.okaynow.admin.controller;

import com.okaynow.admin.dto.OpsAttentionResponse;
import com.okaynow.admin.service.OpsAttentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ops/attention")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOpsAttentionController {

    private final OpsAttentionService opsAttentionService;

    @GetMapping
    public ResponseEntity<OpsAttentionResponse> attention() {
        return ResponseEntity.ok(opsAttentionService.build());
    }
}
