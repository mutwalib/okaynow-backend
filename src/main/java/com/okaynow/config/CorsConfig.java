package com.okaynow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        api.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        api.setAllowedHeaders(List.of("*"));
        api.setAllowCredentials(true);
        source.registerCorsConfiguration("/api/**", api);
        source.registerCorsConfiguration("/uploads/**", api);
        source.registerCorsConfiguration("/v3/api-docs/**", api);
        source.registerCorsConfiguration("/swagger-ui/**", api);

        // WebSocket handshake is checked by CorsFilter before STOMP auth.
        // React Native often sends localhost / null / missing Origin — allow all here.
        CorsConfiguration ws = new CorsConfiguration();
        ws.setAllowedOriginPatterns(List.of("*"));
        ws.setAllowedMethods(List.of("GET", "OPTIONS"));
        ws.setAllowedHeaders(List.of("*"));
        ws.setAllowCredentials(false);
        source.registerCorsConfiguration("/ws", ws);
        source.registerCorsConfiguration("/ws/**", ws);

        return source;
    }
}
