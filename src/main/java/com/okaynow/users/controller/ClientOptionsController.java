package com.okaynow.users.controller;

import com.okaynow.users.dto.ClientOptionResponse;
import com.okaynow.users.repository.ClientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/client-options")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ClientOptionsController {

    private final ClientProfileRepository clientProfileRepository;

    @GetMapping
    public ResponseEntity<List<ClientOptionResponse>> list() {
        return ResponseEntity.ok(clientProfileRepository
                .findAll(Sort.by("lastName", "firstName"))
                .stream()
                .map(client -> new ClientOptionResponse(
                        client.getId(), client.getFirstName(), client.getLastName()))
                .toList());
    }
}
