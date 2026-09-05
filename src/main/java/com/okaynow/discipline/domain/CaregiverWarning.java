package com.okaynow.discipline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Formal platform warning issued when a caregiver is marked no-show.
 * Three warnings trigger automatic {@code UserStatus.RESTRICTED}.
 */
@Entity
@Table(
        name = "caregiver_warnings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_caregiver_warnings_shift",
                columnNames = "shift_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaregiverWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "caregiver_profile_id", nullable = false)
    private UUID caregiverProfileId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    /** 1-based ordinal at the time this warning was issued. */
    @Column(nullable = false)
    private int warningNumber;

    @Column(nullable = false, length = 500)
    private String reason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
