package com.mailapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể nhật ký kiểm toán hệ thống (Audit Log).
 * Ghi nhận các hành động quan trọng mà không lưu mật khẩu, token hay nội dung mail.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_logs_user", columnList = "user_id"),
    @Index(name = "idx_audit_logs_action", columnList = "action"),
    @Index(name = "idx_audit_logs_created_at", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "mail_event_id", length = 36, unique = true)
    private String mailEventId;
    @Column(name = "mail_actor_id")
    private Long mailActorId;

    public AuditLog() {}

    public AuditLog(User user, String action, String detail, String ipAddress) {
        this.user = user;
        this.action = action;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getMailEventId() { return mailEventId; }
    public void setMailEventId(String mailEventId) { this.mailEventId = mailEventId; }
    public Long getMailActorId() { return mailActorId; }
    public void setMailActorId(Long mailActorId) { this.mailActorId = mailActorId; }
}

