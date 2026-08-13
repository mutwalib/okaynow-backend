package com.okaynow.users.mapper;

import com.okaynow.users.domain.CareRecipientRelationship;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.MedicaidEligibility;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.dto.CaregiverProfileResponse;
import com.okaynow.users.dto.ClientProfileResponse;
import com.okaynow.users.dto.UserResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-13T12:53:42-0400",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 26.0.1 (Eclipse Adoptium)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserResponse toUserResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UUID id = null;
        String email = null;
        String phone = null;
        Role role = null;
        UserStatus status = null;
        Instant createdAt = null;

        id = user.getId();
        email = user.getEmail();
        phone = user.getPhone();
        role = user.getRole();
        status = user.getStatus();
        createdAt = user.getCreatedAt();

        UserResponse userResponse = new UserResponse( id, email, phone, role, status, createdAt );

        return userResponse;
    }

    @Override
    public CaregiverProfileResponse toCaregiverProfileResponse(CaregiverProfile profile) {
        if ( profile == null ) {
            return null;
        }

        UUID userId = null;
        UUID id = null;
        String firstName = null;
        String lastName = null;
        Set<Qualification> qualifications = null;
        BigDecimal hourlyRateMin = null;
        BigDecimal hourlyRateMax = null;
        Integer serviceRadiusMiles = null;
        Double homeLat = null;
        Double homeLng = null;
        String profilePhotoUrl = null;
        BigDecimal ratingAvg = null;
        Integer ratingCount = null;

        userId = profileUserId( profile );
        id = profile.getId();
        firstName = profile.getFirstName();
        lastName = profile.getLastName();
        Set<Qualification> set = profile.getQualifications();
        if ( set != null ) {
            qualifications = new LinkedHashSet<Qualification>( set );
        }
        hourlyRateMin = profile.getHourlyRateMin();
        hourlyRateMax = profile.getHourlyRateMax();
        serviceRadiusMiles = profile.getServiceRadiusMiles();
        homeLat = profile.getHomeLat();
        homeLng = profile.getHomeLng();
        profilePhotoUrl = profile.getProfilePhotoUrl();
        ratingAvg = profile.getRatingAvg();
        ratingCount = profile.getRatingCount();

        CaregiverProfileResponse caregiverProfileResponse = new CaregiverProfileResponse( id, userId, firstName, lastName, qualifications, hourlyRateMin, hourlyRateMax, serviceRadiusMiles, homeLat, homeLng, profilePhotoUrl, ratingAvg, ratingCount );

        return caregiverProfileResponse;
    }

    @Override
    public ClientProfileResponse toClientProfileResponse(ClientProfile profile) {
        if ( profile == null ) {
            return null;
        }

        UUID userId = null;
        UUID id = null;
        String firstName = null;
        String lastName = null;
        String addressLine = null;
        String city = null;
        String state = null;
        String zip = null;
        Double lat = null;
        Double lng = null;
        String careNeeds = null;
        boolean registeringForSelf = false;
        MedicaidEligibility medicaidEligible = null;
        CareRecipientRelationship relationshipToCareRecipient = null;
        boolean canViewShifts = false;
        boolean canCreateShifts = false;
        boolean canUpdateShifts = false;
        boolean canDeleteShifts = false;
        String profilePhotoUrl = null;

        userId = profileUserId1( profile );
        id = profile.getId();
        firstName = profile.getFirstName();
        lastName = profile.getLastName();
        addressLine = profile.getAddressLine();
        city = profile.getCity();
        state = profile.getState();
        zip = profile.getZip();
        lat = profile.getLat();
        lng = profile.getLng();
        careNeeds = profile.getCareNeeds();
        registeringForSelf = profile.isRegisteringForSelf();
        medicaidEligible = profile.getMedicaidEligible();
        relationshipToCareRecipient = profile.getRelationshipToCareRecipient();
        canViewShifts = profile.isCanViewShifts();
        canCreateShifts = profile.isCanCreateShifts();
        canUpdateShifts = profile.isCanUpdateShifts();
        canDeleteShifts = profile.isCanDeleteShifts();
        profilePhotoUrl = profile.getProfilePhotoUrl();

        ClientProfileResponse clientProfileResponse = new ClientProfileResponse( id, userId, firstName, lastName, addressLine, city, state, zip, lat, lng, careNeeds, registeringForSelf, medicaidEligible, relationshipToCareRecipient, canViewShifts, canCreateShifts, canUpdateShifts, canDeleteShifts, profilePhotoUrl );

        return clientProfileResponse;
    }

    private UUID profileUserId(CaregiverProfile caregiverProfile) {
        if ( caregiverProfile == null ) {
            return null;
        }
        User user = caregiverProfile.getUser();
        if ( user == null ) {
            return null;
        }
        UUID id = user.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private UUID profileUserId1(ClientProfile clientProfile) {
        if ( clientProfile == null ) {
            return null;
        }
        User user = clientProfile.getUser();
        if ( user == null ) {
            return null;
        }
        UUID id = user.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
