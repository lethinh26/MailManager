package com.mailapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Only audit metadata, never message content, credentials or provider replies. */
@Entity
@Table(name = "mail_audit_outbox", indexes = @Index(name = "idx_mail_audit_outbox_ready",
        columnList = "outcome, occurred_at, event_id"))
public class MailAuditOutbox {
    public static final String PREPARED = "PREPARED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String UNCERTAIN = "UNCERTAIN";

    @Id
    @Column(name = "event_id", length = 36)
    private String id;
    @Column(name = "actor_id")
    private Long actorId;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(length = 500)
    private String detail;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
    @Column(nullable = false, length = 16)
    private String outcome;
    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    protected MailAuditOutbox() {}

    public MailAuditOutbox(String id, Long actorId, String action, String detail,
                           String ipAddress, LocalDateTime occurredAt) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.occurredAt = occurredAt;
        this.outcome = PREPARED;
    }

    public String getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getOutcome() { return outcome; }
    public int getDeliveryAttempts() { return deliveryAttempts; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }

    public boolean isDue(LocalDateTime now) {
        return nextAttemptAt == null || !nextAttemptAt.isAfter(now);
    }

    public void deferDelivery(LocalDateTime now, long baseMillis) {
        if (PREPARED.equals(outcome)) throw new IllegalStateException("Intent not finalized");
        deliveryAttempts = Math.min(30, deliveryAttempts + 1);
        long delay = Math.min(300_000L, baseMillis * (1L << Math.min(15, deliveryAttempts - 1)));
        nextAttemptAt = now.plusNanos(delay * 1_000_000L);
    }

    public void finish(String outcome, String detail) {
        if (!PREPARED.equals(this.outcome)) throw new IllegalStateException("Event already finalized");
        if (!CONFIRMED.equals(outcome) && !UNCERTAIN.equals(outcome))
            throw new IllegalArgumentException("Invalid final mail audit outcome");
        this.outcome = outcome;
        if (detail != null) this.detail = detail;
    }
}
