package com.okaynow.users.service;

import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.storage.LocalFileStorageService;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.dto.CaregiverProfileResponse;
import com.okaynow.users.dto.UpdateCaregiverProfileRequest;
import com.okaynow.users.mapper.UserMapper;
import com.okaynow.users.repository.CaregiverProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverProfileService {

    private final CaregiverProfileRepository caregiverProfileRepository;
    private final UserMapper userMapper;
    private final LocalFileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public CaregiverProfileResponse getByUserId(UUID userId) {
        return userMapper.toCaregiverProfileResponse(findByUserId(userId));
    }

    @Transactional
    public CaregiverProfileResponse update(UUID userId, UpdateCaregiverProfileRequest request) {
        CaregiverProfile profile = findByUserId(userId);
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        if (request.qualifications() != null) {
            profile.getQualifications().clear();
            profile.getQualifications().addAll(request.qualifications());
        }
        profile.setHourlyRateMin(request.hourlyRateMin());
        profile.setHourlyRateMax(request.hourlyRateMax());
        profile.setServiceRadiusMiles(request.serviceRadiusMiles());
        profile.setHomeLat(request.homeLat());
        profile.setHomeLng(request.homeLng());
        return userMapper.toCaregiverProfileResponse(profile);
    }

    @Transactional
    public CaregiverProfileResponse uploadPhoto(UUID userId, MultipartFile file) {
        CaregiverProfile profile = findByUserId(userId);
        profile.setProfilePhotoUrl(fileStorageService.storeProfilePhoto(profile.getId(), file));
        return userMapper.toCaregiverProfileResponse(profile);
    }

    private CaregiverProfile findByUserId(UUID userId) {
        return caregiverProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
    }
}
