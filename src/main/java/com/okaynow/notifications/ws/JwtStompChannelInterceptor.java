package com.okaynow.notifications.ws;

import com.okaynow.auth.JwtTokenProvider;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authenticates STOMP CONNECT frames with the same JWT access tokens used by REST.
 * Principal name is the user email so {@code convertAndSendToUser(email, ...)} works.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token == null || token.isBlank()) {
                log.warn("STOMP CONNECT rejected: missing token");
                throw new IllegalArgumentException("Missing Authorization bearer token on STOMP CONNECT");
            }
            Claims claims = tokenProvider.parseClaimsOrNull(token);
            if (claims == null || !tokenProvider.isAccessToken(claims)) {
                log.warn(
                        "STOMP CONNECT rejected: invalid access token (len={}, reason={})",
                        token.length(),
                        tokenProvider.describeParseFailure(token));
                throw new IllegalArgumentException("Invalid access token for WebSocket");
            }
            String email = claims.get("email", String.class);
            String role = claims.get(JwtTokenProvider.ROLE_CLAIM, String.class);
            if (email == null || email.isBlank() || role == null || role.isBlank()) {
                log.warn("STOMP CONNECT rejected: token missing email/role claims");
                throw new IllegalArgumentException("Invalid access token for WebSocket");
            }
            String normalizedEmail = email.toLowerCase().trim();
            boolean active = userRepository.findByEmail(normalizedEmail)
                    .map(user -> user.getStatus() == UserStatus.ACTIVE
                            && user.getRole().name().equals(role))
                    .orElse(false);
            if (!active) {
                log.warn("STOMP CONNECT rejected: user {} not active for role {}",
                        normalizedEmail, role);
                throw new IllegalArgumentException("User is not active for WebSocket");
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            normalizedEmail, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            accessor.setUser(authentication);
            log.debug("STOMP CONNECT accepted for {}", normalizedEmail);
        }
        return message;
    }

    private static String extractToken(StompHeaderAccessor accessor) {
        String header = firstHeader(accessor, "Authorization");
        if (header == null) {
            header = firstHeader(accessor, "authorization");
        }
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        // React Native / some STOMP clients send the JWT as passcode.
        String passcode = firstHeader(accessor, "passcode");
        if (passcode != null && !passcode.isBlank()) {
            return passcode.trim();
        }
        String token = firstHeader(accessor, "token");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        return null;
    }

    private static String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
