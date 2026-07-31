package com.okaynow.audit.service;

import com.okaynow.audit.domain.AuditAction;
import com.okaynow.audit.domain.AuditLog;
import com.okaynow.audit.dto.AuditLogResponse;
import com.okaynow.audit.repository.AuditLogRepository;
import com.okaynow.common.dto.PagedResponse;
import com.okaynow.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void record(User actor, AuditAction action, String entityType, UUID entityId,
                       UUID clientProfileId, String details) {
        auditLogRepository.save(AuditLog.builder()
                .actorUserId(actor.getId())
                .actorEmail(actor.getEmail())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .clientProfileId(clientProfileId)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> list(Pageable pageable) {
        return PagedResponse.from(auditLogRepository.findAll(pageable).map(this::toResponse));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getActorEmail(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getClientProfileId(),
                log.getDetails(), log.getCreatedAt());
    }
}
