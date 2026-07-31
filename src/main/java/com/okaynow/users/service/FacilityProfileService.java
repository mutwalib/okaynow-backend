package com.okaynow.users.service;

import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.User;
import com.okaynow.users.dto.FacilityProfileResponse;
import com.okaynow.users.dto.UpdateFacilityProfileRequest;
import com.okaynow.users.repository.FacilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FacilityProfileService {

    private final FacilityProfileRepository facilityProfileRepository;
    private final ServiceRegionService serviceRegionService;

    @Transactional(readOnly = true)
    public FacilityProfileResponse getByUserId(UUID userId) {
        return toResponse(findByUserId(userId));
    }

    @Transactional
    public FacilityProfileResponse update(UUID userId, UpdateFacilityProfileRequest request) {
        FacilityProfile profile = findByUserId(userId);
        profile.setContactFirstName(request.contactFirstName().trim());
        profile.setContactLastName(request.contactLastName().trim());
        profile.setAddressLine(request.addressLine().trim());
        profile.setCity(request.city().trim());
        var region = serviceRegionService.validate(request.state(), request.zip());
        profile.setState(region.state());
        profile.setZip(region.zip());
        profile.setLat(request.lat());
        profile.setLng(request.lng());
        profile.setNotes(request.notes());

        User user = profile.getUser();
        String phone = request.phone() == null || request.phone().isBlank()
                ? null
                : request.phone().trim();
        user.setPhone(phone);

        return toResponse(profile);
    }

    private FacilityProfile findByUserId(UUID userId) {
        return facilityProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
    }

    private FacilityProfileResponse toResponse(FacilityProfile profile) {
        User user = profile.getUser();
        return new FacilityProfileResponse(
                profile.getId(),
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                profile.getFacilityName(),
                profile.getContactFirstName(),
                profile.getContactLastName(),
                profile.getAddressLine(),
                profile.getCity(),
                profile.getState(),
                profile.getZip(),
                profile.getLat(),
                profile.getLng(),
                profile.getNotes());
    }
}
