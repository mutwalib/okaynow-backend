package com.okaynow.auth;

import com.okaynow.auth.dto.AuthResponse;
import com.okaynow.auth.dto.LoginRequest;
import com.okaynow.auth.dto.RefreshRequest;
import com.okaynow.auth.dto.RegisterRequest;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.legal.dto.AcceptLegalDocumentsRequest;
import com.okaynow.legal.service.LegalDocumentService;
import com.okaynow.users.domain.CareRecipientRelationship;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.MedicaidEligibility;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import com.okaynow.users.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final ServiceRegionService serviceRegionService;
    private final LegalDocumentService legalDocumentService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            // Admin accounts are provisioned internally, never through public sign-up.
            throw new BadRequestException("ADMIN accounts cannot be self-registered");
        }
        if (request.acceptedLegalDocumentIds() == null || request.acceptedLegalDocumentIds().isEmpty()) {
            throw new BadRequestException(
                    "You must accept the current Terms of Service, Privacy Policy, and Platform Policy");
        }
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        legalDocumentService.accept(
                new AcceptLegalDocumentsRequest(request.acceptedLegalDocumentIds()), user);
        var status = legalDocumentService.acceptanceStatus(user);
        if (!status.upToDate()) {
            throw new BadRequestException(
                    "You must accept the latest published Terms, Privacy Policy, and Platform Policy");
        }

        if (request.role() == Role.CAREGIVER) {
            caregiverProfileRepository.save(CaregiverProfile.builder()
                    .user(user)
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .build());
        } else if (request.role() == Role.CLIENT) {
            if (request.registeringForSelf() == null) {
                throw new BadRequestException(
                        "Indicate whether you are registering for yourself or another person");
            }
            boolean registeringForSelf = request.registeringForSelf();
            MedicaidEligibility medicaidEligible = null;
            CareRecipientRelationship relationship = null;
            if (!registeringForSelf) {
                if (request.medicaidEligible() == null) {
                    throw new BadRequestException(
                            "Medicaid eligibility is required when registering for another person");
                }
                if (request.relationshipToCareRecipient() == null) {
                    throw new BadRequestException(
                            "Relationship to the care recipient is required when registering for another person");
                }
                medicaidEligible = request.medicaidEligible();
                relationship = request.relationshipToCareRecipient();
            }
            clientProfileRepository.save(ClientProfile.builder()
                    .user(user)
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .registeringForSelf(registeringForSelf)
                    .medicaidEligible(medicaidEligible)
                    .relationshipToCareRecipient(relationship)
                    .build());
        } else if (request.role() == Role.FACILITY) {
            if (isBlank(request.facilityName())
                    || isBlank(request.addressLine())
                    || isBlank(request.city())
                    || isBlank(request.zip())) {
                throw new BadRequestException(
                        "Facility name and full service address are required for facility registration");
            }
            var region = serviceRegionService.validate(request.state(), request.zip());
            facilityProfileRepository.save(FacilityProfile.builder()
                    .user(user)
                    .facilityName(request.facilityName().trim())
                    .contactFirstName(request.firstName().trim())
                    .contactLastName(request.lastName().trim())
                    .addressLine(request.addressLine().trim())
                    .city(request.city().trim())
                    .state(region.state())
                    .zip(region.zip())
                    .build());
        }

        return issueTokens(user);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Account is not active");
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest request) {
        Claims claims = tokenProvider.parseClaimsOrNull(request.refreshToken());
        if (claims == null || !tokenProvider.isRefreshToken(claims)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Account is not active");
        }
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String access = tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refresh = tokenProvider.createRefreshToken(user.getId(), user.getEmail(), user.getRole().name());
        return AuthResponse.bearer(access, refresh, tokenProvider.getAccessTokenValiditySeconds(),
                user.getId(), user.getEmail(), user.getRole());
    }
}
