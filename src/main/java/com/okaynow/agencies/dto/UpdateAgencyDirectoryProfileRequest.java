package com.okaynow.agencies.dto;

import com.okaynow.users.domain.Qualification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateAgencyDirectoryProfileRequest(
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 200) String legalName,
        @Size(max = 80) String licenseNumber,
        @Size(max = 200) String addressLine,
        @Size(max = 80) String city,
        @Size(max = 2) String state,
        @Size(max = 10) String zip,
        Integer serviceRadiusMiles,
        @Size(max = 4000) String publicDescription,
        List<Qualification> qualificationsSupported,
        Boolean directoryListed,
        Boolean hiringOpen,
        @Size(max = 1000) String hiringNote
) {
}
