package com.mailapp.service.impl;

import com.mailapp.model.AuditLog;
import com.mailapp.model.User;
import com.mailapp.repository.AuditLogRepository;
import com.mailapp.repository.UserRepository;
import com.mailapp.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * Triển khai AuditLogService thực tế, ghi nhật ký kiểm toán vào bảng audit_logs.
 * Đảm bảo không ghi mật khẩu, access token hay refresh token.
 */
@Service
@Primary
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transaction;
    private final MailAuditRecovery mailAuditRecovery;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository, UserRepository userRepository,
                               PlatformTransactionManager transactionManager, MailAuditRecovery mailAuditRecovery) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.mailAuditRecovery = mailAuditRecovery;
    }

    @Override
    public String prepareMailMutation(Long userId, String action, String detail, String ipAddress) {
        return mailAuditRecovery.prepare(userId, action, detail, ipAddress);
    }

    @Override
    public void finishMailMutation(String eventId, boolean confirmed, String confirmedDetail) {
        mailAuditRecovery.finish(eventId, confirmed, confirmedDetail);
    }

    @Override
    public void log(Long userId, String action, String detail, String ipAddress) {
        // Join a business transaction for reversible database writes: an
        // audit failure must roll back those writes, not be swallowed inside
        // a rollback-only transaction. Standalone audit (e.g. after confirmed
        // SMTP delivery) has its own transaction; catch AFTER commit/rollback.
        boolean participates = TransactionSynchronizationManager.isActualTransactionActive();
        try {
            transaction.executeWithoutResult(status -> persist(userId, action, detail, ipAddress));
        } catch (RuntimeException e) {
            log.error("AUDIT_WRITE_FAILED action={} actorId={} cause={}", safeAction(action),
                    userId, e.getClass().getSimpleName());
            if (participates) throw e;
        }
    }

    private void persist(Long userId, String action, String detail, String ipAddress) {
        AuditLog auditLog = new AuditLog();
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            auditLog.setUser(user);
        }
        auditLog.setAction(action);
        auditLog.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(LocalDateTime.now());

        auditLogRepository.saveAndFlush(auditLog);
        Runnable committed = () -> log.info("[AUDIT] Action={}, User={}", safeAction(action), userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committed.run(); }
            });
        } else {
            committed.run();
        }
    }

    private static String safeAction(String action) {
        return action != null && action.matches("[A-Z0-9_]{1,64}") ? action : "UNKNOWN";
    }
}

