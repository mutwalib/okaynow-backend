package com.okaynow.common.geo;

import com.okaynow.common.exception.BadRequestException;
import com.okaynow.config.ServiceRegionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Enforces OkayNow's service footprint (Massachusetts-only for launch).
 * Expand {@link ServiceRegionProperties} when adding states.
 */
@Service
@RequiredArgsConstructor
public class ServiceRegionService {

    private final ServiceRegionProperties properties;

    public List<String> allowedStates() {
        return properties.allowedStateCodes();
    }

    public String defaultState() {
        List<String> allowed = allowedStates();
        return allowed.isEmpty() ? "MA" : allowed.getFirst();
    }

    /** Normalize + validate state/ZIP; returns normalized uppercase state and ZIP digits. */
    public NormalizedAddress validate(String state, String zip) {
        String normalizedState = normalizeState(state);
        if (!allowedStates().contains(normalizedState)) {
            throw new BadRequestException(
                    "OkayNow currently operates in "
                            + formatAllowedStates()
                            + " only. Addresses outside this region are not accepted yet.");
        }
        String normalizedZip = normalizeZip(zip);
        validateZipForState(normalizedState, normalizedZip);
        return new NormalizedAddress(normalizedState, normalizedZip);
    }

    public String normalizeState(String state) {
        if (state == null || state.isBlank()) {
            return defaultState();
        }
        String normalized = state.trim().toUpperCase(Locale.US);
        if (normalized.length() != 2) {
            throw new BadRequestException("State must be a 2-letter USPS code (e.g. MA)");
        }
        return normalized;
    }

    public String normalizeZip(String zip) {
        if (zip == null || zip.isBlank()) {
            throw new BadRequestException("ZIP code is required");
        }
        String digits = zip.trim().replaceAll("[^0-9]", "");
        if (digits.length() < 5) {
            throw new BadRequestException("Enter a valid 5-digit ZIP code");
        }
        String five = digits.substring(0, 5);
        if (digits.length() >= 9) {
            return five + "-" + digits.substring(5, 9);
        }
        return five;
    }

    private void validateZipForState(String state, String zip) {
        List<String> prefixes = properties.getZipPrefixes() == null
                ? List.of()
                : properties.getZipPrefixes().getOrDefault(state, List.of());
        if (prefixes == null || prefixes.isEmpty()) {
            return;
        }
        String five = zip.length() >= 5 ? zip.substring(0, 5) : zip;
        String zip3 = five.substring(0, 3);
        boolean ok = prefixes.stream()
                .map(ServiceRegionService::normalizeZip3Prefix)
                .anyMatch(p -> p.equals(zip3));
        if (!ok) {
            throw new BadRequestException(
                    "That ZIP code is not in " + state
                            + ". OkayNow currently accepts "
                            + state
                            + " service addresses only.");
        }
    }

    /** YAML may bind 010 as integer 10 — pad back to ZIP3. */
    private static String normalizeZip3Prefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String digits = prefix.trim().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return "";
        }
        if (digits.length() >= 3) {
            return digits.substring(0, 3);
        }
        return String.format("%03d", Integer.parseInt(digits));
    }

    private String formatAllowedStates() {
        return allowedStates().stream().collect(Collectors.joining(", "));
    }

    public record NormalizedAddress(String state, String zip) {
    }
}
