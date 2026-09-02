package com.okaynow.shifts.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.service.AuditLogService;
import com.okaynow.booking.domain.ClaimSource;
import com.okaynow.booking.domain.ShiftClaim;
import com.okaynow.booking.domain.ShiftClaimStatus;
import com.okaynow.booking.dto.AssignedCaregiverResponse;
import com.okaynow.booking.repository.ShiftClaimRepository;
import com.okaynow.booking.service.BookingService;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.evv.support.ShiftWindows;
import com.okaynow.marketplace.service.MarketplaceEligibilityService;
import com.okaynow.notifications.domain.NotificationType;
import com.okaynow.notifications.service.ShiftEventPublisher;
import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.service.AgencySettingsService;
import com.okaynow.payroll.service.SettlementService;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.DayPeriod;
import com.okaynow.shifts.domain.ShiftScheduleType;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.dto.CreateShiftRequest;
import com.okaynow.shifts.dto.CreateShiftResponse;
import com.okaynow.shifts.dto.ShiftResponse;
import com.okaynow.shifts.dto.ShiftResponses;
import com.okaynow.shifts.dto.UpdateShiftRequest;
import com.okaynow.shifts.mapper.ShiftMapper;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.shifts.repository.ShiftSpecifications;
import com.okaynow.staffing.domain.AssignmentType;
import com.okaynow.staffing.dto.ClientCaregiverAssignmentResponse;
import com.okaynow.staffing.service.ClientStaffingService;
import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Qualification;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.CaregiverProfileRepository;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShiftService {

    /** Max span for legacy bounded daily routines that still send an endDate. */
    private static final int MAX_DAILY_ROUTINE_DAYS = 90;
    /** Rolling window seeded (and extended from the calendar) for open-ended routines. */
    private static final int OPEN_ENDED_HORIZON_DAYS = 42;

    private final ShiftRepository shiftRepository;
    private final ShiftMapper shiftMapper;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final CaregiverProfileRepository caregiverProfileRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final AuditLogService auditLogService;
    private final SettlementService settlementService;
    private final ShiftEventPublisher shiftEventPublisher;
    private final ServiceRegionService serviceRegionService;
    private final AgencySettingsService agencySettingsService;
    private final ClientStaffingService clientStaffingService;
    private final BookingService bookingService;
    private final MarketplaceEligibilityService marketplaceEligibilityService;

    @Transactional
    public CreateShiftResponse create(CreateShiftRequest request, User actor) {
        if (request.endTime().equals(request.startTime())) {
            throw new BadRequestException("endTime must differ from startTime");
        }

        ClientProfile client = null;
        FacilityProfile facility = null;
        UUID clientId = null;
        UUID facilityId = null;
        String addressLine;
        String city;
        String state;
        String zip;
        Double lat;
        Double lng;

        if (actor.getRole() == Role.CLIENT) {
            if (request.facilityProfileId() != null) {
                throw new BadRequestException("Families cannot post facility shifts");
            }
            client = clientProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
            if (!client.isCanCreateShifts()) {
                throw new AccessDeniedException("You do not have permission to create shifts");
            }
            if (client.getAddressLine() == null || client.getAddressLine().isBlank()
                    || client.getCity() == null || client.getCity().isBlank()
                    || client.getZip() == null || client.getZip().isBlank()) {
                throw new BadRequestException("Complete your service address before posting shifts");
            }
            clientId = client.getId();
            addressLine = client.getAddressLine();
            city = client.getCity();
            state = client.getState();
            zip = client.getZip();
            lat = client.getLat();
            lng = client.getLng();
        } else if (actor.getRole() == Role.FACILITY) {
            if (request.clientProfileId() != null) {
                throw new BadRequestException(
                        "Facilities cannot attach family/client profiles — post coverage for your own site only");
            }
            facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            facilityId = facility.getId();
            addressLine = blankToNull(request.addressLine()) != null
                    ? request.addressLine() : facility.getAddressLine();
            city = blankToNull(request.city()) != null ? request.city() : facility.getCity();
            state = blankToNull(request.state()) != null
                    ? request.state()
                    : (facility.getState() != null ? facility.getState() : "MA");
            zip = blankToNull(request.zip()) != null ? request.zip() : facility.getZip();
            lat = request.lat() != null ? request.lat() : facility.getLat();
            lng = request.lng() != null ? request.lng() : facility.getLng();
            if (blankToNull(addressLine) == null || blankToNull(city) == null || blankToNull(zip) == null) {
                throw new BadRequestException("Facility site address is required");
            }
        } else if (actor.getRole() == Role.ADMIN) {
            if (request.clientProfileId() != null && request.facilityProfileId() != null) {
                throw new BadRequestException("A shift cannot belong to both a family client and a facility");
            }
            if (request.clientProfileId() != null) {
                client = clientProfileRepository.findById(request.clientProfileId())
                        .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
                if (client.getAddressLine() == null || client.getAddressLine().isBlank()
                        || client.getCity() == null || client.getCity().isBlank()
                        || client.getZip() == null || client.getZip().isBlank()) {
                    throw new BadRequestException("The selected client must have a complete service address");
                }
                clientId = client.getId();
                addressLine = client.getAddressLine();
                city = client.getCity();
                state = client.getState();
                zip = client.getZip();
                lat = client.getLat();
                lng = client.getLng();
            } else if (request.facilityProfileId() != null) {
                facility = facilityProfileRepository.findById(request.facilityProfileId())
                        .orElseThrow(() -> new ResourceNotFoundException("Facility not found"));
                facilityId = facility.getId();
                addressLine = blankToNull(request.addressLine()) != null
                        ? request.addressLine() : facility.getAddressLine();
                city = blankToNull(request.city()) != null ? request.city() : facility.getCity();
                state = blankToNull(request.state()) != null
                        ? request.state()
                        : (facility.getState() != null ? facility.getState() : "MA");
                zip = blankToNull(request.zip()) != null ? request.zip() : facility.getZip();
                lat = request.lat() != null ? request.lat() : facility.getLat();
                lng = request.lng() != null ? request.lng() : facility.getLng();
            } else {
                addressLine = request.addressLine();
                city = request.city();
                state = request.state() != null ? request.state() : "MA";
                zip = request.zip();
                lat = request.lat();
                lng = request.lng();
            }
        } else {
            throw new AccessDeniedException("Only families, facilities, or admins can post shifts");
        }

        BigDecimal billRate;
        BigDecimal payRate;
        if (actor.getRole() == Role.CLIENT || actor.getRole() == Role.FACILITY) {
            // Clients use agency-configured caregiver pay; bill is derived from take %.
            AgencySettings settings = agencySettingsService.getOrCreate();
            payRate = settings.getDefaultPayRate().setScale(2, java.math.RoundingMode.HALF_UP);
            try {
                billRate = settings.billRateFromPayRate(payRate);
            } catch (IllegalStateException ex) {
                throw new BadRequestException(ex.getMessage());
            }
        } else {
            if (request.billRate() == null || request.payRate() == null) {
                throw new BadRequestException("payRate and billRate are required when posting as admin");
            }
            billRate = request.billRate();
            payRate = request.payRate();
            if (billRate.compareTo(payRate) < 0) {
                throw new BadRequestException("billRate must be greater than or equal to payRate");
            }
        }

        var region = serviceRegionService.validate(state, zip);
        state = region.state();
        zip = region.zip();

        ShiftScheduleType scheduleType = request.scheduleType() == null
                ? ShiftScheduleType.ONE_OFF
                : request.scheduleType();
        boolean openEnded = scheduleType == ShiftScheduleType.DAILY_ROUTINE
                && request.endDate() == null;
        List<LocalDate> dates = expandDates(scheduleType, request.date(), request.endDate());
        UUID seriesId = scheduleType == ShiftScheduleType.DAILY_ROUTINE ? UUID.randomUUID() : null;

        List<Shift> created = new ArrayList<>();
        for (LocalDate day : dates) {
            created.add(Shift.builder()
                    .clientProfileId(clientId)
                    .facilityProfileId(facilityId)
                    .requiredQualification(request.requiredQualification())
                    .date(day)
                    .startTime(request.startTime())
                    .endTime(request.endTime())
                    .addressLine(addressLine)
                    .city(city)
                    .state(state)
                    .zip(zip)
                    .lat(lat)
                    .lng(lng)
                    .payRate(payRate)
                    .billRate(billRate)
                    .status(ShiftStatus.DRAFT)
                    .scheduleType(scheduleType)
                    .seriesId(seriesId)
                    .openEnded(openEnded)
                    .notes(request.notes())
                    .requiredHeadcount(request.requiredHeadcount() == null
                            ? 1
                            : Math.max(1, request.requiredHeadcount()))
                    .filledSlots(0)
                    .createdBy(actor.getId())
                    .build());
        }

        int skippedOverlapCount = 0;
        if (scheduleType == ShiftScheduleType.DAILY_ROUTINE) {
            // Skip individual days that collide with an existing shift; keep the rest.
            List<Shift> kept = new ArrayList<>();
            for (Shift draft : created) {
                if (hasOwnerTimeOverlap(draft, null, seriesId)) {
                    skippedOverlapCount++;
                    continue;
                }
                kept.add(draft);
            }
            created = kept;
            if (created.isEmpty()) {
                throw new ConflictException(
                        skippedOverlapCount == 1
                                ? "Every day in this daily schedule overlaps an existing shift"
                                : "Every day in this daily schedule overlaps existing shifts ("
                                        + skippedOverlapCount + " days)");
            }
            assertNoOverlapsInBatch(created);
        } else {
            assertNoOverlapsInBatch(created);
            for (Shift draft : created) {
                assertNoOwnerTimeOverlap(draft, null, seriesId);
            }
        }
        created = shiftRepository.saveAll(created);

        // Open-ended daily routines fill from roster by default.
        boolean assignRoster = request.assignFromRoster() != null
                ? Boolean.TRUE.equals(request.assignFromRoster())
                : openEnded;
        if (assignRoster && clientId != null) {
            assignRosterCaregivers(created, clientId, actor);
            // Reload so filledSlots/status reflect roster assignments.
            created = created.stream()
                    .map(s -> shiftRepository.findById(s.getId()).orElse(s))
                    .toList();
        }

        if (actor.getRole() == Role.CLIENT) {
            for (Shift shift : created) {
                auditLogService.record(actor, AuditAction.CLIENT_SHIFT_CREATED, "SHIFT",
                        shift.getId(), shift.getClientProfileId(),
                        scheduleType == ShiftScheduleType.DAILY_ROUTINE
                                ? "Created daily routine shift day"
                                : "Created shift");
                shiftEventPublisher.publish(
                        NotificationType.SHIFT_DRAFT_CREATED,
                        shift,
                        null,
                        "New shift draft",
                        "A client created a draft shift for " + shift.getDate()
                                + " — publish it to open the marketplace.");
            }
        }

        return new CreateShiftResponse(
                scheduleType,
                seriesId,
                created.size(),
                skippedOverlapCount,
                created.stream()
                        .map(shift -> ShiftResponses.forViewer(shiftMapper.toResponse(shift), actor.getRole()))
                        .toList());
    }

    /**
     * Fill each day from the client's roster (PRIMARY first). Days where no
     * eligible caregiver is free stay empty — client can later request replacement.
     */
    private void assignRosterCaregivers(List<Shift> shifts, UUID clientProfileId, User actor) {
        List<ClientCaregiverAssignmentResponse> roster =
                clientStaffingService.listForClient(clientProfileId).stream()
                        .sorted(Comparator.comparingInt(a ->
                                a.assignmentType() == AssignmentType.PRIMARY ? 0 : 1))
                        .toList();
        if (roster.isEmpty()) {
            return;
        }
        for (Shift shift : shifts) {
            int needed = Math.max(1, shift.getRequiredHeadcount());
            for (ClientCaregiverAssignmentResponse row : roster) {
                Shift fresh = shiftRepository.findById(shift.getId()).orElse(shift);
                if (fresh.getFilledSlots() >= needed) {
                    break;
                }
                if (row.qualifications() == null
                        || !row.qualifications().contains(fresh.getRequiredQualification())) {
                    continue;
                }
                try {
                    bookingService.assign(fresh.getId(), row.caregiverProfileId(), actor.getEmail());
                } catch (ConflictException | BadRequestException ignored) {
                    // Overlap, jurisdiction, or already filled — try next roster CG / next day.
                }
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<LocalDate> expandDates(
            ShiftScheduleType scheduleType, LocalDate start, LocalDate endDate) {
        if (scheduleType == ShiftScheduleType.ONE_OFF) {
            if (start == null) {
                throw new BadRequestException("date is required for one-off shifts");
            }
            return List.of(start);
        }
        // Open-ended daily routine: no end date — seed a rolling horizon from today (or start).
        if (endDate == null) {
            LocalDate from = start != null ? start : LocalDate.now();
            if (from.isBefore(LocalDate.now())) {
                from = LocalDate.now();
            }
            LocalDate to = from.plusDays(OPEN_ENDED_HORIZON_DAYS - 1L);
            List<LocalDate> dates = new ArrayList<>(OPEN_ENDED_HORIZON_DAYS);
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                dates.add(d);
            }
            return dates;
        }
        if (start == null) {
            throw new BadRequestException("date is required when endDate is set");
        }
        if (endDate.isBefore(start)) {
            throw new BadRequestException("endDate must be on or after the start date");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, endDate) + 1;
        if (days > MAX_DAILY_ROUTINE_DAYS) {
            throw new BadRequestException(
                    "Daily routine series cannot exceed " + MAX_DAILY_ROUTINE_DAYS + " days");
        }
        List<LocalDate> dates = new ArrayList<>((int) days);
        for (LocalDate d = start; !d.isAfter(endDate); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }

    /**
     * Materialize missing day instances for open-ended daily routines in
     * {@code [from, to]} (from today forward), then auto-fill from the client roster.
     * Called when loading the schedule calendar so coverage rolls forward without
     * the client setting an end date.
     */
    @Transactional
    public void ensureOpenEndedCoverage(
            LocalDate from,
            LocalDate to,
            UUID clientProfileId,
            UUID facilityProfileId,
            User actor) {
        LocalDate today = LocalDate.now();
        LocalDate rangeStart = from.isBefore(today) ? today : from;
        LocalDate rangeEnd = to;
        LocalDate horizonEnd = today.plusDays(OPEN_ENDED_HORIZON_DAYS - 1L);
        if (horizonEnd.isAfter(rangeEnd)) {
            rangeEnd = horizonEnd;
        }
        if (rangeEnd.isBefore(rangeStart)) {
            return;
        }

        List<UUID> seriesIds;
        if (actor.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
            seriesIds = shiftRepository.findOpenEndedSeriesIdsForFacility(
                    facility.getId(), actor.getId());
        } else if (actor.getRole() == Role.CLIENT) {
            seriesIds = shiftRepository.findOpenEndedSeriesIds(clientProfileId, null);
        } else if (actor.getRole() == Role.ADMIN) {
            seriesIds = shiftRepository.findOpenEndedSeriesIds(clientProfileId, facilityProfileId);
        } else {
            return;
        }
        if (seriesIds.isEmpty()) {
            return;
        }

        for (UUID seriesId : seriesIds) {
            Shift template = shiftRepository
                    .findFirstBySeriesIdAndOpenEndedTrueOrderByDateDesc(seriesId)
                    .orElse(null);
            if (template == null) {
                continue;
            }
            List<Shift> created = new ArrayList<>();
            for (LocalDate d = rangeStart; !d.isAfter(rangeEnd); d = d.plusDays(1)) {
                if (shiftRepository.existsBySeriesIdAndDate(seriesId, d)) {
                    continue;
                }
                Shift draft = Shift.builder()
                        .clientProfileId(template.getClientProfileId())
                        .facilityProfileId(template.getFacilityProfileId())
                        .requiredQualification(template.getRequiredQualification())
                        .date(d)
                        .startTime(template.getStartTime())
                        .endTime(template.getEndTime())
                        .addressLine(template.getAddressLine())
                        .city(template.getCity())
                        .state(template.getState())
                        .zip(template.getZip())
                        .lat(template.getLat())
                        .lng(template.getLng())
                        .payRate(template.getPayRate())
                        .billRate(template.getBillRate())
                        .status(ShiftStatus.DRAFT)
                        .scheduleType(ShiftScheduleType.DAILY_ROUTINE)
                        .seriesId(seriesId)
                        .openEnded(true)
                        .notes(template.getNotes())
                        .requiredHeadcount(Math.max(1, template.getRequiredHeadcount()))
                        .filledSlots(0)
                        .createdBy(template.getCreatedBy())
                        .build();
                if (hasOwnerTimeOverlap(draft, null, seriesId)) {
                    // Another shift already occupies this window — skip this day.
                    continue;
                }
                created.add(draft);
            }
            if (created.isEmpty()) {
                continue;
            }
            created = shiftRepository.saveAll(created);
            if (template.getClientProfileId() != null) {
                assignRosterCaregivers(created, template.getClientProfileId(), actor);
            }
        }
    }

    @Transactional(readOnly = true)
    public ShiftResponse getById(UUID id, User actor) {
        Shift shift = findById(id);
        authorizeView(shift, actor);
        return ShiftResponses.forViewer(shiftMapper.toResponse(shift), actor.getRole());
    }

    /**
     * Confirmed/assigned caregivers for a shift — limited profile fields for the
     * family or facility that owns the shift (not full caregiver PII).
     */
    @Transactional(readOnly = true)
    public List<AssignedCaregiverResponse> assignedCaregivers(UUID shiftId, User actor) {
        if (actor.getRole() != Role.CLIENT
                && actor.getRole() != Role.FACILITY
                && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only clients, facilities, and admins can view assigned caregivers");
        }
        Shift shift = findById(shiftId);
        authorizeView(shift, actor);
        EnumSet<ShiftClaimStatus> visible = EnumSet.of(
                ShiftClaimStatus.PENDING,
                ShiftClaimStatus.CONFIRMED,
                ShiftClaimStatus.COMPLETED);
        return shiftClaimRepository.findByShiftIdOrderByClaimedAtDesc(shiftId).stream()
                .filter(c -> visible.contains(c.getStatus()))
                .map(this::toAssignedCaregiver)
                .toList();
    }

    private AssignedCaregiverResponse toAssignedCaregiver(ShiftClaim claim) {
        CaregiverProfile caregiver = claim.getCaregiverProfile();
        User caregiverUser = caregiver.getUser();
        java.util.Set<Qualification> quals = caregiver.getQualifications() == null
                ? java.util.Set.of()
                : java.util.Set.copyOf(caregiver.getQualifications());
        return new AssignedCaregiverResponse(
                claim.getId(),
                caregiver.getId(),
                caregiver.getFirstName(),
                caregiver.getLastName(),
                caregiverUser != null ? caregiverUser.getPhone() : null,
                quals,
                caregiver.getRatingAvg(),
                caregiver.getRatingCount() != null ? caregiver.getRatingCount() : 0,
                caregiver.getProfilePhotoUrl(),
                claim.getStatus(),
                claim.getSource() != null ? claim.getSource() : ClaimSource.MARKETPLACE);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ShiftResponse> search(ShiftStatus status, Qualification qualification,
                                               LocalDate dateFrom, LocalDate dateTo,
                                               UUID requestedClientProfileId,
                                               UUID requestedFacilityProfileId,
                                               BigDecimal minPay, BigDecimal maxPay,
                                               DayPeriod dayPeriod,
                                               Pageable pageable, User actor) {
        if (minPay != null && maxPay != null && minPay.compareTo(maxPay) > 0) {
            throw new BadRequestException("minPay must be less than or equal to maxPay");
        }
        if ((requestedClientProfileId != null || requestedFacilityProfileId != null)
                && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only platform owners can filter by client");
        }

        UUID clientProfileId = requestedClientProfileId;
        UUID facilityProfileId = requestedFacilityProfileId;
        org.springframework.data.jpa.domain.Specification<Shift> ownership = null;

        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfile(actor);
            if (!client.isCanViewShifts()) {
                throw new AccessDeniedException("You do not have permission to view shifts");
            }
            clientProfileId = client.getId();
            facilityProfileId = null;
        } else if (actor.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfile(actor);
            ownership = ShiftSpecifications.ownedByFacility(facility.getId(), actor.getId());
            clientProfileId = null;
            facilityProfileId = null;
        }

        // Caregivers only browse the public OPEN board here. Assigned/private shifts
        // are accessed via My shifts (claims), not the marketplace list.
        ShiftStatus effectiveStatus = status;
        CaregiverProfile caregiverJurisdiction = null;
        LocalDate effectiveDateFrom = dateFrom;
        if (actor.getRole() == Role.CAREGIVER) {
            if (effectiveStatus != null && effectiveStatus != ShiftStatus.OPEN) {
                throw new AccessDeniedException(
                        "Caregivers can only browse OPEN marketplace shifts; open My shifts for assignments");
            }
            effectiveStatus = ShiftStatus.OPEN;
            caregiverJurisdiction = caregiverProfileRepository.findByUserId(actor.getId()).orElse(null);
            LocalDate today = LocalDate.now(ShiftWindows.ZONE);
            if (effectiveDateFrom == null || effectiveDateFrom.isBefore(today)) {
                effectiveDateFrom = today;
            }
        }

        String rateField = (actor.getRole() == Role.CLIENT || actor.getRole() == Role.FACILITY)
                ? "billRate"
                : "payRate";
        org.springframework.data.jpa.domain.Specification<Shift> filters =
                ShiftSpecifications.withFilters(
                        effectiveStatus, qualification, effectiveDateFrom, dateTo, clientProfileId,
                        facilityProfileId, minPay, maxPay, rateField, dayPeriod);
        // Owners' boards: drafts stay off the default list until released.
        // Explicit status=DRAFT still returns them. HELD remains visible.
        if ((actor.getRole() == Role.ADMIN
                || actor.getRole() == Role.CLIENT
                || actor.getRole() == Role.FACILITY)
                && effectiveStatus == null) {
            filters = filters.and((root, query, cb) ->
                    cb.notEqual(root.get("status"), ShiftStatus.DRAFT));
        }
        if (ownership != null) {
            filters = filters.and(ownership);
        }

        Page<Shift> page = shiftRepository.findAll(filters, pageable);

        if (caregiverJurisdiction != null) {
            CaregiverProfile caregiver = caregiverJurisdiction;
            List<Shift> filtered = page.getContent().stream()
                    .filter(shift -> shift.getAgencyId() == null)
                    .filter(shift -> marketplaceEligibilityService.isEligible(caregiver, shift))
                    .toList();
            page = new PageImpl<>(filtered, pageable, filtered.size());
        }

        return PagedResponse.from(page.map(shift ->
                ShiftResponses.forViewer(shiftMapper.toResponse(shift), actor.getRole())));
    }

    @Transactional
    public ShiftResponse update(UUID id, UpdateShiftRequest request, User actor) {
        Shift shift = findById(id);
        ClientProfile client = authorizeMutation(shift, actor, Mutation.UPDATE);
        assertEditable(shift);
        if (request.requiredQualification() != null) shift.setRequiredQualification(request.requiredQualification());
        if (request.date() != null) shift.setDate(request.date());
        if (request.startTime() != null) shift.setStartTime(request.startTime());
        if (request.endTime() != null) shift.setEndTime(request.endTime());
        if (request.addressLine() != null) shift.setAddressLine(request.addressLine());
        if (request.city() != null) shift.setCity(request.city());
        if (request.state() != null) shift.setState(request.state());
        if (request.zip() != null) shift.setZip(request.zip());
        if (request.lat() != null) shift.setLat(request.lat());
        if (request.lng() != null) shift.setLng(request.lng());
        if (actor.getRole() == Role.ADMIN) {
            if (request.payRate() != null) shift.setPayRate(request.payRate());
            if (request.billRate() != null) shift.setBillRate(request.billRate());
        }
        if (request.notes() != null) shift.setNotes(request.notes());

        if (request.state() != null || request.zip() != null) {
            var region = serviceRegionService.validate(shift.getState(), shift.getZip());
            shift.setState(region.state());
            shift.setZip(region.zip());
        }

        if (shift.getEndTime().equals(shift.getStartTime())) {
            throw new BadRequestException("endTime must differ from startTime");
        }
        if (shift.getBillRate().compareTo(shift.getPayRate()) < 0) {
            throw new BadRequestException("billRate must be greater than or equal to payRate");
        }

        assertNoOwnerTimeOverlap(shift, shift.getId(), shift.getSeriesId());

        if (actor.getRole() == Role.CLIENT && client != null) {
            auditLogService.record(actor, AuditAction.CLIENT_SHIFT_UPDATED, "SHIFT",
                    shift.getId(), client.getId(), "Updated shift");
        }

        // Keep open-ended daily routines uniform: mirror hours/location/qual onto
        // other future days in the same series. Skip a sibling day if the new
        // window would overlap another existing shift that day.
        if (shift.getScheduleType() == ShiftScheduleType.DAILY_ROUTINE
                && shift.isOpenEnded()
                && shift.getSeriesId() != null) {
            syncOpenEndedSeries(shift);
        }

        return ShiftResponses.forViewer(shiftMapper.toResponse(shift), actor.getRole());
    }

    private void syncOpenEndedSeries(Shift template) {
        LocalDate today = LocalDate.now();
        List<Shift> siblings = shiftRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("seriesId"), template.getSeriesId()),
                        cb.notEqual(root.get("id"), template.getId()),
                        cb.greaterThanOrEqualTo(root.get("date"), today),
                        cb.notEqual(root.get("status"), ShiftStatus.CANCELLED),
                        cb.notEqual(root.get("status"), ShiftStatus.COMPLETED),
                        cb.notEqual(root.get("status"), ShiftStatus.NO_SHOW),
                        cb.notEqual(root.get("status"), ShiftStatus.IN_PROGRESS)));
        for (Shift sibling : siblings) {
            LocalTime prevStart = sibling.getStartTime();
            LocalTime prevEnd = sibling.getEndTime();
            Qualification prevQual = sibling.getRequiredQualification();
            String prevAddress = sibling.getAddressLine();
            String prevCity = sibling.getCity();
            String prevState = sibling.getState();
            String prevZip = sibling.getZip();
            Double prevLat = sibling.getLat();
            Double prevLng = sibling.getLng();
            String prevNotes = sibling.getNotes();
            int prevHeadcount = sibling.getRequiredHeadcount();

            sibling.setRequiredQualification(template.getRequiredQualification());
            sibling.setStartTime(template.getStartTime());
            sibling.setEndTime(template.getEndTime());
            sibling.setAddressLine(template.getAddressLine());
            sibling.setCity(template.getCity());
            sibling.setState(template.getState());
            sibling.setZip(template.getZip());
            sibling.setLat(template.getLat());
            sibling.setLng(template.getLng());
            sibling.setNotes(template.getNotes());
            sibling.setRequiredHeadcount(template.getRequiredHeadcount());

            if (hasOwnerTimeOverlap(sibling, sibling.getId(), template.getSeriesId())) {
                // Leave this day alone — another shift already occupies the new window.
                sibling.setRequiredQualification(prevQual);
                sibling.setStartTime(prevStart);
                sibling.setEndTime(prevEnd);
                sibling.setAddressLine(prevAddress);
                sibling.setCity(prevCity);
                sibling.setState(prevState);
                sibling.setZip(prevZip);
                sibling.setLat(prevLat);
                sibling.setLng(prevLng);
                sibling.setNotes(prevNotes);
                sibling.setRequiredHeadcount(prevHeadcount);
            }
        }
    }

    /**
     * Rejects when this shift's window overlaps another active shift for the same
     * family or facility. Adjacent times (one ends when the other starts) are allowed.
     */
    private void assertNoOwnerTimeOverlap(
            Shift candidate, UUID excludeShiftId, UUID excludeSeriesId) {
        Shift overlapping = findOverlappingOwnerShift(candidate, excludeShiftId, excludeSeriesId);
        if (overlapping != null) {
            throw new ConflictException(
                    "Shift times overlap an existing shift on "
                            + overlapping.getDate()
                            + " ("
                            + overlapping.getStartTime()
                            + "–"
                            + overlapping.getEndTime()
                            + ")");
        }
    }

    private boolean hasOwnerTimeOverlap(
            Shift candidate, UUID excludeShiftId, UUID excludeSeriesId) {
        return findOverlappingOwnerShift(candidate, excludeShiftId, excludeSeriesId) != null;
    }

    private Shift findOverlappingOwnerShift(
            Shift candidate, UUID excludeShiftId, UUID excludeSeriesId) {
        UUID clientId = candidate.getClientProfileId();
        UUID facilityId = candidate.getFacilityProfileId();
        if (clientId == null && facilityId == null) {
            return null;
        }
        LocalDate from = candidate.getDate().minusDays(1);
        LocalDate to = candidate.getDate().plusDays(1);
        List<Shift> nearby = shiftRepository.findActiveForOwnerBetween(
                clientId, facilityId, from, to);
        for (Shift other : nearby) {
            if (excludeShiftId != null && excludeShiftId.equals(other.getId())) {
                continue;
            }
            if (excludeSeriesId != null
                    && excludeSeriesId.equals(other.getSeriesId())) {
                continue;
            }
            if (ShiftWindows.overlaps(candidate, other)) {
                return other;
            }
        }
        return null;
    }

    private void assertNoOverlapsInBatch(List<Shift> drafts) {
        for (int i = 0; i < drafts.size(); i++) {
            for (int j = i + 1; j < drafts.size(); j++) {
                if (ShiftWindows.overlaps(drafts.get(i), drafts.get(j))) {
                    throw new ConflictException(
                            "Shift times overlap within this schedule ("
                                    + drafts.get(i).getDate()
                                    + " and "
                                    + drafts.get(j).getDate()
                                    + ")");
                }
            }
        }
    }

    @Transactional
    public void delete(UUID id, User actor) {
        Shift shift = findById(id);
        ClientProfile client = authorizeMutation(shift, actor, Mutation.DELETE);
        assertDeletable(shift);

        List<ShiftClaim> claims = shiftClaimRepository.findByShiftIdOrderByClaimedAtDesc(id);
        Instant now = Instant.now();
        for (ShiftClaim claim : claims) {
            if (claim.getStatus() == ShiftClaimStatus.PENDING
                    || claim.getStatus() == ShiftClaimStatus.CONFIRMED) {
                claim.setStatus(ShiftClaimStatus.CANCELLED);
                claim.setReleasedAt(now);
                claim.setCancelReason("Shift removed from schedule");
            }
        }

        if (claims.isEmpty() && !shift.isMarketplacePosted()) {
            shiftRepository.delete(shift);
        } else {
            shift.setStatus(ShiftStatus.CANCELLED);
            shift.setMarketplacePosted(false);
            shift.setFilledSlots(0);
        }

        if (actor.getRole() == Role.CLIENT && client != null) {
            auditLogService.record(actor, AuditAction.CLIENT_SHIFT_DELETED, "SHIFT",
                    shift.getId(), client.getId(), "Deleted shift from schedule");
        }
    }

    private void assertEditable(Shift shift) {
        assertNotPast(shift);
        assertNotClaimedOrInProgress(shift);
        if (shift.getStatus() == ShiftStatus.CANCELLED) {
            throw new BadRequestException("Cancelled shifts cannot be edited; reopen them first");
        }
    }

    private void assertDeletable(Shift shift) {
        assertNotPast(shift);
        assertNotClaimedOrInProgress(shift);
    }

    private void assertNotPast(Shift shift) {
        if (shift.getDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Past shifts are history only and cannot be edited or deleted");
        }
    }

    private void assertNotClaimedOrInProgress(Shift shift) {
        ShiftStatus status = shift.getStatus();
        if (status == ShiftStatus.CLAIMED
                || status == ShiftStatus.CONFIRMED
                || status == ShiftStatus.IN_PROGRESS
                || status == ShiftStatus.COMPLETED
                || status == ShiftStatus.NO_SHOW) {
            throw new BadRequestException(
                    "Cannot edit or delete a shift that is claimed, confirmed, in progress, completed, or marked no-show");
        }
    }

    @Transactional
    public ShiftResponse publish(UUID id, User actor) {
        if (actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only platform owners can release shifts to caregivers");
        }
        Shift shift = findById(id);
        if (shift.getStatus() != ShiftStatus.DRAFT && shift.getStatus() != ShiftStatus.HELD) {
            throw new BadRequestException("Only DRAFT or HELD shifts can be made available for claiming");
        }
        shift.setStatus(ShiftStatus.OPEN);
        shift.setMarketplacePosted(true);
        int remaining = Math.max(0, Math.max(1, shift.getRequiredHeadcount()) - shift.getFilledSlots());
        shift.setMarketplaceSlots(Math.max(1, remaining));
        auditLogService.record(actor, AuditAction.SHIFT_PUBLISHED, "SHIFT",
                shift.getId(), shift.getClientProfileId(), "Released to caregiver marketplace");
        shiftEventPublisher.publish(
                NotificationType.SHIFT_POSTED,
                shift,
                null,
                "New open shift",
                "A shift on " + shift.getDate() + " in " + shift.getCity()
                        + " is available — " + shift.getRequiredQualification()
                        + " · $" + shift.getPayRate() + "/hr.");
        return shiftMapper.toResponse(shift);
    }

    /**
     * Pull an OPEN shift to HELD (off the marketplace, not back to DRAFT).
     * Not allowed if claimed.
     */
    @Transactional
    public ShiftResponse unpublish(UUID id, User actor) {
        if (actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only platform owners can hold shifts off the board");
        }
        Shift shift = findById(id);
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new BadRequestException("Only OPEN shifts can be held off the marketplace");
        }
        if (shiftClaimRepository.findFirstByShiftIdAndStatusIn(
                id, EnumSet.of(ShiftClaimStatus.PENDING, ShiftClaimStatus.CONFIRMED)).isPresent()) {
            throw new ConflictException("Cancel the active claim before holding this shift");
        }
        shift.setStatus(ShiftStatus.HELD);
        shift.setMarketplacePosted(false);
        shift.setMarketplaceSlots(0);
        auditLogService.record(actor, AuditAction.SHIFT_UNPUBLISHED, "SHIFT",
                shift.getId(), shift.getClientProfileId(), "Held off caregiver marketplace");
        shiftEventPublisher.publish(
                NotificationType.SHIFT_HELD,
                shift,
                null,
                "Shift held off marketplace",
                "The " + shift.getDate() + " shift in " + shift.getCity()
                        + " was pulled from the open board.");
        return shiftMapper.toResponse(shift);
    }

    @Transactional
    public ShiftResponse updatePlatformPayment(UUID id, boolean platformPaid, User actor) {
        Shift shift = findById(id);
        shift.setPlatformPaid(platformPaid);
        auditLogService.record(actor, AuditAction.PLATFORM_PAYMENT_CHANGED, "SHIFT",
                shift.getId(), shift.getClientProfileId(),
                "platformPaid=" + platformPaid);
        settlementService.syncFromPlatformPaid(shift, platformPaid, actor);
        return shiftMapper.toResponse(shift);
    }

    private Shift findById(UUID id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
    }

    private void authorizeView(Shift shift, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() == Role.CAREGIVER) {
            if (shift.getStatus() == ShiftStatus.OPEN) {
                return;
            }
            CaregiverProfile caregiver = caregiverProfileRepository.findByUserId(actor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Caregiver profile not found"));
            boolean assignedToMe = shiftClaimRepository
                    .findFirstByShiftIdAndCaregiverProfileIdAndStatusIn(
                            shift.getId(),
                            caregiver.getId(),
                            java.util.EnumSet.of(
                                    ShiftClaimStatus.PENDING,
                                    ShiftClaimStatus.CONFIRMED,
                                    ShiftClaimStatus.COMPLETED))
                    .isPresent();
            if (!assignedToMe) {
                throw new AccessDeniedException(
                        "This shift is not on the open board and is not assigned to you");
            }
            return;
        }
        if (actor.getRole() == Role.CLIENT) {
            ClientProfile client = clientProfile(actor);
            if (!client.isCanViewShifts() || !client.getId().equals(shift.getClientProfileId())) {
                throw new AccessDeniedException("You do not have permission to view this shift");
            }
            return;
        }
        if (actor.getRole() == Role.FACILITY) {
            if (!ownsFacilityShift(shift, actor)) {
                throw new AccessDeniedException("You can only view shifts for your own facility");
            }
            return;
        }
        throw new AccessDeniedException("You do not have permission to view this shift");
    }

    private ClientProfile authorizeMutation(Shift shift, User actor, Mutation mutation) {
        if (actor.getRole() == Role.ADMIN) {
            return null;
        }
        if (actor.getRole() == Role.FACILITY) {
            if (!ownsFacilityShift(shift, actor)) {
                throw new AccessDeniedException("You can only manage shifts for your own facility");
            }
            return null;
        }
        if (actor.getRole() != Role.CLIENT) {
            throw new AccessDeniedException("You cannot manage this shift");
        }
        ClientProfile client = clientProfile(actor);
        if (!client.getId().equals(shift.getClientProfileId())) {
            throw new AccessDeniedException("You cannot manage another family's shift");
        }
        boolean allowed = mutation == Mutation.UPDATE
                ? client.isCanUpdateShifts()
                : client.isCanDeleteShifts();
        if (!allowed) {
            throw new AccessDeniedException("You do not have permission to "
                    + mutation.name().toLowerCase() + " shifts");
        }
        return client;
    }

    private boolean ownsFacilityShift(Shift shift, User actor) {
        FacilityProfile facility = facilityProfile(actor);
        if (facility.getId().equals(shift.getFacilityProfileId())) {
            return true;
        }
        // Legacy facility drafts created before facilityProfileId existed.
        return shift.getFacilityProfileId() == null
                && shift.getClientProfileId() == null
                && actor.getId().equals(shift.getCreatedBy());
    }

    private ClientProfile clientProfile(User actor) {
        return clientProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));
    }

    private FacilityProfile facilityProfile(User actor) {
        return facilityProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
    }

    private enum Mutation {
        UPDATE,
        DELETE
    }
}
