package com.okaynow.users.repository;

import com.okaynow.users.domain.CaregiverProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CaregiverProfileRepository extends JpaRepository<CaregiverProfile, UUID> {

    Optional<CaregiverProfile> findByUserId(UUID userId);

    @Query("select c from CaregiverProfile c join fetch c.user")
    java.util.List<CaregiverProfile> findAllWithUser();

    /**
     * Pessimistic write lock used by booking operations. Always acquired BEFORE the
     * shift lock (consistent lock ordering avoids deadlocks).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CaregiverProfile p where p.user.id = :userId")
    Optional<CaregiverProfile> findByUserIdForUpdate(@Param("userId") UUID userId);
}
