package com.okaynow.connections.repository;

import com.okaynow.connections.domain.ConnectionStatus;
import com.okaynow.connections.domain.HomeAgencyConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeAgencyConnectionRepository extends JpaRepository<HomeAgencyConnection, UUID> {

    Optional<HomeAgencyConnection> findByHomeUserIdAndAgencyId(UUID homeUserId, UUID agencyId);

    List<HomeAgencyConnection> findByHomeUserIdOrderByCreatedAtDesc(UUID homeUserId);

    List<HomeAgencyConnection> findByAgencyIdOrderByCreatedAtDesc(UUID agencyId);

    List<HomeAgencyConnection> findByAgencyIdAndStatusOrderByCreatedAtDesc(
            UUID agencyId, ConnectionStatus status);

    boolean existsByHomeUserIdAndAgencyIdAndStatusIn(
            UUID homeUserId, UUID agencyId, List<ConnectionStatus> statuses);
}
