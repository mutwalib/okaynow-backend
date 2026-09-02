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
            Double lat,
            Double lng,
            Integer radiusMiles,
            Qualification qualification,
            String city,
            String zip,
            Boolean hiringOnly) {
        List<Agency> listed = agencyRepository.findDirectoryListed();
        List<AgencyDirectoryEntryResponse> results = new ArrayList<>();
        String cityFilter = city == null ? null : city.trim().toLowerCase();
        String zipFilter = zip == null ? null : zip.trim().replaceAll("[^0-9]", "");
        for (Agency agency : listed) {
            if (Boolean.TRUE.equals(hiringOnly) && !agency.isHiringOpen()) {
                continue;
            }
            if (qualification != null
                    && !agency.getQualificationsSupported().contains(qualification)) {
                continue;
            }
            if (cityFilter != null && !cityFilter.isEmpty()) {
                String agencyCity = agency.getCity() == null ? "" : agency.getCity().toLowerCase();
                if (!agencyCity.contains(cityFilter)) {
                    continue;
                }
            }
            if (zipFilter != null && !zipFilter.isEmpty()) {
                String agencyZip = agency.getZip() == null ? "" : agency.getZip().replaceAll("[^0-9]", "");
                if (agencyZip.isEmpty()
                        || !(agencyZip.startsWith(zipFilter) || zipFilter.startsWith(agencyZip))) {
                    continue;
                }
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
                    agency.getZip(),
                    agency.getLat(),
                    agency.getLng(),
                    distance,
                    agency.getSubscriptionPlan(),
                    new ArrayList<>(agency.getQualificationsSupported()),
                    snippet(agency.getPublicDescription()),
                    agency.isHiringOpen(),
                    agency.getHiringNote()));
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
