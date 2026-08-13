package com.okaynow.users.service;

import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.GeocodingService;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.dto.ClientProfileResponse;
import com.okaynow.users.dto.UpdateClientProfileRequest;
import com.okaynow.users.mapper.UserMapper;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.UserRepository;
import com.okaynow.users.support.LegalNameGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientProfileService {

    private final ClientProfileRepository clientProfileRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ServiceRegionService serviceRegionService;
    private final GeocodingService geocodingService;

    @Transactional(readOnly = true)
    public ClientProfileResponse getByUserId(UUID userId) {
        return userMapper.toClientProfileResponse(findByUserId(userId));
    }

    @Transactional
    public ClientProfileResponse update(UUID userId, UpdateClientProfileRequest request) {
        assertProfileEditable(userId);
        ClientProfile profile = findByUserId(userId);
        LegalNameGuard.assertUnchanged(
                profile.getFirstName(), profile.getLastName(),
                request.firstName(), request.lastName());
        profile.setAddressLine(request.addressLine());
        profile.setCity(request.city());
        String state = request.state() != null ? request.state() : profile.getState();
        String zip = request.zip() != null ? request.zip() : profile.getZip();
        boolean addressTouched = request.addressLine() != null || request.city() != null
                || request.state() != null || request.zip() != null;
        if (addressTouched) {
            if (zip == null || zip.isBlank()) {
                throw new BadRequestException(
                        "ZIP code is required for a Massachusetts service address");
            }
            var region = serviceRegionService.validate(state, zip);
            profile.setState(region.state());
            profile.setZip(region.zip());
            if (!isBlank(profile.getAddressLine()) && !isBlank(profile.getCity())) {
                var coords = geocodingService.requireGeocode(
                        profile.getAddressLine(),
                        profile.getCity(),
                        profile.getState(),
                        profile.getZip());
                profile.setLat(coords.lat());
                profile.setLng(coords.lng());
            } else if (request.lat() != null && request.lng() != null) {
                profile.setLat(request.lat());
                profile.setLng(request.lng());
            }
        } else if (request.lat() != null && request.lng() != null) {
            profile.setLat(request.lat());
            profile.setLng(request.lng());
        }
        profile.setCareNeeds(request.careNeeds());
        return userMapper.toClientProfileResponse(profile);
    }

    @Transactional
    public ClientProfileResponse uploadPhoto(UUID userId, MultipartFile file) {
        throw new BadRequestException(
                "Client profile photos are not collected. Contact the agency if a document is required.");
    }

    private void assertProfileEditable(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException(
                    "Your profile is locked after verification. You can change your password from account settings.");
        }
        if (user.getApplicationSubmittedAt() != null) {
            throw new BadRequestException(
                    "Your application is locked after submission. If the agency asks you to update something, it will reopen below.");
        }
    }

    private ClientProfile findByUserId(UUID userId) {
        return clientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
