package com.okaynow.agencies.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.dto.AgencyDirectoryEntryResponse;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.common.geo.GeoUtils;
import com.okaynow.users.domain.Qualification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgencyDirectoryService {

    private static final int SNIPPET_MAX = 160;

    private final AgencyRepository agencyRepository;

    @Transactional(readOnly = true)
    public List<AgencyDirectoryEntryResponse> search(
            Double lat, Double lng, Integer radiusMiles, Qualification qualification) {
        List<Agency> listed = agencyRepository.findDirectoryListed();
        List<AgencyDirectoryEntryResponse> results = new ArrayList<>();
        for (Agency agency : listed) {
            if (qualification != null
                    && !agency.getQualificationsSupported().contains(qualification)) {
                continue;
            }
            Double distance = null;
            if (lat != null && lng != null && agency.getLat() != null && agency.getLng() != null) {
                distance = GeoUtils.distanceMiles(lat, lng, agency.getLat(), agency.getLng());
                int effectiveRadius = radiusMiles != null && radiusMiles > 0
                        ? radiusMiles
                        : agency.getServiceRadiusMiles() != null ? agency.getServiceRadiusMiles() : 50;
                if (distance > effectiveRadius) {
                    continue;
                }
            }
            results.add(new AgencyDirectoryEntryResponse(
                    agency.getId(),
                    agency.getSlug(),
                    agency.getDisplayName(),
                    agency.getCity(),
                    agency.getState(),
                    agency.getLat(),
                    agency.getLng(),
                    distance,
                    agency.getSubscriptionPlan(),
                    new ArrayList<>(agency.getQualificationsSupported()),
                    snippet(agency.getPublicDescription())));
        }
        results.sort(Comparator
                .comparing((AgencyDirectoryEntryResponse e) ->
                        e.subscriptionPlan() != null ? e.subscriptionPlan().ordinal() : 0)
                .reversed()
                .thenComparing(e -> e.distanceMiles() != null ? e.distanceMiles() : Double.MAX_VALUE)
                .thenComparing(AgencyDirectoryEntryResponse::displayName, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    private static String snippet(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() <= SNIPPET_MAX) {
            return trimmed;
        }
        return trimmed.substring(0, SNIPPET_MAX - 1).trim() + "…";
    }
}
