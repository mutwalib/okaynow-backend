package com.okaynow.shiftrequests.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.common.geo.GeocodingService;
import com.okaynow.common.geo.ServiceRegionService;
import com.okaynow.connections.domain.ConnectionStatus;
import com.okaynow.connections.service.HomeAgencyConnectionService;
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
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShiftRequestService {

    private final ShiftRequestRepository shiftRequestRepository;
    private final ShiftRequestAgencyRepository shiftRequestAgencyRepository;
    private final HomeAgencyConnectionService connectionService;
    private final AgencyRepository agencyRepository;
    private final AgencyAccessService agencyAccessService;
    private final ClientProfileRepository clientProfileRepository;
    private final UserRepository userRepository;
    private final ServiceRegionService serviceRegionService;
    private final GeocodingService geocodingService;
    private final AgencySettingsService agencySettingsService;
    private final ShiftRepository shiftRepository;

    @Transactional
    public ShiftRequestResponse createForHome(UUID homeUserId, CreateShiftRequestPayload payload) {
        User homeUser = requireHomeUser(homeUserId);
        ClientProfile client = clientProfileRepository.findByUserId(homeUserId)
                .orElseThrow(() -> new BadRequestException("Complete your home profile before posting care needs"));

        if (payload.endDate() != null && payload.endDate().isBefore(payload.startDate())) {
            throw new BadRequestException("End date must be on or after start date");
        }
        if (payload.agencyIds().isEmpty()) {
            throw new BadRequestException("Select at least one connected agency");
        }
        var uniqueAgencyIds = new HashSet<>(payload.agencyIds());
        if (uniqueAgencyIds.size() != payload.agencyIds().size()) {
            throw new BadRequestException("Duplicate agencies in request");
        }

        String addressLine = payload.addressLine() != null && !payload.addressLine().isBlank()
                ? payload.addressLine().trim()
                : client.getAddressLine();
        String city = payload.city() != null && !payload.city().isBlank()
                ? payload.city().trim()
                : client.getCity();
        String state = payload.state() != null ? payload.state() : client.getState();
        String zip = payload.zip() != null && !payload.zip().isBlank()
                ? payload.zip().trim()
                : client.getZip();
        if (addressLine == null || city == null || zip == null) {
            throw new BadRequestException("Address is required — update your profile or include it in the request");
        }
        var region = serviceRegionService.validate(state, zip);

        ShiftRequest request = ShiftRequest.builder()
                .homeUser(homeUser)
                .clientProfile(client)
                .requiredQualification(payload.requiredQualification())
                .startDate(payload.startDate())
                .endDate(payload.endDate())
                .startTime(payload.startTime())
                .endTime(payload.endTime())
                .addressLine(addressLine)
                .city(city)
                .state(region.state())
                .zip(region.zip())
                .notes(trimOrNull(payload.notes()))
                .status(ShiftRequestStatus.OPEN)
                .build();
        request = shiftRequestRepository.save(request);

        List<ShiftRequestAgency> targets = new ArrayList<>();
        for (UUID agencyId : uniqueAgencyIds) {
            if (!connectionService.hasActiveConnection(homeUserId, agencyId)) {
                throw new BadRequestException("You must be connected to every selected agency");
            }
            Agency agency = agencyRepository.findById(agencyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
            targets.add(shiftRequestAgencyRepository.save(ShiftRequestAgency.builder()
                    .shiftRequest(request)
                    .agency(agency)
                    .status(ShiftRequestAgencyStatus.PENDING)
                    .build()));
        }
        return toResponse(request, targets);
    }

    @Transactional(readOnly = true)
    public List<ShiftRequestResponse> listForHome(UUID homeUserId) {
        requireHomeUser(homeUserId);
        return shiftRequestRepository.findByHomeUserIdOrderByCreatedAtDesc(homeUserId).stream()
                .map(req -> toResponse(req, shiftRequestAgencyRepository.findByShiftRequestId(req.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftRequestResponse getForHome(UUID homeUserId, UUID requestId) {
        requireHomeUser(homeUserId);
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

        AgencySettings settings = agencySettingsService.getOrCreate();
        var payRate = settings.getDefaultPayRate();
        var billRate = settings.billRateFromPayRate(payRate);

        LocalDate shiftDate = request.getStartDate();
        Shift shift = Shift.builder()
                .agencyId(agency.getId())
                .shiftRequestId(request.getId())
                .clientProfileId(request.getClientProfile().getId())
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
                .requiredHeadcount(1)
                .notes(request.getNotes())
                .createdBy(agencyUserId)
                .build();
        geocodingService.geocode(
                        shift.getAddressLine(), shift.getCity(), shift.getState(), shift.getZip())
                .ifPresent(point -> {
                    shift.setLat(point.lat());
                    shift.setLng(point.lng());
                });
        Shift savedShift = shiftRepository.save(shift);

        row.setStatus(ShiftRequestAgencyStatus.ACCEPTED);
        row.setRespondedAt(Instant.now());
        row.setCreatedShiftId(savedShift.getId());
        shiftRequestAgencyRepository.save(row);

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

    private User requireHomeUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.CLIENT) {
            throw new BadRequestException("Only home accounts can manage shift requests");
        }
        return user;
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
        ClientProfile client = req.getClientProfile();
        return new AgencyShiftRequestInboxResponse(
                row.getId(),
                req.getId(),
                row.getStatus(),
                req.getStatus(),
                req.getHomeUser().getId(),
                client.getFirstName(),
                client.getLastName(),
                req.getRequiredQualification(),
                req.getStartDate(),
                req.getEndDate(),
                req.getStartTime(),
                req.getEndTime(),
                req.getCity(),
                req.getZip(),
                req.getNotes(),
                req.getCreatedAt(),
                row.getCreatedShiftId());
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
