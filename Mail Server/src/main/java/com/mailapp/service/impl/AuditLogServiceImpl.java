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
import org.springframework.transaction.annotation.Transactional;

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

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void log(Long userId, String action, String detail, String ipAddress) {
        try {
            AuditLog auditLog = new AuditLog();
            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                auditLog.setUser(user);
            }
            auditLog.setAction(action);
            auditLog.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
            auditLog.setIpAddress(ipAddress);
            auditLog.setCreatedAt(LocalDateTime.now());

            auditLogRepository.save(auditLog);
            log.info("[AUDIT] Action={}, User={}, IP={}", action, userId, ipAddress);
        } catch (Exception e) {
            log.error("Không thể ghi audit log action={}: {}", action, e.getMessage());
        }
    }
}

