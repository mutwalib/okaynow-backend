package com.okaynow.staffing.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.staffing.domain.AssignmentType;
import com.okaynow.staffing.domain.ClientCaregiverAssignment;
import com.okaynow.staffing.dto.ClientCaregiverAssignmentResponse;
import com.okaynow.staffing.dto.ClientRosterCaregiverResponse;
import com.okaynow.staffing.dto.CreateClientCaregiverAssignmentRequest;
import com.okaynow.staffing.repository.ClientCaregiverAssignmentRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientStaffingService {

    private final ClientCaregiverAssignmentRepository assignmentRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ClientCaregiverAssignmentResponse> listForClient(UUID clientProfileId) {
        requireClient(clientProfileId);
        return assignmentRepository
                .findByClientProfileIdAndActiveTrueOrderByCreatedAtAsc(clientProfileId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClientRosterCaregiverResponse> listMineForClientUser(User actor) {
        ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
        return assignmentRepository
                .findByClientProfileIdAndActiveTrueOrderByCreatedAtAsc(client.getId())
                .stream()
                .map(this::toRosterResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isOnClientRoster(UUID clientProfileId, UUID caregiverProfileId) {
        return assignmentRepository
                .findByClientProfileIdAndCaregiverProfileId(clientProfileId, caregiverProfileId)
                .filter(ClientCaregiverAssignment::isActive)
                .isPresent();
    }

    @Transactional
    public ClientCaregiverAssignmentResponse assign(
            UUID clientProfileId,
            CreateClientCaregiverAssignmentRequest request,
            String adminEmail) {
        ClientProfile client = requireClient(clientProfileId);
        CaregiverProfile caregiver = caregiverProfileRepository.findById(request.caregiverProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver not found"));

        assignmentRepository
                .findByClientProfileIdAndCaregiverProfileId(clientProfileId, caregiver.getId())
                .ifPresent(existing -> {
                    if (existing.isActive()) {
                        throw new ConflictException("This caregiver is already on the client roster");
                    }
                });

        if (request.assignmentType() == AssignmentType.PRIMARY
                && assignmentRepository.countActiveByClientAndType(
                clientProfileId, AssignmentType.PRIMARY, null) > 0) {
            throw new ConflictException(
                    "This client already has a PRIMARY caregiver; use ROTATIONAL or replace the primary first");
        }

        var prior = assignmentRepository
                .findByClientProfileIdAndCaregiverProfileId(clientProfileId, caregiver.getId());
        ClientCaregiverAssignment assignment;
        if (prior.isPresent()) {
            assignment = prior.get();
            assignment.setActive(true);
            assignment.setAssignmentType(request.assignmentType());
            assignment.setNotes(request.notes());
        } else {
            assignment = assignmentRepository.save(ClientCaregiverAssignment.builder()
                    .clientProfile(client)
                    .caregiverProfile(caregiver)
                    .assignmentType(request.assignmentType())
                    .notes(request.notes())
                    .active(true)
                    .build());
        }

        User actor = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));
        auditLogService.record(actor, AuditAction.CLIENT_CAREGIVER_ASSIGNED, "CLIENT_PROFILE",
                client.getId(), client.getId(),
                "caregiver=%s type=%s".formatted(caregiver.getId(), request.assignmentType()));
        return toResponse(assignment);
    }

    @Transactional
    public ClientCaregiverAssignmentResponse unassign(UUID clientProfileId, UUID assignmentId, String adminEmail) {
        requireClient(clientProfileId);
        ClientCaregiverAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        if (!assignment.getClientProfile().getId().equals(clientProfileId)) {
            throw new BadRequestException("Assignment does not belong to this client");
        }
        if (!assignment.isActive()) {
            throw new ConflictException("This caregiver is already off the roster");
        }
        assignment.setActive(false);
        User actor = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));
        auditLogService.record(actor, AuditAction.CLIENT_CAREGIVER_UNASSIGNED, "CLIENT_PROFILE",
                clientProfileId, clientProfileId,
                "assignment=%s caregiver=%s".formatted(
                        assignment.getId(), assignment.getCaregiverProfile().getId()));
        return toResponse(assignment);
    }

    private ClientProfile requireClient(UUID clientProfileId) {
        return clientProfileRepository.findById(clientProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    private ClientCaregiverAssignmentResponse toResponse(ClientCaregiverAssignment assignment) {
        CaregiverProfile caregiver = assignment.getCaregiverProfile();
        return new ClientCaregiverAssignmentResponse(
                assignment.getId(),
                assignment.getClientProfile().getId(),
                caregiver.getId(),
                caregiver.getFirstName(),
                caregiver.getLastName(),
                caregiver.getUser().getEmail(),
                caregiver.getQualifications(),
                caregiver.getServiceRadiusMiles(),
                assignment.getAssignmentType(),
                assignment.isActive(),
                assignment.getNotes(),
                assignment.getCreatedAt());
    }

    private ClientRosterCaregiverResponse toRosterResponse(ClientCaregiverAssignment assignment) {
        CaregiverProfile caregiver = assignment.getCaregiverProfile();
        return new ClientRosterCaregiverResponse(
                assignment.getId(),
                caregiver.getId(),
                caregiver.getFirstName(),
                caregiver.getLastName(),
                caregiver.getQualifications(),
                assignment.getAssignmentType());
    }
}
