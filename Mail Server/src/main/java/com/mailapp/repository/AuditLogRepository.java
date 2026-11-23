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
              AND (:action IS NULL OR :action = '' OR UPPER(a.action) = UPPER(:action))
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
