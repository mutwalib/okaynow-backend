package com.okaynow.agencies.support;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.domain.AgencyStaff;
import com.okaynow.agencies.repository.AgencyStaffRepository;
import com.okaynow.common.exception.ForbiddenException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgencyAccessService {

    private final AgencyStaffRepository agencyStaffRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AgencyStaff requireStaffForUser(UUID userId) {
        return agencyStaffRepository.findFirstByUserIdWithAgency(userId)
                .or(() -> agencyStaffRepository.findFirstByUserId(userId))
                .orElseThrow(() -> new ForbiddenException("No agency tenant is linked to this account"));
    }

    @Transactional(readOnly = true)
    public Agency requireAgencyForUser(UUID userId) {
        return requireStaffForUser(userId).getAgency();
    }

    @Transactional(readOnly = true)
    public UUID requireWritableAgencyId(UUID userId) {
        Agency agency = requireAgencyForUser(userId);
        assertAgencyAllowsWrites(agency);
        return agency.getId();
    }

    public void assertAgencyAllowsWrites(Agency agency) {
        if (!agency.accessIsActive()) {
            throw new ForbiddenException(accessDeniedMessage(agency));
        }
        if (!agency.subscriptionAllowsWrites()) {
            throw new ForbiddenException(
                    "Agency subscription is inactive. Renew billing to continue.");
        }
    }

    public static String accessDeniedMessage(Agency agency) {
        return switch (agency.getAccessStatus()) {
            case PENDING_APPROVAL ->
                    "Your agency is awaiting OkayNow approval before you can use the console.";
            case SUSPENDED ->
                    "Your agency access is suspended. Contact OkayNow support for help.";
            case BLOCKED ->
                    "Your agency access is blocked. Contact OkayNow support for help.";
            case ACTIVE -> "Agency access is restricted.";
        };
    }

    public User requireSuperAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Platform admin access required");
        }
        return user;
    }
}
