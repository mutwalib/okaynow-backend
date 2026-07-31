package com.okaynow.users.repository;

import com.okaynow.users.domain.FacilityProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FacilityProfileRepository extends JpaRepository<FacilityProfile, UUID> {

    Optional<FacilityProfile> findByUserId(UUID userId);

    @Query("""
            select f from FacilityProfile f
            join f.user u
            where (lower(f.facilityName) like lower(concat('%', :search, '%'))
                or lower(f.contactFirstName) like lower(concat('%', :search, '%'))
                or lower(f.contactLastName) like lower(concat('%', :search, '%'))
                or lower(u.email) like lower(concat('%', :search, '%'))
                or lower(f.addressLine) like lower(concat('%', :search, '%'))
                or lower(f.city) like lower(concat('%', :search, '%')))
            """)
    Page<FacilityProfile> search(@Param("search") String search, Pageable pageable);
}
