package com.okaynow.users.service;

import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.storage.LocalFileStorageService;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.dto.ClientProfileResponse;
import com.okaynow.users.dto.UpdateClientProfileRequest;
import com.okaynow.users.mapper.UserMapper;
import com.okaynow.users.repository.ClientProfileRepository;
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
    private final UserMapper userMapper;
    private final ServiceRegionService serviceRegionService;
    private final LocalFileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public ClientProfileResponse getByUserId(UUID userId) {
        return userMapper.toClientProfileResponse(findByUserId(userId));
    }

    @Transactional
    public ClientProfileResponse update(UUID userId, UpdateClientProfileRequest request) {
        ClientProfile profile = findByUserId(userId);
        LegalNameGuard.assertUnchanged(
                profile.getFirstName(), profile.getLastName(),
                request.firstName(), request.lastName());
        profile.setAddressLine(request.addressLine());
        profile.setCity(request.city());
        String state = request.state() != null ? request.state() : profile.getState();
        String zip = request.zip() != null ? request.zip() : profile.getZip();
        if (request.addressLine() != null || request.city() != null
                || request.state() != null || request.zip() != null) {
            if (zip == null || zip.isBlank()) {
                throw new BadRequestException(
                        "ZIP code is required for a Massachusetts service address");
            }
            var region = serviceRegionService.validate(state, zip);
            profile.setState(region.state());
            profile.setZip(region.zip());
        }
        profile.setLat(request.lat());
        profile.setLng(request.lng());
        profile.setCareNeeds(request.careNeeds());
        return userMapper.toClientProfileResponse(profile);
    }

    @Transactional
    public ClientProfileResponse uploadPhoto(UUID userId, MultipartFile file) {
        ClientProfile profile = findByUserId(userId);
        profile.setProfilePhotoUrl(fileStorageService.storeProfilePhoto(profile.getId(), file));
        return userMapper.toClientProfileResponse(profile);
    }

    private ClientProfile findByUserId(UUID userId) {
        return clientProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
    }
}
