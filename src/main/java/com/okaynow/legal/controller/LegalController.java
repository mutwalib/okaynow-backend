package com.okaynow.legal.controller;

import com.okaynow.legal.domain.LegalDocumentType;
import com.okaynow.legal.dto.AcceptLegalDocumentsRequest;
import com.okaynow.legal.dto.LegalAcceptanceStatusResponse;
import com.okaynow.legal.dto.LegalDocumentResponse;
import com.okaynow.legal.dto.UpsertLegalDocumentRequest;
import com.okaynow.legal.service.LegalDocumentService;
import com.okaynow.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
public class LegalController {

    private final LegalDocumentService legalDocumentService;
    private final UserService userService;

    @GetMapping("/current")
    public ResponseEntity<List<LegalDocumentResponse>> currentAll() {
        return ResponseEntity.ok(legalDocumentService.listCurrent());
    }

    @GetMapping("/current/{type}")
    public ResponseEntity<LegalDocumentResponse> current(@PathVariable LegalDocumentType type) {
        return ResponseEntity.ok(legalDocumentService.current(type));
    }

    @GetMapping("/me/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LegalAcceptanceStatusResponse> myStatus(Authentication authentication) {
        return ResponseEntity.ok(legalDocumentService.acceptanceStatus(
                userService.getByEmail(authentication.getName())));
    }

    @PostMapping("/me/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LegalAcceptanceStatusResponse> accept(
            @Valid @RequestBody AcceptLegalDocumentsRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(legalDocumentService.accept(
                request, userService.getByEmail(authentication.getName())));
    }

    @GetMapping("/admin/{type}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LegalDocumentResponse>> history(@PathVariable LegalDocumentType type) {
        return ResponseEntity.ok(legalDocumentService.history(type));
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LegalDocumentResponse> upsert(
            @Valid @RequestBody UpsertLegalDocumentRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                legalDocumentService.upsert(
                        request, userService.getByEmail(authentication.getName())));
    }
}
