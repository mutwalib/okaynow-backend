package com.okaynow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Operating footprint. Expand {@code allowed-states} (and ZIP rules) when launching
 * additional states — validation stays centralized in {@link com.okaynow.common.geo.ServiceRegionService}.
 */
@Component
@ConfigurationProperties(prefix = "app.service-region")
@Getter
@Setter
public class ServiceRegionProperties {

    /** Comma-separated two-letter USPS codes. Default: Massachusetts only. */
    private String allowedStates = "MA";

    /**
     * Optional ZIP3 prefixes per state for stricter postal validation.
     * MA uses 010–027. Omit a state to skip ZIP prefix checks for it.
     */
    private Map<String, List<String>> zipPrefixes = defaultMaPrefixes();

    public List<String> allowedStateCodes() {
        return Arrays.stream(allowedStates.split(","))
                .map(s -> s.trim().toUpperCase(Locale.US))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private static Map<String, List<String>> defaultMaPrefixes() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> ma = new ArrayList<>();
        for (int i = 10; i <= 27; i++) {
            ma.add(String.format("%03d", i));
        }
        map.put("MA", ma);
        return map;
    }
}
