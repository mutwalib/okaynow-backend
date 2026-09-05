package com.okaynow.shiftrequests.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.agencies.service.AgencyShiftRoutingService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.GeocodingService;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.connections.service.HomeAgencyConnectionService;
import com.okaynow.evv.support.ShiftWindows;
import com.okaynow.payroll.domain.AgencySettings;
import com.okaynow.payroll.service.AgencySettingsService;
import com.okaynow.shiftrequests.domain.ShiftRequest;
import com.okaynow.shiftrequests.domain.ShiftRequestAgency;
import com.okaynow.shiftrequests.domain.ShiftRequestAgencyStatus;
import com.okaynow.shiftrequests.domain.ShiftRequestStatus;
import com.okaynow.shiftrequests.dto.AgencyShiftRequestInboxResponse;
import com.okaynow.shiftrequests.dto.CreateShiftRequestPayload;
import com.okaynow.shiftrequests.dto.ShiftRequestResponse;
import com.okaynow.shiftrequests.repository.ShiftRequestAgencyRepository;
import com.okaynow.shiftrequests.repository.ShiftRequestRepository;
import com.okaynow.shifts.domain.Shift;
import com.okaynow.shifts.domain.ShiftStatus;
import com.okaynow.shifts.repository.ShiftRepository;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.FacilityProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.FacilityProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShiftRequestService {

    private final ShiftRequestRepository shiftRequestRepository;
    private final ShiftRequestAgencyRepository shiftRequestAgencyRepository;
    private final HomeAgencyConnectionService connectionService;
    private final AgencyRepository agencyRepository;
    private final AgencyAccessService agencyAccessService;
    private final ClientProfileRepository clientProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final UserRepository userRepository;
    private final ServiceRegionService serviceRegionService;
    private final GeocodingService geocodingService;
    private final AgencySettingsService agencySettingsService;
    private final ShiftRepository shiftRepository;
    private final AgencyShiftRoutingService agencyShiftRoutingService;

    @Transactional
    public ShiftRequestResponse createForHome(UUID homeUserId, CreateShiftRequestPayload payload) {
        User requester = requireRequester(homeUserId);

        if (payload.endDate() != null && payload.endDate().isBefore(payload.startDate())) {
            throw new BadRequestException("End date must be on or after start date");
        }
        Set<UUID> uniqueAgencyIds = uniqueAgencyIds(payload.agencyIds());

        ShiftRequest.ShiftRequestBuilder builder = ShiftRequest.builder()
                .homeUser(requester)
                .requiredQualification(payload.requiredQualification())
                .startDate(payload.startDate())
                .endDate(payload.endDate())
                .startTime(payload.startTime())
                .endTime(payload.endTime())
                .notes(trimOrNull(payload.notes()))
                .requiredHeadcount(1)
                .status(ShiftRequestStatus.OPEN);

        if (requester.getRole() == Role.FACILITY) {
            FacilityProfile facility = facilityProfileRepository.findByUserId(homeUserId)
                    .orElseThrow(() -> new BadRequestException(
                            "Complete your facility profile before posting openings"));
            String addressLine = firstNonBlank(payload.addressLine(), facility.getAddressLine());
            String city = firstNonBlank(payload.city(), facility.getCity());
            String state = payload.state() != null ? payload.state() : facility.getState();
            String zip = firstNonBlank(payload.zip(), facility.getZip());
            if (addressLine == null || city == null || zip == null) {
                throw new BadRequestException(
                        "Address is required — update your profile or include it in the request");
            }
            var region = serviceRegionService.validate(state, zip);
            builder.facilityProfile(facility)
                    .clientProfile(null)
                    .addressLine(addressLine)
                    .city(city)
                    .state(region.state())
                    .zip(region.zip());
        } else {
            ClientProfile client = clientProfileRepository.findByUserId(homeUserId)
                    .orElseThrow(() -> new BadRequestException(
                            "Complete your home profile before posting care needs"));
            String addressLine = firstNonBlank(payload.addressLine(), client.getAddressLine());
            String city = firstNonBlank(payload.city(), client.getCity());
            String state = payload.state() != null ? payload.state() : client.getState();
            String zip = firstNonBlank(payload.zip(), client.getZip());
            if (addressLine == null || city == null || zip == null) {
                throw new BadRequestException(
                        "Address is required — update your profile or include it in the request");
            }
            var region = serviceRegionService.validate(state, zip);
            builder.clientProfile(client)
                    .facilityProfile(null)
                    .addressLine(addressLine)
                    .city(city)
                    .state(region.state())
                    .zip(region.zip());
        }

        ShiftRequest request = shiftRequestRepository.save(builder.build());
        attachTargets(request, homeUserId, uniqueAgencyIds, true);
        return toResponse(request, shiftRequestAgencyRepository.findByShiftRequestId(request.getId()));
    }

    /**
     * Facility schedule "need coverage": route the existing calendar shift to
     * exactly one connected agency. Does not open the public marketplace.
     */
    @Transactional
    public void openFromFacilityShift(
            UUID shiftId,
            User actor,
            List<UUID> agencyIds,
            String reason,
            Integer slots) {
        if (actor.getRole() != Role.FACILITY) {
            throw new BadRequestException("Only facilities can route schedule openings to agencies");
        }
        FacilityProfile facility = facilityProfileRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));
        Shift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        boolean owns = shift.getFacilityProfileId() != null
                && shift.getFacilityProfileId().equals(facility.getId());
        boolean legacy = shift.getFacilityProfileId() == null
                && shift.getClientProfileId() == null
                && actor.getId().equals(shift.getCreatedBy());
        if (!owns && !legacy) {
            throw new AccessDeniedException("Not your shift");
        }
        if (shift.getStatus() == ShiftStatus.COMPLETED
                || shift.getStatus() == ShiftStatus.CANCELLED
                || shift.getStatus() == ShiftStatus.NO_SHOW) {
            throw new ConflictException("Cannot request coverage for a " + shift.getStatus() + " shift");
        }
        if (shift.getStatus() == ShiftStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Shift is already in progress — finish or contact the agency to reassign");
        }
        if (!Instant.now().isBefore(ShiftWindows.endInstant(shift))) {
            throw new ConflictException(
                    "Cannot request coverage after the shift's scheduled end ("
                            + ShiftWindows.endLocal(shift) + " ET)");
        }

        Set<UUID> uniqueAgencyIds = uniqueAgencyIds(agencyIds);
        int remaining = Math.max(0, shift.getRequiredHeadcount() - shift.getFilledSlots());
        int requested = slots != null ? slots : Math.max(1, remaining > 0 ? remaining : shift.getRequiredHeadcount());
        String notes = trimOrNull(reason);

        ShiftRequest request = shiftRequestRepository
                .findFirstBySourceShiftIdAndStatus(shift.getId(), ShiftRequestStatus.OPEN)
                .orElse(null);
        if (request != null) {
            request.setRequiredHeadcount(Math.max(1, requested));
            if (notes != null) {
                request.setNotes(notes);
            }
            shiftRequestRepository.save(request);
            attachTargets(request, actor.getId(), uniqueAgencyIds, false);
        } else {
            var region = serviceRegionService.validate(shift.getState(), shift.getZip());
            request = shiftRequestRepository.save(ShiftRequest.builder()
                    .homeUser(actor)
                    .facilityProfile(facility)
                    .clientProfile(null)
                    .sourceShiftId(shift.getId())
                    .requiredQualification(shift.getRequiredQualification())
                    .startDate(shift.getDate())
                    .startTime(shift.getStartTime())
                    .endTime(shift.getEndTime())
                    .addressLine(shift.getAddressLine())
                    .city(shift.getCity())
                    .state(region.state())
                    .zip(region.zip())
                    .notes(notes)
                    .requiredHeadcount(Math.max(1, requested))
                    .status(ShiftRequestStatus.OPEN)
                    .build());
            attachTargets(request, actor.getId(), uniqueAgencyIds, true);
        }
        shift.setAgencyCoverageRequested(true);
        shiftRepository.save(shift);
    }

    @Transactional(readOnly = true)
    public List<ShiftRequestResponse> listForHome(UUID homeUserId) {
        requireRequester(homeUserId);
        return shiftRequestRepository.findByHomeUserIdOrderByCreatedAtDesc(homeUserId).stream()
                .map(req -> toResponse(req, shiftRequestAgencyRepository.findByShiftRequestId(req.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftRequestResponse getForHome(UUID homeUserId, UUID requestId) {
        requireRequester(homeUserId);
        ShiftRequest request = shiftRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift request not found"));
        if (!request.getHomeUser().getId().equals(homeUserId)) {
            throw new ResourceNotFoundException("Shift request not found");
        }
        return toResponse(request, shiftRequestAgencyRepository.findByShiftRequestId(requestId));
    }

    @Transactional(readOnly = true)
    public List<AgencyShiftRequestInboxResponse> inboxForAgency(UUID agencyUserId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        return shiftRequestAgencyRepository.findInboxForAgency(agency.getId()).stream()
                .map(this::toInboxResponse)
                .toList();
    }

    @Transactional
    public ShiftRequestResponse acceptForAgency(UUID agencyUserId, UUID inboxRowId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        ShiftRequestAgency row = shiftRequestAgencyRepository.findByIdAndAgencyId(inboxRowId, agency.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift request not found"));
        if (row.getStatus() != ShiftRequestAgencyStatus.PENDING) {
            throw new BadRequestException("This request was already handled");
        }
        ShiftRequest request = row.getShiftRequest();
        if (request.getStatus() != ShiftRequestStatus.OPEN) {
            throw new BadRequestException("This care need is no longer open");
        }

        AgencySettings settings = agencySettingsService.getOrCreateForAgency(agency.getId());
        var payRate = settings.getDefaultPayRate();
        var billRate = settings.billRateFromPayRate(payRate);

        Shift savedShift;
        if (request.getSourceShiftId() != null) {
            Shift source = shiftRepository.findByIdForUpdate(request.getSourceShiftId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source shift not found"));
            if (source.getAgencyId() != null && !source.getAgencyId().equals(agency.getId())) {
                throw new ConflictException("Another agency already accepted this opening");
            }
            source.setAgencyId(agency.getId());
            source.setShiftRequestId(request.getId());
            source.setPayRate(payRate);
            source.setBillRate(billRate);
            savedShift = shiftRepository.save(source);
        } else {
            LocalDate shiftDate = request.getStartDate();
            UUID clientId = request.getClientProfile() != null ? request.getClientProfile().getId() : null;
            UUID facilityId = request.getFacilityProfile() != null ? request.getFacilityProfile().getId() : null;
            Shift shift = Shift.builder()
                    .agencyId(agency.getId())
                    .shiftRequestId(request.getId())
                    .clientProfileId(clientId)
                    .facilityProfileId(facilityId)
                    .requiredQualification(request.getRequiredQualification())
                    .date(shiftDate)
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .addressLine(request.getAddressLine())
                    .city(request.getCity())
                    .state(request.getState())
                    .zip(request.getZip())
                    .payRate(payRate)
                    .billRate(billRate)
                    .status(ShiftStatus.DRAFT)
                    .marketplacePosted(false)
                    .marketplaceSlots(0)
                    .requiredHeadcount(Math.max(1, request.getRequiredHeadcount()))
                    .notes(request.getNotes())
                    .createdBy(agencyUserId)
                    .build();
            var point = geocodingService.requireGeocode(
                    shift.getAddressLine(), shift.getCity(), shift.getState(), shift.getZip());
            shift.setLat(point.lat());
            shift.setLng(point.lng());
            savedShift = shiftRepository.save(shift);
        }

        agencyShiftRoutingService.routeAfterHomeRequestAccepted(agency.getId(), savedShift);

        row.setStatus(ShiftRequestAgencyStatus.ACCEPTED);
        row.setRespondedAt(Instant.now());
        row.setCreatedShiftId(savedShift.getId());
        shiftRequestAgencyRepository.save(row);

        Instant now = Instant.now();
        for (ShiftRequestAgency other : shiftRequestAgencyRepository.findByShiftRequestId(request.getId())) {
            if (other.getId().equals(row.getId())) {
                continue;
            }
            if (other.getStatus() == ShiftRequestAgencyStatus.PENDING) {
                other.setStatus(ShiftRequestAgencyStatus.DECLINED);
                other.setRespondedAt(now);
                shiftRequestAgencyRepository.save(other);
            }
        }

        request.setStatus(ShiftRequestStatus.FULFILLED);
        shiftRequestRepository.save(request);

        return toResponse(request, shiftRequestAgencyRepository.findByShiftRequestId(request.getId()));
    }

    @Transactional
    public AgencyShiftRequestInboxResponse declineForAgency(UUID agencyUserId, UUID inboxRowId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        ShiftRequestAgency row = shiftRequestAgencyRepository.findByIdAndAgencyId(inboxRowId, agency.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift request not found"));
        if (row.getStatus() != ShiftRequestAgencyStatus.PENDING) {
            throw new BadRequestException("This request was already handled");
        }
        row.setStatus(ShiftRequestAgencyStatus.DECLINED);
        row.setRespondedAt(Instant.now());
        return toInboxResponse(shiftRequestAgencyRepository.save(row));
    }

    private User requireRequester(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.CLIENT && user.getRole() != Role.FACILITY) {
            throw new BadRequestException("Only home and facility accounts can manage shift requests");
        }
        return user;
    }

    private Set<UUID> uniqueAgencyIds(List<UUID> agencyIds) {
        if (agencyIds == null || agencyIds.isEmpty()) {
            throw new BadRequestException("Select exactly one connected agency");
        }
        var unique = new HashSet<>(agencyIds);
        if (unique.size() != agencyIds.size()) {
            throw new BadRequestException("Duplicate agencies in request");
        }
        if (unique.size() != 1) {
            throw new BadRequestException(
                    "Send each opening to exactly one agency. Connect with another agency for a separate request.");
        }
        return unique;
    }

    private void attachTargets(
            ShiftRequest request,
            UUID requesterUserId,
            Set<UUID> agencyIds,
            boolean requireAtLeastOneNew) {
        Set<UUID> already = shiftRequestAgencyRepository.findByShiftRequestId(request.getId()).stream()
                .map(row -> row.getAgency().getId())
                .collect(Collectors.toSet());
        List<UUID> toAdd = agencyIds.stream()
                .filter(id -> !already.contains(id))
                .toList();
        if (toAdd.isEmpty() && requireAtLeastOneNew) {
            throw new BadRequestException("Select exactly one connected agency");
        }
        if (toAdd.isEmpty()) {
            throw new BadRequestException("Already sent to the selected agency");
        }
        for (UUID agencyId : toAdd) {
            if (!connectionService.hasActiveConnection(requesterUserId, agencyId)) {
                throw new BadRequestException("You must be connected to the selected agency");
            }
        }
        for (UUID agencyId : toAdd) {
            Agency agency = agencyRepository.findById(agencyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
            shiftRequestAgencyRepository.save(ShiftRequestAgency.builder()
                    .shiftRequest(request)
                    .agency(agency)
                    .status(ShiftRequestAgencyStatus.PENDING)
                    .build());
        }
    }

    private ShiftRequestResponse toResponse(ShiftRequest request, List<ShiftRequestAgency> targets) {
        return new ShiftRequestResponse(
                request.getId(),
                request.getStatus(),
                request.getRequiredQualification(),
                request.getStartDate(),
                request.getEndDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.getAddressLine(),
                request.getCity(),
                request.getState(),
                request.getZip(),
                request.getNotes(),
                request.getCreatedAt(),
                targets.stream()
                        .map(t -> new ShiftRequestResponse.TargetAgencyResponse(
                                t.getAgency().getId(),
                                t.getAgency().getDisplayName(),
                                t.getStatus(),
                                t.getCreatedShiftId()))
                        .toList());
    }

    private AgencyShiftRequestInboxResponse toInboxResponse(ShiftRequestAgency row) {
        ShiftRequest req = row.getShiftRequest();
        FacilityProfile facility = req.getFacilityProfile();
        ClientProfile client = req.getClientProfile();
        boolean fromFacility = facility != null;
        String firstName;
        String lastName;
        String facilityName;
        if (fromFacility) {
            firstName = facility.getFacilityName();
            lastName = "";
            facilityName = facility.getFacilityName();
        } else if (client != null) {
            firstName = client.getFirstName();
            lastName = client.getLastName();
            facilityName = null;
        } else {
            firstName = "";
            lastName = "";
            facilityName = null;
        }
        return new AgencyShiftRequestInboxResponse(
                row.getId(),
                req.getId(),
                row.getStatus(),
                req.getStatus(),
                req.getHomeUser().getId(),
                firstName,
                lastName,
                req.getRequiredQualification(),
                req.getStartDate(),
                req.getEndDate(),
                req.getStartTime(),
                req.getEndTime(),
                req.getCity(),
                req.getZip(),
                req.getNotes(),
                req.getCreatedAt(),
                row.getCreatedShiftId(),
                fromFacility,
                facilityName,
                Math.max(1, req.getRequiredHeadcount()));
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
