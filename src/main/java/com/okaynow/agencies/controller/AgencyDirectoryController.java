package com.okaynow.agencies.controller;

import com.okaynow.agencies.dto.AgencyDirectoryEntryResponse;
import com.okaynow.agencies.dto.AgencyPublicProfileResponse;
import com.okaynow.agencies.service.AgencyDirectoryService;
import com.okaynow.agencies.service.AgencyService;
import com.okaynow.users.domain.Qualification;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agencies")
@RequiredArgsConstructor
public class AgencyDirectoryController {

    private final AgencyDirectoryService agencyDirectoryService;
    private final AgencyService agencyService;

    @GetMapping("/directory")
    public ResponseEntity<List<AgencyDirectoryEntryResponse>> directory(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Qualification qualification,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String zip,
            @RequestParam(required = false) Boolean hiringOnly) {
        return ResponseEntity.ok(agencyDirectoryService.search(
                lat, lng, radius, qualification, city, zip, hiringOnly));
    }

    @GetMapping("/{slug}/public-profile")
    public ResponseEntity<AgencyPublicProfileResponse> publicProfile(@PathVariable String slug) {
        return ResponseEntity.ok(agencyService.getPublicProfile(slug));
    }
}
