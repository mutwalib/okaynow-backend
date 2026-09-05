package com.okaynow.shifts.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.dto.ShiftResponses;
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

    public ShiftResponse label(Shift shift, ShiftResponse raw) {
        if (raw == null || shift == null || shift.getAgencyId() == null) {
            return raw;
        }
        String name = agencyRepository.findById(shift.getAgencyId())
                .map(ShiftAgencyLabelService::displayName)
                .orElse("Agency");
        return ShiftResponses.withAgency(raw, shift.getAgencyId(), name);
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
        if (raw == null || shift == null || shift.getAgencyId() == null) {
            return raw;
        }
        String name = names != null && names.containsKey(shift.getAgencyId())
                ? names.get(shift.getAgencyId())
                : "Agency";
        return ShiftResponses.withAgency(raw, shift.getAgencyId(), name);
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
