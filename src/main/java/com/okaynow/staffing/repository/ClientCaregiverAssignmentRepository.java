package com.okaynow.staffing.repository;

import com.okaynow.staffing.domain.AssignmentType;
import com.okaynow.staffing.domain.ClientCaregiverAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientCaregiverAssignmentRepository
        extends JpaRepository<ClientCaregiverAssignment, UUID> {

    @Query("""
            select a from ClientCaregiverAssignment a
            join fetch a.caregiverProfile c
            join fetch c.user
            where a.clientProfile.id = :clientId and a.active = true
            order by a.createdAt asc
            """)
    List<ClientCaregiverAssignment> findByClientProfileIdAndActiveTrueOrderByCreatedAtAsc(
            @Param("clientId") UUID clientProfileId);

    Optional<ClientCaregiverAssignment> findByClientProfileIdAndCaregiverProfileId(
            UUID clientProfileId, UUID caregiverProfileId);

    @Query("""
            select count(a) from ClientCaregiverAssignment a
            where a.clientProfile.id = :clientId
              and a.assignmentType = :type
              and a.active = true
              and (:excludeId is null or a.id <> :excludeId)
            """)
    long countActiveByClientAndType(
            @Param("clientId") UUID clientId,
            @Param("type") AssignmentType type,
            @Param("excludeId") UUID excludeId);
}
