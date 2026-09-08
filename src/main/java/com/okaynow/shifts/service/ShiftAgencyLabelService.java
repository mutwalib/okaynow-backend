package com.okaynow.shifts.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.dto.ShiftResponses;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShiftAgencyLabelService {

    private final AgencyRepository agencyRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;

    public ShiftResponse label(Shift shift, ShiftResponse raw) {
        if (raw == null || shift == null) {
            return raw;
        }
        ShiftResponse labeled = raw;
        if (shift.getAgencyId() != null) {
            String name = agencyRepository.findById(shift.getAgencyId())
                    .map(ShiftAgencyLabelService::displayName)
                    .orElse("Agency");
            labeled = ShiftResponses.withAgency(labeled, shift.getAgencyId(), name);
        }
        return withSite(shift, labeled);
    }

    public Map<UUID, String> namesFor(Collection<UUID> agencyIds) {
        if (agencyIds == null || agencyIds.isEmpty()) {
            return Map.of();
        }
        var distinct = agencyIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new HashMap<>();
        for (Agency agency : agencyRepository.findAllById(distinct)) {
            out.put(agency.getId(), displayName(agency));
        }
        return out;
    }

    public ShiftResponse label(Shift shift, ShiftResponse raw, Map<UUID, String> names) {
        if (raw == null || shift == null) {
            return raw;
        }
        ShiftResponse labeled = raw;
        if (shift.getAgencyId() != null) {
            String name = names != null && names.containsKey(shift.getAgencyId())
                    ? names.get(shift.getAgencyId())
                    : "Agency";
            labeled = ShiftResponses.withAgency(labeled, shift.getAgencyId(), name);
        }
        return withSite(shift, labeled);
    }

    private ShiftResponse withSite(Shift shift, ShiftResponse raw) {
        String site = resolveSiteDisplayName(shift);
        if (site == null) {
            return raw;
        }
        return ShiftResponses.withSiteDisplayName(raw, site);
    }

    private String resolveSiteDisplayName(Shift shift) {
        if (shift.getFacilityProfileId() != null) {
            return facilityProfileRepository.findById(shift.getFacilityProfileId())
                    .map(FacilityProfile::getFacilityName)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse("Facility");
        }
        if (shift.getClientProfileId() != null) {
            return clientProfileRepository.findById(shift.getClientProfileId())
                    .map(ShiftAgencyLabelService::clientDisplayName)
                    .orElse("Home");
        }
        return null;
    }

    private static String clientDisplayName(ClientProfile client) {
        String first = client.getFirstName() != null ? client.getFirstName().trim() : "";
        String last = client.getLastName() != null ? client.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "Home" : full;
    }

    private static String displayName(Agency agency) {
        if (agency.getDisplayName() != null && !agency.getDisplayName().isBlank()) {
            return agency.getDisplayName().trim();
        }
        if (agency.getLegalName() != null && !agency.getLegalName().isBlank()) {
            return agency.getLegalName().trim();
        }
        return "Agency";
    }
}
