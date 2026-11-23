package com.mailapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Thực thể cấu hình hệ thống (key-value) do Admin quản lý.
 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String value;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SystemSetting() {}

    public SystemSetting(String key, String value, String description, User updatedBy) {
        this.key = key;
        this.value = value;
        this.description = description;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public User getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(User updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

