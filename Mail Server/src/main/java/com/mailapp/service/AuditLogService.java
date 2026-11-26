package com.mailapp.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Interface ghi nhật ký kiểm toán hệ thống.
 * TODO: thay bằng bản của Thịnh (M01)
 */
public interface AuditLogService {
    /**
     * Participates in a caller's database transaction (failure propagates and
     * rolls it back). Outside a transaction, records a best-effort standalone
     * audit, reporting failure without changing a confirmed external outcome.
     * Callers must supply only allowlisted, non-secret detail fields.
     */
    void log(Long userId, String action, String detail, String ipAddress);

    /** Prepare metadata durably BEFORE an explicit external mail mutation. */
    String prepareMailMutation(Long userId, String action, String detail, String ipAddress);

    /**
     * Finalize only the audit intent, never repeat the external mutation.
     * confirmed=false records an uncertain outcome, not a success.
     * Failure to persist the outcome must not change a confirmed mail result.
     */
    void finishMailMutation(String eventId, boolean confirmed, String confirmedDetail);

    /** Use the effective remote address after the configured trusted-proxy processing. */
    default void log(Long userId, String action, String detail, HttpServletRequest request) {
        log(userId, action, detail, request == null ? (String) null : request.getRemoteAddr());
    }
}

