package com.okaynow.connections.service;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.support.AgencyAccessService;
import com.okaynow.common.exception.BadRequestException;
import com.okaynow.common.exception.ConflictException;
import com.okaynow.common.exception.ResourceNotFoundException;
import com.okaynow.connections.domain.ConnectionStatus;
import com.okaynow.connections.domain.HomeAgencyConnection;
import com.okaynow.connections.dto.ConnectAgencyRequest;
import com.okaynow.connections.dto.HomeAgencyConnectionResponse;
import com.okaynow.connections.repository.HomeAgencyConnectionRepository;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HomeAgencyConnectionService {

    private final HomeAgencyConnectionRepository connectionRepository;
    private final AgencyRepository agencyRepository;
    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final AgencyAccessService agencyAccessService;

    @Transactional
    public HomeAgencyConnectionResponse requestConnection(
            UUID homeUserId, UUID agencyId, ConnectAgencyRequest request) {
        User homeUser = requireHomeUser(homeUserId);
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found"));
        if (!agency.subscriptionAllowsDirectoryListing()) {
            throw new BadRequestException("This agency is not accepting new home connections");
        }
        var existing = connectionRepository.findByHomeUserIdAndAgencyId(homeUserId, agencyId);
        if (existing.isPresent()) {
            ConnectionStatus status = existing.get().getStatus();
            if (status == ConnectionStatus.PENDING || status == ConnectionStatus.ACTIVE) {
                throw new ConflictException("You already have a connection request with this agency");
            }
        }
        HomeAgencyConnection connection = HomeAgencyConnection.builder()
                .homeUser(homeUser)
                .agency(agency)
                .status(ConnectionStatus.PENDING)
                .homeMessage(request != null ? trimOrNull(request.message()) : null)
                .build();
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public List<HomeAgencyConnectionResponse> listForHome(UUID homeUserId) {
        requireHomeUser(homeUserId);
        return connectionRepository.findByHomeUserIdOrderByCreatedAtDesc(homeUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void endConnectionForHome(UUID homeUserId, UUID agencyId) {
        requireHomeUser(homeUserId);
        HomeAgencyConnection connection = connectionRepository
                .findByHomeUserIdAndAgencyId(homeUserId, agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        connection.setStatus(ConnectionStatus.ENDED);
        connection.setRespondedAt(Instant.now());
        connectionRepository.save(connection);
    }

    @Transactional(readOnly = true)
    public List<HomeAgencyConnectionResponse> listForAgency(UUID agencyUserId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        return connectionRepository.findByAgencyIdOrderByCreatedAtDesc(agency.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HomeAgencyConnectionResponse acceptConnection(UUID agencyUserId, UUID connectionId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        HomeAgencyConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        if (!connection.getAgency().getId().equals(agency.getId())) {
            throw new ResourceNotFoundException("Connection not found");
        }
        if (connection.getStatus() != ConnectionStatus.PENDING) {
            throw new BadRequestException("Only pending connection requests can be accepted");
        }
        connection.setStatus(ConnectionStatus.ACTIVE);
        connection.setRespondedAt(Instant.now());
        return toResponse(connectionRepository.save(connection));
    }

    @Transactional
    public HomeAgencyConnectionResponse endConnectionForAgency(UUID agencyUserId, UUID connectionId) {
        Agency agency = agencyAccessService.requireAgencyForUser(agencyUserId);
        agencyAccessService.assertAgencyAllowsWrites(agency);
        HomeAgencyConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        if (!connection.getAgency().getId().equals(agency.getId())) {
            throw new ResourceNotFoundException("Connection not found");
        }
        connection.setStatus(ConnectionStatus.ENDED);
        connection.setRespondedAt(Instant.now());
        return toResponse(connectionRepository.save(connection));
    }

    public boolean hasActiveConnection(UUID homeUserId, UUID agencyId) {
        return connectionRepository.findByHomeUserIdAndAgencyId(homeUserId, agencyId)
                .map(c -> c.getStatus() == ConnectionStatus.ACTIVE)
                .orElse(false);
    }

    private User requireHomeUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.CLIENT) {
            throw new BadRequestException("Only home accounts can manage agency connections");
        }
        return user;
    }

    private HomeAgencyConnectionResponse toResponse(HomeAgencyConnection connection) {
        Agency agency = connection.getAgency();
        String homeFirst = null;
        String homeLast = null;
        var profile = clientProfileRepository.findByUserId(connection.getHomeUser().getId());
        if (profile.isPresent()) {
            homeFirst = profile.get().getFirstName();
            homeLast = profile.get().getLastName();
        }
        return new HomeAgencyConnectionResponse(
                connection.getId(),
                agency.getId(),
                agency.getSlug(),
                agency.getDisplayName(),
                agency.getCity(),
                agency.getState(),
                homeFirst,
                homeLast,
                connection.getStatus(),
                connection.getHomeMessage(),
                connection.getCreatedAt(),
                connection.getRespondedAt());
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
