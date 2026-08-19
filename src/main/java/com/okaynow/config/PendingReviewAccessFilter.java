package com.okaynow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Users in {@link UserStatus#PENDING_REVIEW} may only hit onboarding / self endpoints
 * until an admin approves their account.
 */
@Component
@RequiredArgsConstructor
public class PendingReviewAccessFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String email = auth.getName();
        if (email == null || "anonymousUser".equals(email)) {
            filterChain.doFilter(request, response);
            return;
        }
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || userOpt.get().getStatus() != UserStatus.PENDING_REVIEW) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (isAllowedWhilePendingReview(path, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "message",
                "Your account is pending agency review. Complete any requested information and wait for approval."));
    }

    static boolean isAllowedWhilePendingReview(String path, String method) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (path.startsWith("/api/onboarding/")) {
            return true;
        }
        if (path.startsWith("/api/legal/")) {
            return true;
        }
        if ("/api/users/me".equals(path)
                && ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return true;
        }
        // Allow finishing application details while waiting for agency review.
        if ("/api/caregivers/me".equals(path)
                && ("GET".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
            return true;
        }
        if ("/api/caregivers/me/qualifications".equals(path) && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        if ("/api/caregivers/me/photo".equals(path) && "POST".equalsIgnoreCase(method)) {
            return true;
        }
        if ("/api/clients/me".equals(path)
                && ("GET".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
            return true;
        }
        if (path.startsWith("/api/notifications/")) {
            return true;
        }
        return false;
    }
}
