package com.okaynow.shiftrequests.repository;

import com.okaynow.shiftrequests.domain.ShiftRequest;
import com.okaynow.shiftrequests.domain.ShiftRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShiftRequestRepository extends JpaRepository<ShiftRequest, UUID> {

    List<ShiftRequest> findByHomeUserIdOrderByCreatedAtDesc(UUID homeUserId);

    List<ShiftRequest> findByHomeUserIdAndStatusOrderByCreatedAtDesc(
            UUID homeUserId, ShiftRequestStatus status);
}
