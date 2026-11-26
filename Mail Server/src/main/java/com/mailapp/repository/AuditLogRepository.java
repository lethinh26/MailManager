package com.mailapp.repository;

import com.mailapp.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);
    java.util.Optional<AuditLog> findByMailEventId(String eventId);

    long countByActionAndCreatedAtAfter(String action, LocalDateTime since);
    List<AuditLog> findByActionAndCreatedAtGreaterThanEqual(String action, LocalDateTime since);

    @Modifying
    @Query("UPDATE AuditLog a SET a.user = null WHERE a.user.id = :userId")
    void detachUser(@Param("userId") Long userId);

    @Query("""
            SELECT a FROM AuditLog a
            LEFT JOIN a.user u
            WHERE (:userId IS NULL OR u.id = :userId)
              AND (:username IS NULL OR :username = '' OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :username, '%')))
              AND (:action IS NULL OR :action = '' OR LOCATE(UPPER(:action), UPPER(a.action)) > 0
                   OR (UPPER(:action) = 'LOGIN_SUCCESS' AND a.action = 'LOGIN')
                   OR (UPPER(:action) = 'MAIL_DELETE' AND a.action = 'DELETE_MAIL')
                   OR (UPPER(:action) = 'DELETE_MAIL' AND a.action = 'MAIL_DELETE')
                   OR (UPPER(:action) = 'SETTING_CHANGE' AND a.action = 'ADMIN_CHANGE_SETTING')
                   OR (UPPER(:action) = 'ADMIN_CHANGE_SETTING' AND a.action = 'SETTING_CHANGE')
                   OR (UPPER(:action) = 'OAUTH2_CALLBACK'
                       AND a.action IN ('LINK_GMAIL', 'ADD_GMAIL_METHOD', 'UPDATE_GMAIL_METHOD')
                       AND LOCATE('OAUTH2', UPPER(COALESCE(a.detail, ''))) > 0)
                   OR (a.action = 'OAUTH2_CALLBACK' AND (
                       (UPPER(:action) = 'LINK_GMAIL' AND LOCATE('outcome=CREATED;', COALESCE(a.detail, '')) > 0)
                       OR (UPPER(:action) = 'ADD_GMAIL_METHOD' AND LOCATE('outcome=METHOD_ADDED;', COALESCE(a.detail, '')) > 0)
                       OR (UPPER(:action) = 'UPDATE_GMAIL_METHOD' AND LOCATE('outcome=METHOD_UPDATED;', COALESCE(a.detail, '')) > 0))))
              AND (:fromTime IS NULL OR a.createdAt >= :fromTime)
              AND (:toTime IS NULL OR a.createdAt <= :toTime)
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<AuditLog> searchLogs(@Param("userId") Long userId,
                              @Param("username") String username,
                              @Param("action") String action,
                              @Param("fromTime") LocalDateTime fromTime,
                              @Param("toTime") LocalDateTime toTime,
                              Pageable pageable);
}
