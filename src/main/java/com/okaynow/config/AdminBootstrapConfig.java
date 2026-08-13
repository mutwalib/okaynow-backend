package com.okaynow.config;

import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional platform-owner bootstrap. Public registration deliberately cannot
 * create ADMIN users; set both properties (or env vars) for local/dev startup.
 */
@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapConfig implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.email:}")
    private String email;

    @Value("${app.bootstrap-admin.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (password == null || password.length() < 8) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_PASSWORD must contain at least 8 characters");
        }

        String normalizedEmail = email.trim().toLowerCase();
        String hash = passwordEncoder.encode(password);

        // If the configured owner email is new, rename a prior bootstrap admin account.
        if (userRepository.findByEmail(normalizedEmail).isEmpty()) {
            for (String legacy : new String[]{"admin@okaynowcare.com", "admin@okaynow.com"}) {
                if (legacy.equals(normalizedEmail)) {
                    continue;
                }
                var legacyAdmin = userRepository.findByEmail(legacy);
                if (legacyAdmin.isPresent() && legacyAdmin.get().getRole() == Role.ADMIN) {
                    User renamed = legacyAdmin.get();
                    renamed.setEmail(normalizedEmail);
                    renamed.setPasswordHash(hash);
                    renamed.setStatus(UserStatus.ACTIVE);
                    renamed.setEmailVerified(true);
                    if (renamed.getEmailVerifiedAt() == null) {
                        renamed.setEmailVerifiedAt(java.time.Instant.now());
                    }
                    userRepository.save(renamed);
                    log.warn(
                            "Renamed platform owner {} → {}. Remove bootstrap credentials from the environment in production.",
                            legacy,
                            normalizedEmail);
                    return;
                }
            }
        }

        userRepository.findByEmail(normalizedEmail).ifPresentOrElse(existing -> {
            existing.setPasswordHash(hash);
            existing.setRole(Role.ADMIN);
            existing.setStatus(UserStatus.ACTIVE);
            existing.setEmailVerified(true);
            if (existing.getEmailVerifiedAt() == null) {
                existing.setEmailVerifiedAt(java.time.Instant.now());
            }
            userRepository.save(existing);
            log.warn("Reset platform owner credentials for {}. Remove bootstrap credentials from the environment in production.",
                    normalizedEmail);
        }, () -> {
            userRepository.save(User.builder()
                    .email(normalizedEmail)
                    .passwordHash(hash)
                    .role(Role.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .emailVerifiedAt(java.time.Instant.now())
                    .build());
            log.warn("Bootstrapped platform owner {}. Remove bootstrap credentials from the environment in production.",
                    normalizedEmail);
        });
    }
}
