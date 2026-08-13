package com.okaynow.common.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okaynow.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

/**
 * Server-side US address geocoding via the Census Bureau geocoder (no API key).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeocodingService {

    private final ObjectMapper objectMapper;

    public record LatLng(double lat, double lng) {
    }

    public LatLng requireGeocode(String addressLine, String city, String state, String zip) {
        return geocode(addressLine, city, state, zip)
                .orElseThrow(() -> new BadRequestException(
                        "We could not locate that address. Check the street, city, and ZIP, then try again."));
    }

    public Optional<LatLng> geocode(String addressLine, String city, String state, String zip) {
        if (isBlank(addressLine) || isBlank(city) || isBlank(zip)) {
            return Optional.empty();
        }
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString("https://geocoding.geo.census.gov/geocoder/locations/address")
                    .queryParam("street", addressLine.trim())
                    .queryParam("city", city.trim())
                    .queryParam("state", state == null || state.isBlank() ? "MA" : state.trim())
                    .queryParam("zip", zip.trim().replaceAll("[^0-9-]", ""))
                    .queryParam("benchmark", "Public_AR_Current")
                    .queryParam("format", "json")
                    .build()
                    .encode()
                    .toUri();
            String body = RestClient.create()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode matches = objectMapper.readTree(body)
                    .path("result")
                    .path("addressMatches");
            if (!matches.isArray() || matches.isEmpty()) {
                return Optional.empty();
            }
            JsonNode coords = matches.get(0).path("coordinates");
            if (!coords.has("y") || !coords.has("x")) {
                return Optional.empty();
            }
            double lat = coords.get("y").asDouble();
            double lng = coords.get("x").asDouble();
            return Optional.of(new LatLng(lat, lng));
        } catch (Exception ex) {
            log.warn("Geocode failed for {} {}, {}: {}", addressLine, city, zip, ex.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
