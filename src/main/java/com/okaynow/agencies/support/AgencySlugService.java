package com.okaynow.agencies.support;

import com.okaynow.agencies.repository.AgencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgencySlugService {

    private final AgencyRepository agencyRepository;

    public String generateUniqueSlug(String displayName) {
        String base = slugify(displayName);
        if (base.isBlank()) {
            base = "agency";
        }
        String candidate = base;
        int suffix = 2;
        while (agencyRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s_-]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
