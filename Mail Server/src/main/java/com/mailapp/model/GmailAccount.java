package com.mailapp.model;

import com.mailapp.util.EmailNormalizer;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể GmailAccount liên kết với User (1 User - Nhiều Gmail).
 * Hỗ trợ đồng thời nhiều phương thức đăng nhập (Google OAuth2 và App Password) trên cùng 1 hộp thư.
 * Cột auth_type biểu diễn phương thức CHÍNH (preferredMethod).
 */
@Entity
@Table(name = "gmail_accounts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_gmail_key", columnNames = {"user_id", "email_key"})
})
public class GmailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // An in-flight refresh/probe must not restore a method that another
    // request removed or overwrite a newly linked grant via a stale save.
    @Version
    @Column(name = "revision", nullable = false)
    private Long revision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "email_key", nullable = false, length = 255)
    private String emailKey;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 30)
    private AuthType authType;

    @Column(name = "app_password_encrypted", columnDefinition = "TEXT")
    private String appPasswordEncrypted;

    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "fallback_active", nullable = false)
    private boolean fallbackActive = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public GmailAccount() {}

    @PrePersist
    protected void onCreate() {
        syncEmailKey();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        syncEmailKey();
        this.updatedAt = LocalDateTime.now();
    }

    private void syncEmailKey() {
        if ((this.emailKey == null || this.emailKey.isBlank()) && this.email != null) {
            this.emailKey = EmailNormalizer.normalizeKey(this.email);
        }
    }

    public boolean hasOAuth() {
        return refreshTokenEncrypted != null && !refreshTokenEncrypted.isBlank();
    }

    public boolean hasAppPassword() {
        return appPasswordEncrypted != null && !appPasswordEncrypted.isBlank();
    }

    public AuthType getPreferredMethod() {
        return authType;
    }

    /**
     * Trả về phương thức xác thực đang thực sự được sử dụng cho hộp thư này.
     * Khi OAuth2 gặp NEEDS_REAUTH và fallbackActive=true (có App Password dự phòng), trả APP_PASSWORD.
     */
    public AuthType getActiveMethod() {
        if (fallbackActive && hasAppPassword()) {
            return AuthType.APP_PASSWORD;
        }
        if (authType == AuthType.OAUTH2 && hasOAuth()) {
            return AuthType.OAUTH2;
        }
        if (authType == AuthType.APP_PASSWORD && hasAppPassword()) {
            return AuthType.APP_PASSWORD;
        }
        if (hasOAuth()) {
            return AuthType.OAUTH2;
        }
        if (hasAppPassword()) {
            return AuthType.APP_PASSWORD;
        }
        return authType;
    }

    public boolean canFallbackToAppPassword() {
        return authType == AuthType.OAUTH2 && hasAppPassword();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Long getRevision() { return revision; }

    /**
     * A repository save of a detached entity merges into a different instance.
     * Carry forward ONLY the revision returned by that save, never the revision
     * of a separately reloaded row (which would defeat optimistic locking).
     */
    public void acceptOwnSave(GmailAccount saved) {
        if (saved == this) return;
        if (saved == null || !java.util.Objects.equals(id, saved.id)
                || revision == null || saved.revision == null
                || (saved.revision.longValue() != revision.longValue()
                    && saved.revision.longValue() != revision.longValue() + 1)) {
            throw new IllegalStateException("Invalid saved mailbox revision");
        }
        revision = saved.revision;
        updatedAt = saved.updatedAt;
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email;
        if (email != null) {
            this.emailKey = EmailNormalizer.normalizeKey(email);
        }
    }

    public String getEmailKey() {
        if ((emailKey == null || emailKey.isBlank()) && email != null) {
            return EmailNormalizer.normalizeKey(email);
        }
        return emailKey;
    }
    public void setEmailKey(String emailKey) { this.emailKey = emailKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public AuthType getAuthType() { return authType; }
    public void setAuthType(AuthType authType) { this.authType = authType; }

    public String getAppPasswordEncrypted() { return appPasswordEncrypted; }
    public void setAppPasswordEncrypted(String appPasswordEncrypted) { this.appPasswordEncrypted = appPasswordEncrypted; }

    public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public void setRefreshTokenEncrypted(String refreshTokenEncrypted) { this.refreshTokenEncrypted = refreshTokenEncrypted; }

    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(LocalDateTime lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public boolean isFallbackActive() { return fallbackActive; }
    public void setFallbackActive(boolean fallbackActive) { this.fallbackActive = fallbackActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

