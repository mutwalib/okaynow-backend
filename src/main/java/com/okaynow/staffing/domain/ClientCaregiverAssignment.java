package com.okaynow.staffing.domain;

import com.okaynow.users.domain.CaregiverProfile;
import com.okaynow.users.domain.ClientProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

@Entity
@Table(name = "client_caregiver_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_client_caregiver_assignment",
                columnNames = {"client_profile_id", "caregiver_profile_id"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientCaregiverAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "client_profile_id", nullable = false)
    private ClientProfile clientProfile;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "caregiver_profile_id", nullable = false)
    private CaregiverProfile caregiverProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentType assignmentType;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 500)
    private String notes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
