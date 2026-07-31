package com.okaynow.legal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "legal_documents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_legal_documents_type_version", columnNames = {"document_type", "version"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private LegalDocumentType documentType;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    private Instant publishedAt;

    private UUID publishedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
