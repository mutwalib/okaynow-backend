package com.okaynow.agencies.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.domain.AgencyStaff;
import com.okaynow.agencies.domain.AgencyStaffRole;
import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;
import com.okaynow.agencies.dto.AgencyMeResponse;
import com.okaynow.agencies.dto.AgencyPublicProfileResponse;
import com.okaynow.agencies.dto.SuperAdminAgencyResponse;
import com.okaynow.agencies.dto.SuperAdminUpdateSubscriptionRequest;
import com.okaynow.agencies.dto.UpdateAgencyDirectoryProfileRequest;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.repository.AgencyStaffRepository;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.agencies.support.AgencySlugService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.GeocodingService;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencyService {

    private final AgencyRepository agencyRepository;
    private final AgencyStaffRepository agencyStaffRepository;
    private final AgencyAccessService agencyAccessService;
    private final AgencySlugService agencySlugService;
    private final ServiceRegionService serviceRegionService;
    private final GeocodingService geocodingService;
    private final StripeBillingService stripeBillingService;

    @Transactional
    public Agency createForRegistration(User adminUser, String agencyName, String addressLine,
                                        String city, String state, String zip) {
        if (agencyStaffRepository.findFirstByUserId(adminUser.getId()).isPresent()) {
            throw new BadRequestException("This account is already linked to an agency");
        }
        var region = serviceRegionService.validate(state, zip);
        String displayName = agencyName.trim();
        Agency agency = Agency.builder()
                .slug(agencySlugService.generateUniqueSlug(displayName))
                .legalName(displayName)
                .displayName(displayName)
                .addressLine(addressLine.trim())
                .city(city.trim())
                .state(region.state())
                .zip(region.zip())
                .subscriptionStatus(SubscriptionStatus.TRIAL)
                .subscriptionPlan(SubscriptionPlan.STARTER)
                .subscriptionPeriodStart(Instant.now())
                .subscriptionPeriodEnd(Instant.now().plus(14, ChronoUnit.DAYS))
                .directoryListed(false)
                .build();
        geocodeAgency(agency);
        agency = agencyRepository.save(agency);
        agencyStaffRepository.save(AgencyStaff.builder()
                .agency(agency)
                .user(adminUser)
                .role(AgencyStaffRole.ADMIN)
                .build());
        return agency;
    }

    @Transactional(readOnly = true)
    public AgencyMeResponse getMe(UUID userId) {
        Agency agency = agencyAccessService.requireAgencyForUser(userId);
        return toMeResponse(agency);
    }

    @Transactional(readOnly = true)
    public AgencyPublicProfileResponse getPublicProfile(String slug) {
        Agency agency = agencyRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
        if (!agency.subscriptionAllowsDirectoryListing()) {
            throw new ResourceNotFoundException("Agency not found");
        }
        return toPublicProfile(agency);
    }

    @Transactional
    public AgencyMeResponse updateDirectoryProfile(UUID userId, UpdateAgencyDirectoryProfileRequest request) {
        Agency agency = agencyAccessService.requireAgencyForUser(userId);
        agencyAccessService.assertAgencyAllowsWrites(agency);

        agency.setDisplayName(request.displayName().trim());
        if (request.legalName() != null && !request.legalName().isBlank()) {
            agency.setLegalName(request.legalName().trim());
        }
        agency.setLicenseNumber(trimOrNull(request.licenseNumber()));
        if (request.addressLine() != null) {
            agency.setAddressLine(request.addressLine().trim());
        }
        if (request.city() != null) {
            agency.setCity(request.city().trim());
        }
        if (request.state() != null && request.zip() != null) {
            var region = serviceRegionService.validate(request.state(), request.zip());
            agency.setState(region.state());
            agency.setZip(region.zip());
        }
        if (request.serviceRadiusMiles() != null) {
            agency.setServiceRadiusMiles(Math.max(1, request.serviceRadiusMiles()));
        }
        if (request.publicDescription() != null) {
            agency.setPublicDescription(request.publicDescription().trim());
        }
        if (request.qualificationsSupported() != null) {
            agency.setQualificationsSupported(new HashSet<>(request.qualificationsSupported()));
        }
        if (request.directoryListed() != null) {
            agency.setDirectoryListed(request.directoryListed());
        }
        geocodeAgency(agency);
        return toMeResponse(agencyRepository.save(agency));
    }

    @Transactional(readOnly = true)
    public List<SuperAdminAgencyResponse> listAllForSuperAdmin() {
        return agencyRepository.findAll().stream()
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .map(this::toSuperAdminResponse)
                .toList();
    }

    @Transactional
    public SuperAdminAgencyResponse updateSubscriptionForSuperAdmin(
            UUID agencyId, SuperAdminUpdateSubscriptionRequest request) {
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
        if (request.subscriptionStatus() != null) {
            agency.setSubscriptionStatus(request.subscriptionStatus());
        }
        if (request.subscriptionPlan() != null) {
            agency.setSubscriptionPlan(request.subscriptionPlan());
        }
        if (request.directoryListed() != null) {
            agency.setDirectoryListed(request.directoryListed());
        }
        if (request.subscriptionPeriodEnd() != null) {
            agency.setSubscriptionPeriodEnd(request.subscriptionPeriodEnd());
        }
        return toSuperAdminResponse(agencyRepository.save(agency));
    }

    private void geocodeAgency(Agency agency) {
        if (agency.getAddressLine() == null || agency.getCity() == null || agency.getZip() == null) {
            return;
        }
        geocodingService.geocode(
                        agency.getAddressLine(),
                        agency.getCity(),
                        agency.getState(),
                        agency.getZip())
                .ifPresent(point -> {
                    agency.setLat(point.lat());
                    agency.setLng(point.lng());
                });
    }

    private AgencyMeResponse toMeResponse(Agency agency) {
        return new AgencyMeResponse(
                agency.getId(),
                agency.getSlug(),
                agency.getLegalName(),
                agency.getDisplayName(),
                agency.getLicenseNumber(),
                agency.getAddressLine(),
                agency.getCity(),
                agency.getState(),
                agency.getZip(),
                agency.getLat(),
                agency.getLng(),
                agency.getServiceRadiusMiles(),
                agency.getPublicDescription(),
                new ArrayList<>(agency.getQualificationsSupported()),
                agency.getSubscriptionStatus(),
                agency.getSubscriptionPlan(),
                agency.getSubscriptionPeriodStart(),
                agency.getSubscriptionPeriodEnd(),
                agency.isDirectoryListed(),
                stripeBillingService.isConfigured(),
                agency.subscriptionAllowsWrites());
    }

    private AgencyPublicProfileResponse toPublicProfile(Agency agency) {
        return new AgencyPublicProfileResponse(
                agency.getId(),
                agency.getSlug(),
                agency.getDisplayName(),
                agency.getLegalName(),
                agency.getLicenseNumber(),
                agency.getAddressLine(),
                agency.getCity(),
                agency.getState(),
                agency.getZip(),
                agency.getLat(),
                agency.getLng(),
                agency.getServiceRadiusMiles(),
                agency.getPublicDescription(),
                new ArrayList<>(agency.getQualificationsSupported()),
                agency.getSubscriptionPlan(),
                agency.getSubscriptionStatus(),
                agency.isDirectoryListed());
    }

    private SuperAdminAgencyResponse toSuperAdminResponse(Agency agency) {
        return new SuperAdminAgencyResponse(
                agency.getId(),
                agency.getSlug(),
                agency.getDisplayName(),
                agency.getSubscriptionStatus(),
                agency.getSubscriptionPlan(),
                agency.isDirectoryListed(),
                agency.getSubscriptionPeriodEnd(),
                agency.getCreatedAt());
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
