package com.okaynow.users.repository;

import com.okaynow.users.domain.ClientProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClientProfileRepository extends JpaRepository<ClientProfile, UUID> {

    Optional<ClientProfile> findByUserId(UUID userId);

    @Query("select c from ClientProfile c join fetch c.user where c.id = :id")
    Optional<ClientProfile> findByIdWithUser(@Param("id") UUID id);

    @Query("""
            select c from ClientProfile c
            join c.user u
            where (lower(c.firstName) like lower(concat('%', :search, '%'))
                or lower(c.lastName) like lower(concat('%', :search, '%'))
                or lower(u.email) like lower(concat('%', :search, '%'))
                or lower(c.addressLine) like lower(concat('%', :search, '%'))
                or lower(c.city) like lower(concat('%', :search, '%')))
            """)
    Page<ClientProfile> search(@Param("search") String search, Pageable pageable);
}
