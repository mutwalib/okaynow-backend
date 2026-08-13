package com.okaynow.users.service;

import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.GeocodingService;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.storage.LocalFileStorageService;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.dto.CaregiverProfileResponse;
import com.okaynow.users.dto.UpdateCaregiverProfileRequest;
import com.okaynow.users.mapper.UserMapper;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.UserRepository;
import com.okaynow.users.support.LegalNameGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverProfileService {

    private final CaregiverProfileRepository caregiverProfileRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final LocalFileStorageService fileStorageService;
    private final ServiceRegionService serviceRegionService;
    private final GeocodingService geocodingService;

    @Transactional(readOnly = true)
    public CaregiverProfileResponse getByUserId(UUID userId) {
        return userMapper.toCaregiverProfileResponse(findByUserId(userId));
    }

    @Transactional
    public CaregiverProfileResponse update(UUID userId, UpdateCaregiverProfileRequest request) {
        assertProfileEditable(userId);
        CaregiverProfile profile = findByUserId(userId);
        LegalNameGuard.assertUnchanged(
                profile.getFirstName(), profile.getLastName(),
                request.firstName(), request.lastName());
        if (request.qualifications() != null) {
            profile.getQualifications().clear();
            profile.getQualifications().addAll(request.qualifications());
        }
        profile.setHourlyRateMin(request.hourlyRateMin());
        profile.setHourlyRateMax(request.hourlyRateMax());
        profile.setServiceRadiusMiles(request.serviceRadiusMiles());

        boolean addressProvided = !isBlank(request.homeAddressLine())
                || !isBlank(request.homeCity())
                || !isBlank(request.homeZip())
                || !isBlank(request.homeState());
        if (addressProvided) {
            if (isBlank(request.homeAddressLine()) || isBlank(request.homeCity()) || isBlank(request.homeZip())) {
                throw new BadRequestException("Enter street address, city, and ZIP for your home location");
            }
            var region = serviceRegionService.validate(request.homeState(), request.homeZip());
            profile.setHomeAddressLine(request.homeAddressLine().trim());
            profile.setHomeCity(request.homeCity().trim());
            profile.setHomeState(region.state());
            profile.setHomeZip(region.zip());
            var coords = geocodingService.requireGeocode(
                    profile.getHomeAddressLine(),
                    profile.getHomeCity(),
                    profile.getHomeState(),
                    profile.getHomeZip());
            profile.setHomeLat(coords.lat());
            profile.setHomeLng(coords.lng());
        } else if (request.homeLat() != null && request.homeLng() != null) {
            // Backward-compatible path for older clients until they send address fields.
            profile.setHomeLat(request.homeLat());
            profile.setHomeLng(request.homeLng());
        }

        return userMapper.toCaregiverProfileResponse(profile);
    }

    @Transactional
    public CaregiverProfileResponse uploadPhoto(UUID userId, MultipartFile file) {
        assertProfileEditable(userId);
        CaregiverProfile profile = findByUserId(userId);
        profile.setProfilePhotoUrl(fileStorageService.storeProfilePhoto(profile.getId(), file));
        return userMapper.toCaregiverProfileResponse(profile);
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

    private CaregiverProfile findByUserId(UUID userId) {
        return caregiverProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
