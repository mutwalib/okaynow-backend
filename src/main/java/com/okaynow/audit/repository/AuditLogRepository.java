package com.okaynow.audit.repository;

import com.okaynow.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
