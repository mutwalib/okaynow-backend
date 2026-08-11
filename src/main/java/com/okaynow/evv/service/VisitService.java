package com.okaynow.evv.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.evv.domain.ClockMethod;
import com.okaynow.evv.domain.Visit;
import com.okaynow.evv.dto.ClientAttendanceRequest;
import com.okaynow.evv.dto.ClockInRequest;
import com.okaynow.evv.dto.ClockOutRequest;
import com.okaynow.evv.dto.VisitResponse;
import com.okaynow.evv.repository.VisitRepository;
import com.okaynow.evv.support.ShiftWindows;
import com.okaynow.marketplace.service.QualificationRulePackService;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.ShiftEventPublisher;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import com.okaynow.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VisitService {

    private static final Set<ShiftClaimStatus> ACTIVE_OR_COMPLETED = EnumSet.of(
            ShiftClaimStatus.CONFIRMED, ShiftClaimStatus.COMPLETED, ShiftClaimStatus.PENDING);

    private final VisitRepository visitRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final ShiftEventPublisher shiftEventPublisher;
    private final QualificationRulePackService qualificationRulePackService;

    @Transactional
    public VisitResponse clockIn(UUID shiftId, String caregiverEmail, ClockInRequest request) {
        User user = userService.getByEmail(caregiverEmail);
        CaregiverProfile caregiver = caregiverProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (shift.getStatus() != ShiftStatus.CONFIRMED && shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            throw new ConflictException("Clock-in is only allowed after the agency confirms the shift");
        }
        if (visitRepository.existsByShiftId(shiftId)) {
            throw new ConflictException("Already clocked in for this shift");
        }

        ShiftClaim claim = shiftClaimRepository
                .findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                        shiftId, caregiver.getId(),
                        EnumSet.of(ShiftClaimStatus.CONFIRMED, ShiftClaimStatus.PENDING))
                .orElseThrow(() -> new AccessDeniedException(
                        "You do not have an active assignment on this shift"));
        if (claim.getStatus() != ShiftClaimStatus.CONFIRMED) {
            throw new ConflictException("Your claim must be confirmed by the agency before clock-in");
        }

        assertCaregiverClockInWindow(shift, caregiver.getId());

        boolean hasGps = request != null && request.lat() != null && request.lng() != null;
        var pack = qualificationRulePackService.getOrCreate(shift.getRequiredQualification());
        if (pack.isEvvRequired() && !hasGps) {
            throw new BadRequestException(
                    "GPS clock-in is required for " + pack.getQualification() + " visits (EVV)");
        }
        Visit visit = Visit.builder()
                .shiftId(shiftId)
                .claimId(claim.getId())
                .caregiverProfileId(caregiver.getId())
                .clockInAt(Instant.now())
                .clockInLat(hasGps ? request.lat() : null)
                .clockInLng(hasGps ? request.lng() : null)
                .method(hasGps ? ClockMethod.GPS : ClockMethod.MANUAL)
                .notes(request != null ? request.notes() : null)
                .build();
        visit = visitRepository.save(visit);

        if (shift.getStatus() == ShiftStatus.CONFIRMED) {
            shift.setStatus(ShiftStatus.IN_PROGRESS);
        }

        auditLogService.record(user, AuditAction.CAREGIVER_CLOCKED_IN, "VISIT",
                visit.getId(), shift.getClientProfileId(),
                "shift=%s method=%s".formatted(shiftId, visit.getMethod()));
        shiftEventPublisher.publish(
                NotificationType.VISIT_CLOCK_IN,
                shift,
                caregiver.getUser().getId(),
                "Caregiver clocked in",
                caregiver.getFirstName() + " clocked in for the " + shift.getDate() + " shift.");
        return toResponse(visit);
    }

    @Transactional
    public VisitResponse clockOut(UUID shiftId, String caregiverEmail, ClockOutRequest request) {
        User user = userService.getByEmail(caregiverEmail);
        CaregiverProfile caregiver = caregiverProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
        Visit visit = visitRepository.findByShiftId(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("No clock-in found for this shift"));
        if (!visit.getCaregiverProfileId().equals(caregiver.getId())) {
            throw new AccessDeniedException("You did not clock in on this shift");
        }
        if (visit.getClockOutAt() != null) {
            throw new ConflictException("Already clocked out");
        }
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getStatus() != ShiftStatus.IN_PROGRESS) {
            throw new ConflictException("Shift is not in progress");
        }

        visit.setClockOutAt(Instant.now());
        if (request != null) {
            visit.setClockOutLat(request.lat());
            visit.setClockOutLng(request.lng());
        }
        visitRepository.save(visit);

        auditLogService.record(user, AuditAction.CAREGIVER_CLOCKED_OUT, "VISIT",
                visit.getId(), shift.getClientProfileId(), "shift=" + shiftId);
        shiftEventPublisher.publish(
                NotificationType.VISIT_CLOCK_OUT,
                shift,
                caregiver.getUser().getId(),
                "Caregiver clocked out",
                caregiver.getFirstName() + " clocked out of the " + shift.getDate() + " shift.");
        return toResponse(visit);
    }

    @Transactional
    public VisitResponse confirmArrival(UUID shiftId, String clientEmail) {
        User user = userService.getByEmail(clientEmail);
        ClientProfile client = clientProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getClientProfileId() == null || !shift.getClientProfileId().equals(client.getId())) {
            throw new AccessDeniedException("This shift does not belong to your account");
        }
        Visit visit = visitRepository.findByShiftId(shiftId)
                .orElseThrow(() -> new BadRequestException(
                        "The caregiver has not clocked in yet — nothing to confirm"));
        if (visit.isClientArrivalConfirmed()) {
            return toResponse(visit);
        }

        visit.setClientArrivalConfirmed(true);
        visit.setClientArrivalConfirmedAt(Instant.now());
        visit.setClientArrivalConfirmedByUserId(user.getId());
        visitRepository.save(visit);

        auditLogService.record(user, AuditAction.CLIENT_ARRIVAL_CONFIRMED, "VISIT",
                visit.getId(), client.getId(), "shift=" + shiftId);
        UUID caregiverUserId = shiftClaimRepository
                .findFirstByShiftIdAndStatusIn(shiftId, ACTIVE_OR_COMPLETED)
                .map(c -> c.getCaregiverProfile().getUser().getId())
                .orElse(null);
        shiftEventPublisher.publish(
                NotificationType.VISIT_ARRIVAL_CONFIRMED,
                shift,
                caregiverUserId,
                "Arrival confirmed",
                "The client confirmed caregiver arrival for the " + shift.getDate() + " shift.");
        return toResponse(visit);
    }

    /**
     * Client records or corrects clock-in/out when the caregiver missed self service.
     */
    @Transactional
    public VisitResponse recordClientAttendance(UUID shiftId, String clientEmail,
                                                ClientAttendanceRequest request) {
        User user = userService.getByEmail(clientEmail);
        ClientProfile client = clientProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (shift.getClientProfileId() == null || !shift.getClientProfileId().equals(client.getId())) {
            throw new AccessDeniedException("This shift does not belong to your account");
        }
        if (shift.getStatus() == ShiftStatus.CANCELLED || shift.getStatus() == ShiftStatus.DRAFT
                || shift.getStatus() == ShiftStatus.HELD
                || shift.getStatus() == ShiftStatus.OPEN || shift.getStatus() == ShiftStatus.CLAIMED) {
            throw new ConflictException("Attendance can only be recorded for a confirmed assignment");
        }

        ShiftClaim claim = shiftClaimRepository
                .findFirstByShiftIdAndStatusIn(shiftId,
                        EnumSet.of(ShiftClaimStatus.CONFIRMED, ShiftClaimStatus.COMPLETED))
                .orElseThrow(() -> new ConflictException(
                        "No confirmed caregiver assignment to record attendance for"));

        Instant clockInAt = request.clockInAt();
        Instant clockOutAt = request.clockOutAt();
        if (clockOutAt != null && !clockOutAt.isAfter(clockInAt)) {
            throw new BadRequestException("Clock-out must be after clock-in");
        }

        Visit visit = visitRepository.findByShiftId(shiftId).orElse(null);
        if (visit == null) {
            visit = Visit.builder()
                    .shiftId(shiftId)
                    .claimId(claim.getId())
                    .caregiverProfileId(claim.getCaregiverProfile().getId())
                    .clockInAt(clockInAt)
                    .clockOutAt(clockOutAt)
                    .method(ClockMethod.MANUAL)
                    .clientArrivalConfirmed(true)
                    .clientArrivalConfirmedAt(Instant.now())
                    .clientArrivalConfirmedByUserId(user.getId())
                    .notes(request.notes() != null && !request.notes().isBlank()
                            ? request.notes()
                            : "Attendance recorded by client (caregiver missed self clock-in)")
                    .build();
        } else {
            visit.setClockInAt(clockInAt);
            visit.setClockOutAt(clockOutAt);
            visit.setMethod(ClockMethod.MANUAL);
            visit.setClientArrivalConfirmed(true);
            if (visit.getClientArrivalConfirmedAt() == null) {
                visit.setClientArrivalConfirmedAt(Instant.now());
                visit.setClientArrivalConfirmedByUserId(user.getId());
            }
            if (request.notes() != null && !request.notes().isBlank()) {
                visit.setNotes(request.notes());
            }
        }
        visit = visitRepository.save(visit);

        if (shift.getStatus() == ShiftStatus.CONFIRMED) {
            shift.setStatus(ShiftStatus.IN_PROGRESS);
        }

        auditLogService.record(user, AuditAction.CLIENT_ATTENDANCE_RECORDED, "VISIT",
                visit.getId(), client.getId(),
                "shift=%s clockIn=%s clockOut=%s".formatted(shiftId, clockInAt, clockOutAt));
        return toResponse(visit);
    }

    private void assertCaregiverClockInWindow(Shift shift, UUID caregiverProfileId) {
        Instant now = Instant.now();
        Instant earliest = ShiftWindows.earliestClockIn(shift);
        Instant end = ShiftWindows.endInstant(shift);

        if (now.isBefore(earliest)) {
            throw new BadRequestException(
                    "Too early to clock in. You can clock in starting "
                            + ShiftWindows.EARLY_CLOCK_IN_MINUTES
                            + " minutes before the shift begins.");
        }

        if (!now.isAfter(end)) {
            return; // within normal window [earliest, end]
        }

        // After this shift's end: only allowed when moving into a consecutive next shift
        // that begins immediately after — never late into a finished shift.
        boolean hasImmediateNext = shiftClaimRepository
                .findActiveClaimsExcludingShift(
                        caregiverProfileId,
                        shift.getId(),
                        EnumSet.of(ShiftClaimStatus.CONFIRMED, ShiftClaimStatus.PENDING))
                .stream()
                .map(ShiftClaim::getShift)
                .anyMatch(other -> ShiftWindows.beginsImmediatelyAfter(shift, other));

        if (hasImmediateNext) {
            throw new BadRequestException(
                    "This shift has ended. Clock in on your next shift that starts immediately after this one. "
                            + "If you missed clock-in here, ask the client to update your times.");
        }

        throw new BadRequestException(
                "Clock-in is no longer available after the shift period ends. "
                        + "Ask the client to help update your clock-in and clock-out times.");
    }

    /**
     * Admin override start: marks IN_PROGRESS and creates a MANUAL visit if missing.
     */
    @Transactional
    public VisitResponse ensureManualStart(UUID shiftId, User admin) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        if (visitRepository.existsByShiftId(shiftId)) {
            return getByShiftId(shiftId, admin);
        }
        ShiftClaim claim = shiftClaimRepository
                .findFirstByShiftIdAndStatusIn(shiftId, EnumSet.of(ShiftClaimStatus.CONFIRMED))
                .orElseThrow(() -> new ConflictException(
                        "A confirmed caregiver assignment is required to start the shift"));
        Visit visit = Visit.builder()
                .shiftId(shiftId)
                .claimId(claim.getId())
                .caregiverProfileId(claim.getCaregiverProfile().getId())
                .clockInAt(Instant.now())
                .method(ClockMethod.MANUAL)
                .notes("Started by agency")
                .build();
        visit = visitRepository.save(visit);
        auditLogService.record(admin, AuditAction.CAREGIVER_CLOCKED_IN, "VISIT",
                visit.getId(), shift.getClientProfileId(),
                "shift=%s method=MANUAL adminStart=true".formatted(shiftId));
        return toResponse(visit);
    }

    @Transactional(readOnly = true)
    public boolean hasAttendanceEvidence(UUID shiftId) {
        return visitRepository.findByShiftId(shiftId)
                .map(v -> v.getClockInAt() != null || v.isClientArrivalConfirmed())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public VisitResponse getByShiftId(UUID shiftId, User actor) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        authorizeView(shift, actor);
        return visitRepository.findByShiftId(shiftId)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<UUID, VisitResponse> mapByShiftIds(List<UUID> shiftIds) {
        if (shiftIds == null || shiftIds.isEmpty()) {
            return Map.of();
        }
        return visitRepository.findByShiftIdIn(shiftIds).stream()
                .map(this::toResponse)
                .collect(Collectors.toMap(VisitResponse::shiftId, Function.identity(), (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public VisitResponse findOptionalByShiftId(UUID shiftId) {
        return visitRepository.findByShiftId(shiftId).map(this::toResponse).orElse(null);
    }

    private void authorizeView(Shift shift, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            if (shift.getClientProfileId() == null || !shift.getClientProfileId().equals(client.getId())) {
                throw new AccessDeniedException("Not your shift");
            }
            return;
        }
        if (actor.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            boolean owns = facility.getId().equals(shift.getFacilityProfileId())
                    || (shift.getFacilityProfileId() == null
                    && shift.getClientProfileId() == null
                    && actor.getId().equals(shift.getCreatedBy()));
            if (!owns) {
                throw new AccessDeniedException("Not your facility's shift");
            }
            return;
        }
        if (actor.getRole() == Role.CAREGIVER) {
            CaregiverProfile caregiver = caregiverProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
            boolean assigned = shiftClaimRepository
                    .findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                            shift.getId(), caregiver.getId(), ACTIVE_OR_COMPLETED)
                    .isPresent();
            if (!assigned) {
                throw new AccessDeniedException("Not your shift");
            }
            return;
        }
        throw new AccessDeniedException("Not allowed");
    }

    private VisitResponse toResponse(Visit visit) {
        CaregiverProfile cg = caregiverProfileRepository.findById(visit.getCaregiverProfileId()).orElse(null);
        return new VisitResponse(
                visit.getId(),
                visit.getShiftId(),
                visit.getClaimId(),
                visit.getCaregiverProfileId(),
                cg != null ? cg.getFirstName() : null,
                cg != null ? cg.getLastName() : null,
                visit.getClockInAt(),
                visit.getClockInLat(),
                visit.getClockInLng(),
                visit.getClockOutAt(),
                visit.getClockOutLat(),
                visit.getClockOutLng(),
                visit.getMethod(),
                visit.isClientArrivalConfirmed(),
                visit.getClientArrivalConfirmedAt(),
                visit.getNotes());
    }
}
