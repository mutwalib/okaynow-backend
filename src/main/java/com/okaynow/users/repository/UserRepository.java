package com.okaynow.users.repository;

import com.okaynow.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.UserStatus;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    java.util.List<User> findByRoleAndStatus(Role role, UserStatus status);

    /** {@code search} must be non-null (empty string matches everything) — a null String
     * parameter is bound as bytea on Postgres and breaks {@code lower()}. */
    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and lower(u.email) like lower(concat('%', :search, '%'))
            """)
    Page<User> search(@Param("role") Role role,
                      @Param("status") UserStatus status,
                      @Param("search") String search,
                      Pageable pageable);
}
