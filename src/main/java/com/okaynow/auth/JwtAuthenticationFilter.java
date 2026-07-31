package com.okaynow.auth;

import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts a Bearer access token from the Authorization header and populates
 * the SecurityContext. Invalid/missing tokens simply fall through unauthenticated.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            Claims claims = tokenProvider.parseClaimsOrNull(token);
            if (claims != null && tokenProvider.isAccessToken(claims)) {
                String email = claims.get("email", String.class);
                String role = claims.get(JwtTokenProvider.ROLE_CLAIM, String.class);
                boolean active = userRepository.findByEmail(email)
                        .map(user -> user.getStatus() == UserStatus.ACTIVE
                                && user.getRole().name().equals(role))
                        .orElse(false);
                if (active) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
