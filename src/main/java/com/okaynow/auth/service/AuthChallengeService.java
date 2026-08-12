package com.okaynow.auth.service;

import com.okaynow.auth.domain.AuthChallenge;
import com.okaynow.auth.domain.AuthChallengePurpose;
import com.okaynow.auth.repository.AuthChallengeRepository;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.mail.EmailSender;
import com.okaynow.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthChallengeService {

    private final AuthChallengeRepository challengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.otp-ttl-seconds:600}")
    private long otpTtlSeconds;

    @Value("${app.auth.otp-length:6}")
    private int otpLength;

    @Value("${app.auth.max-attempts:5}")
    private int maxAttempts;

    @Transactional
    public void issue(User user, AuthChallengePurpose purpose) {
        challengeRepository.consumeOpenChallenges(user.getId(), purpose, Instant.now());

        String code = generateCode();
        AuthChallenge challenge = AuthChallenge.builder()
                .userId(user.getId())
                .email(user.getEmail().toLowerCase(Locale.ROOT))
                .purpose(purpose)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(Instant.now().plusSeconds(otpTtlSeconds))
                .build();
        challengeRepository.save(challenge);

        emailSender.send(user.getEmail(), subjectFor(purpose), bodyFor(purpose, code));
    }

    @Transactional
    public void verifyOrThrow(User user, AuthChallengePurpose purpose, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BadRequestException("Verification code is required");
        }
        String email = user.getEmail().toLowerCase(Locale.ROOT);
        AuthChallenge challenge = challengeRepository
                .findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new BadRequestException("No active verification code. Request a new one."));

        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            challenge.setConsumedAt(Instant.now());
            challengeRepository.save(challenge);
            throw new BadRequestException("Verification code expired. Request a new one.");
        }
        if (challenge.getAttemptCount() >= maxAttempts) {
            challenge.setConsumedAt(Instant.now());
            challengeRepository.save(challenge);
            throw new BadRequestException("Too many incorrect attempts. Request a new code.");
        }
        if (!passwordEncoder.matches(rawCode.trim(), challenge.getCodeHash())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            challengeRepository.save(challenge);
            throw new BadRequestException("Invalid verification code");
        }

        challenge.setConsumedAt(Instant.now());
        challengeRepository.save(challenge);
        challengeRepository.consumeOpenChallenges(user.getId(), purpose, Instant.now());
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, otpLength);
        int n = secureRandom.nextInt(bound);
        return String.format("%0" + otpLength + "d", n);
    }

    private static String subjectFor(AuthChallengePurpose purpose) {
        return switch (purpose) {
            case SIGNUP_VERIFY -> "Verify your OkayNow email";
            case LOGIN_OTP -> "Your OkayNow sign-in code";
            case PASSWORD_RESET -> "Reset your OkayNow password";
        };
    }

    private String bodyFor(AuthChallengePurpose purpose, String code) {
        int minutes = (int) Math.max(1, otpTtlSeconds / 60);
        return switch (purpose) {
            case SIGNUP_VERIFY -> """
                    Welcome to OkayNow.

                    Your email verification code is: %s

                    It expires in %d minutes. If you did not create an account, you can ignore this email.
                    """.formatted(code, minutes);
            case LOGIN_OTP -> """
                    Your OkayNow agency console sign-in code is: %s

                    It expires in %d minutes. If you did not try to sign in, reset your password and contact support.
                    """.formatted(code, minutes);
            case PASSWORD_RESET -> """
                    Your OkayNow password reset code is: %s

                    It expires in %d minutes. If you did not request a reset, you can ignore this email.
                    """.formatted(code, minutes);
        };
    }
}
