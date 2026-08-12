package com.okaynow.admin.service;

import com.okaynow.admin.dto.AdminClientResponse;
import com.okaynow.admin.dto.ClientType;
import com.okaynow.admin.dto.CreateClientRequest;
import com.okaynow.admin.dto.UpdateClientShiftPermissionsRequest;
import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.users.domain.CareRecipientRelationship;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.MedicaidEligibility;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminClientService {

    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final ServiceRegionService serviceRegionService;

    @Transactional(readOnly = true)
    public PagedResponse<AdminClientResponse> search(String search, Pageable pageable) {
        String normalizedSearch = search == null ? "" : search.trim();
        List<AdminClientResponse> merged = new ArrayList<>();
        clientProfileRepository.search(normalizedSearch, Pageable.unpaged())
                .forEach(profile -> merged.add(toFamilyResponse(profile)));
        facilityProfileRepository.search(normalizedSearch, Pageable.unpaged())
                .forEach(profile -> merged.add(toFacilityResponse(profile)));

        merged.sort(Comparator.comparing(this::sortKey, String.CASE_INSENSITIVE_ORDER));

        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.max(pageable.getPageSize(), 1);
        int from = Math.min(page * size, merged.size());
        int to = Math.min(from + size, merged.size());
        List<AdminClientResponse> slice = merged.subList(from, to);
        int totalPages = (int) Math.ceil(merged.size() / (double) size);
        boolean last = page >= Math.max(totalPages - 1, 0);
        return new PagedResponse<>(slice, page, size, merged.size(), Math.max(totalPages, 1), last);
    }

    @Transactional
    public AdminClientResponse create(CreateClientRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account with this email already exists");
        }

        boolean registeringForSelf = Boolean.TRUE.equals(request.registeringForSelf());
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

        User user = userRepository.save(User.builder()
                .email(email)
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.CLIENT)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .emailVerifiedAt(java.time.Instant.now())
                .build());

        var region = serviceRegionService.validate(request.state(), request.zip());
        ClientProfile profile = clientProfileRepository.save(ClientProfile.builder()
                .user(user)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .addressLine(request.addressLine().trim())
                .city(request.city().trim())
                .state(region.state())
                .zip(region.zip())
                .lat(request.lat())
                .lng(request.lng())
                .careNeeds(request.careNeeds())
                .registeringForSelf(registeringForSelf)
                .medicaidEligible(medicaidEligible)
                .relationshipToCareRecipient(relationship)
                .build());
        return toFamilyResponse(profile);
    }

    @Transactional
    public AdminClientResponse updateShiftPermissions(
            UUID clientProfileId,
            UpdateClientShiftPermissionsRequest request,
            String actingAdminEmail) {
        if (facilityProfileRepository.existsById(clientProfileId)) {
            throw new BadRequestException(
                    "Facility clients manage their own shifts; family shift permissions do not apply");
        }
        ClientProfile profile = clientProfileRepository.findById(clientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        User actor = userRepository.findByEmail(actingAdminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));

        profile.setCanViewShifts(request.canViewShifts());
        profile.setCanCreateShifts(request.canCreateShifts());
        profile.setCanUpdateShifts(request.canUpdateShifts());
        profile.setCanDeleteShifts(request.canDeleteShifts());

        String details = "view=%s, create=%s, update=%s, delete=%s".formatted(
                request.canViewShifts(), request.canCreateShifts(),
                request.canUpdateShifts(), request.canDeleteShifts());
        auditLogService.record(actor, AuditAction.CLIENT_PERMISSIONS_UPDATED,
                "CLIENT_PROFILE", profile.getId(), profile.getId(), details);
        return toFamilyResponse(profile);
    }

    private String sortKey(AdminClientResponse row) {
        if (row.clientType() == ClientType.FACILITY) {
            return row.facilityName() == null ? "" : row.facilityName();
        }
        return ((row.lastName() == null ? "" : row.lastName()) + " "
                + (row.firstName() == null ? "" : row.firstName())).trim();
    }

    private AdminClientResponse toFamilyResponse(ClientProfile profile) {
        User user = profile.getUser();
        return new AdminClientResponse(
                profile.getId(),
                ClientType.FAMILY,
                null,
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getAddressLine(),
                profile.getCity(),
                profile.getState(),
                profile.getZip(),
                profile.getLat(),
                profile.getLng(),
                profile.getCareNeeds(),
                profile.isRegisteringForSelf(),
                profile.getMedicaidEligible(),
                profile.getRelationshipToCareRecipient(),
                profile.isCanViewShifts(),
                profile.isCanCreateShifts(),
                profile.isCanUpdateShifts(),
                profile.isCanDeleteShifts());
    }

    private AdminClientResponse toFacilityResponse(FacilityProfile profile) {
        User user = profile.getUser();
        return new AdminClientResponse(
                profile.getId(),
                ClientType.FACILITY,
                profile.getFacilityName(),
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                profile.getContactFirstName(),
                profile.getContactLastName(),
                profile.getAddressLine(),
                profile.getCity(),
                profile.getState(),
                profile.getZip(),
                profile.getLat(),
                profile.getLng(),
                profile.getNotes(),
                true,
                null,
                null,
                true,
                true,
                true,
                true);
    }
}
