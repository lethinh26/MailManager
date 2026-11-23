package com.mailapp.service;

/**
 * Interface ghi nhật ký kiểm toán hệ thống.
 * TODO: thay bằng bản của Thịnh (M01)
 */
public interface AuditLogService {
    void log(Long userId, String action, String detail, String ipAddress);
}

