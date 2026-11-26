package com.mailapp.service.impl;

import com.mailapp.exception.AppException;
import com.mailapp.model.AuditLog;
import com.mailapp.model.MailAuditOutbox;
import com.mailapp.repository.AuditLogRepository;
import com.mailapp.repository.MailAuditOutboxRepository;
import com.mailapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Durable intent and transactional audit delivery for explicitly audited SMTP/IMAP
 * mutations. Replay never opens a mail connection. A crash between the remote
 * effect and local confirmation leaves PREPARED (not an invented success).
 */
@Service
public class MailAuditRecovery {
    private static final Logger log = LoggerFactory.getLogger(MailAuditRecovery.class);
    private static final Set<String> ACTIONS = Set.of("SEND_MAIL", "MAIL_DELETE", "MAIL_FLAG_CHANGE");
    private static final int BATCH = 32;
    // Reserve capacity before any remote mutation. Never evict an acknowledgement
    // merely to admit a new operation during a prolonged outcome-storage outage.
    private static final int DEFAULT_LOCAL_CAPACITY = 128;
    private final MailAuditOutboxRepository outbox;
    private final AuditLogRepository audits;
    private final UserRepository users;
    private final TransactionTemplate transaction;
    private final int localCapacity;
    private final long retryBaseMillis;
    private final MailAuditReceiptJournal journal;
    // Guarded by pending; no database or mail I/O while holding this monitor.
    // Entries are reserved before prepare commits and released only after durable
    // outcome recording, including when the outcome has already been delivered.
    private final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
    private LocalDateTime cursorTime;
    private String cursorId;
    private LocalDateTime sweepThrough;

    private record Transition(String outcome, String detail) {}
    private record Pending(Transition transition, boolean deferred, boolean recovered) {}

    public record Status(long prepared, long confirmed, long uncertain, long stalePrepared,
                         LocalDateTime oldestPreparedAt, LocalDateTime oldestReadyAt,
                         int localInFlight, int localDeferred, int localCapacity,
                         boolean receiptRecoveryEnabled, boolean receiptJournalAvailable) {}

    public record EventStatus(String eventId, Long actorId, String action, String outcome,
                              LocalDateTime occurredAt, boolean stalePrepared,
                              int deliveryAttempts, LocalDateTime nextAttemptAt) {}
    public record EventPage(List<EventStatus> content, int page, int size, long totalElements,
                            int totalPages) {}

    public MailAuditRecovery(MailAuditOutboxRepository outbox, AuditLogRepository audits,
                             UserRepository users, PlatformTransactionManager manager) {
        this(outbox, audits, users, manager, DEFAULT_LOCAL_CAPACITY);
    }

    public MailAuditRecovery(MailAuditOutboxRepository outbox, AuditLogRepository audits,
                             UserRepository users, PlatformTransactionManager manager, int localCapacity) {
        this(outbox, audits, users, manager, localCapacity, 10_000L, null);
    }

    @Autowired
    public MailAuditRecovery(MailAuditOutboxRepository outbox, AuditLogRepository audits,
                             UserRepository users, PlatformTransactionManager manager,
                             @Value("${mail.audit.local-capacity:128}") int localCapacity,
                             @Value("${mail.audit.retry-base-ms:10000}") long retryBaseMillis,
                             MailAuditReceiptJournal journal) {
        if (localCapacity < 1 || localCapacity > 4096)
            throw new IllegalArgumentException("mail.audit.local-capacity must be between 1 and 4096");
        if (retryBaseMillis < 100 || retryBaseMillis > 300_000)
            throw new IllegalArgumentException("mail.audit.retry-base-ms must be between 100 and 300000");
        this.outbox = outbox;
        this.audits = audits;
        this.users = users;
        this.localCapacity = localCapacity;
        this.retryBaseMillis = retryBaseMillis;
        this.journal = journal;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        if (journal != null) {
            for (var receipt : journal.recover().values())
                pending.put(receipt.id(), new Pending(new Transition(receipt.outcome(), receipt.detail()), true, true));
            if (!pending.isEmpty()) log.info("MAIL_AUDIT_RECEIPTS_RECOVERED count={}", pending.size());
        }
    }

    public String prepare(Long actorId, String action, String detail, String ipAddress) {
        if (!ACTIONS.contains(action)) throw new IllegalArgumentException("Not an external mail audit action");
        if (journal != null && !journal.canAcceptNew()) throw unavailable();
        String id = UUID.randomUUID().toString();
        synchronized (pending) {
            if (pending.size() >= localCapacity) {
                log.warn("MAIL_AUDIT_CAPACITY_EXHAUSTED capacity={}", localCapacity);
                throw unavailable();
            }
            pending.put(id, new Pending(null, false, false));
        }
        try {
            transaction.executeWithoutResult(status -> outbox.saveAndFlush(new MailAuditOutbox(
                    id, actorId, action, scrub(detail, 500), literalIp(ipAddress), LocalDateTime.now())));
            return id;
        } catch (RuntimeException e) {
            release(id);
            log.error("MAIL_AUDIT_PREPARE_FAILED event={} action={} cause={}", id, action, e.getClass().getSimpleName());
            // No external operation may begin without a durable intent.
            throw unavailable();
        }
    }

    private static AppException unavailable() {
        return new AppException(HttpStatus.SERVICE_UNAVAILABLE, "AUDIT_UNAVAILABLE",
                "Không thể ghi nhận thao tác thư lúc này. Chưa thực hiện thao tác, vui lòng thử lại sau.");
    }

    private void release(String id) {
        synchronized (pending) { pending.remove(id); }
    }

    private void markDeferred(String id) {
        synchronized (pending) {
            Pending slot = pending.get(id);
            if (slot != null && slot.transition() != null)
                pending.put(id, new Pending(slot.transition(), true, slot.recovered()));
        }
    }

    private MailAuditReceiptJournal.Receipt receipt(String id, Transition transition) {
        return new MailAuditReceiptJournal.Receipt(id, transition.outcome(), transition.detail());
    }

    private boolean persistReceipt(String id, Transition transition) {
        if (journal == null || !journal.enabled()) return true;
        try {
            journal.write(receipt(id, transition));
            return true;
        } catch (java.io.IOException | RuntimeException ex) {
            // DB may still be available. Never turn an acknowledgement into a
            // false failed mutation; keep the reserved slot if DB also fails.
            log.error("MAIL_AUDIT_RECEIPT_WRITE_FAILED event={} cause={}", id, ex.getClass().getSimpleName());
            return false;
        }
    }

    private void releaseAfterDurable(String id, Transition transition) {
        try {
            if (journal != null) journal.remove(receipt(id, transition));
            release(id);
        } catch (java.io.IOException | RuntimeException ex) {
            markDeferred(id);
            log.error("MAIL_AUDIT_RECEIPT_CLEANUP_PENDING event={} cause={}", id, ex.getClass().getSimpleName());
        }
    }

    public void finish(String id, boolean confirmed, String confirmedDetail) {
        Transition transition = new Transition(confirmed ? MailAuditOutbox.CONFIRMED : MailAuditOutbox.UNCERTAIN,
                confirmed ? scrub(confirmedDetail, 500) : null);
        synchronized (pending) {
            Pending slot = pending.get(id);
            if (slot == null) {
                // A PREPARED row alone, or an unknown ID after restart, cannot
                // authorize success. Only the original reserved operation or
                // validated startup receipt may finalize that intent.
                log.warn("MAIL_AUDIT_UNRESERVED_FINISH event={}", id);
                return;
            }
            if (slot.transition() != null) {
                if (!Objects.equals(slot.transition(), transition))
                    log.error("MAIL_AUDIT_OUTCOME_CONFLICT event={}", id);
                return; // replay owns deferred work, never race a second finish
            }
            pending.put(id, new Pending(transition, false, false));
        }
        // Force the authenticated receipt before attempting DB outcome update.
        // Only an operation with a reserved intent may create a receipt.
        boolean persisted = persistReceipt(id, transition);
        try {
            recordOutcome(id, transition);
            if (persisted) releaseAfterDurable(id, transition);
            else markDeferred(id);
        } catch (RuntimeException e) {
            markDeferred(id);
            log.error("MAIL_AUDIT_CONFIRMATION_PENDING event={} outcome={} cause={}",
                    id, transition.outcome(), e.getClass().getSimpleName());
            return; // Never turn an acknowledged external mutation into a false failure.
        }
        try {
            deliverWithBackoff(id);
        } catch (RuntimeException e) {
            log.warn("MAIL_AUDIT_DELIVERY_DEFERRED event={} cause={}", id, e.getClass().getSimpleName());
        }
    }

    private void recordOutcome(String id, Transition transition) {
        transaction.executeWithoutResult(status -> {
            var event = outbox.lockById(id).orElse(null);
            if (event == null) {
                // A missing intent alone does not prove that it was delivered.
                // Keep orphan receipts for investigation rather than inventing
                // a success or silently discarding acknowledged outcomes.
                var delivered = audits.findByMailEventId(id).orElseThrow(() ->
                        new IllegalStateException("Audit receipt has no durable intent or delivery"));
                boolean deliveredUncertain = "MAIL_OPERATION_UNCERTAIN".equals(delivered.getAction());
                if (deliveredUncertain != MailAuditOutbox.UNCERTAIN.equals(transition.outcome()))
                    throw new IllegalStateException("Receipt conflicts with delivered audit");
                if (transition.detail() != null && !Objects.equals(delivered.getDetail(), transition.detail()))
                    throw new IllegalStateException("Receipt detail conflicts with delivered audit");
                return;
            }
            if (MailAuditOutbox.PREPARED.equals(event.getOutcome())) {
                event.finish(transition.outcome(), transition.detail());
                outbox.saveAndFlush(event);
            } else if (!transition.outcome().equals(event.getOutcome())
                    || (transition.detail() != null && !Objects.equals(event.getDetail(), transition.detail()))) {
                // Never change an existing outcome; this signals a caller defect.
                log.error("MAIL_AUDIT_OUTCOME_CONFLICT event={}", id);
                throw new IllegalStateException("Conflicting durable mail audit outcome");
            }
        });
    }

    private void deliver(String id) {
        String delivered = transaction.execute(status -> {
            var event = outbox.lockById(id).orElse(null);
            if (event == null) return null;
            if (!MailAuditOutbox.CONFIRMED.equals(event.getOutcome())
                    && !MailAuditOutbox.UNCERTAIN.equals(event.getOutcome())) return null;
            if (!event.isDue(LocalDateTime.now())) return null;
            boolean confirmed = MailAuditOutbox.CONFIRMED.equals(event.getOutcome());
            AuditLog row = new AuditLog();
            row.setUser(event.getActorId() == null ? null : users.findById(event.getActorId()).orElse(null));
            row.setAction(confirmed ? event.getAction() : "MAIL_OPERATION_UNCERTAIN");
            row.setDetail(confirmed ? event.getDetail() :
                    scrub("operation=" + event.getAction() + "; " + event.getDetail(), 500));
            row.setIpAddress(event.getIpAddress());
            row.setCreatedAt(event.getOccurredAt());
            row.setMailEventId(event.getId());
            row.setMailActorId(event.getActorId());
            audits.saveAndFlush(row);
            // Same transaction, same row lock: both insert and delete commit
            // or both roll back, even if a commit acknowledgement is lost.
            outbox.delete(event);
            outbox.flush();
            return row.getAction();
        });
        if (delivered != null) log.info("[AUDIT] Action={} event={}", delivered, id);
    }

    private void deliverWithBackoff(String id) {
        try {
            deliver(id);
        } catch (RuntimeException failure) {
            abortIfDatabaseUnavailable(failure);
            try {
                transaction.executeWithoutResult(tx -> outbox.lockById(id).ifPresent(event -> {
                    LocalDateTime now = LocalDateTime.now();
                    if (!MailAuditOutbox.PREPARED.equals(event.getOutcome()) && event.isDue(now)) {
                        event.deferDelivery(now, retryBaseMillis);
                        outbox.saveAndFlush(event);
                    }
                }));
            } catch (RuntimeException schedulingFailure) {
                log.warn("MAIL_AUDIT_BACKOFF_PENDING event={} cause={}", id,
                        schedulingFailure.getClass().getSimpleName());
                abortIfDatabaseUnavailable(schedulingFailure);
            }
            throw failure;
        }
    }

    @Scheduled(fixedDelayString = "${mail.audit.replay-delay-ms:10000}",
            initialDelayString = "${mail.audit.replay-delay-ms:10000}")
    public synchronized void replay() {
        try {
            int failed = 0;
            List<Map.Entry<String, Pending>> deferred = new ArrayList<>();
            synchronized (pending) {
                for (var entry : pending.entrySet()) {
                    if (entry.getValue().deferred()) {
                        deferred.add(Map.entry(entry.getKey(), entry.getValue()));
                        if (deferred.size() >= BATCH) break;
                    }
                }
                // Rotate even failed entries, so one persistent error cannot
                // prevent later acknowledgements from becoming durable.
                for (var entry : deferred) {
                    pending.remove(entry.getKey());
                    pending.put(entry.getKey(), entry.getValue());
                }
            }
            for (var entry : deferred) {
                try {
                    Pending slot = entry.getValue();
                    boolean persisted = persistReceipt(entry.getKey(), slot.transition());
                    if (!persisted && slot.recovered()) {
                        // The receipt is the ONLY source of this recovered
                        // outcome. A conflicting/unreadable sibling must not
                        // turn an unresolved intent into success.
                        failed++;
                        continue;
                    }
                    recordOutcome(entry.getKey(), slot.transition());
                    if (persisted) releaseAfterDurable(entry.getKey(), slot.transition());
                } catch (RuntimeException e) {
                    abortIfDatabaseUnavailable(e);
                    failed++;
                }
            }
            var page = PageRequest.of(0, BATCH);
            LocalDateTime now = LocalDateTime.now();
            // Freeze the upper timestamp for each traversal. Continuous new
            // arrivals cannot postpone revisiting earlier failed rows forever.
            if (sweepThrough == null) sweepThrough = outbox.latestReadyAt(now);
            List<MailAuditOutboxRepository.ReadyEvent> ready = sweepThrough == null ? List.of()
                    : cursorTime == null ? outbox.readyEvents(sweepThrough, now, page)
                    : outbox.readyEventsAfter(cursorTime, cursorId, sweepThrough, now, page);
            // Keyset traversal (not a mutable offset) is safe when successful
            // rows are deleted and a failed row stays in the queue.
            cursorTime = null;
            cursorId = null;
            if (ready.size() == BATCH) {
                var last = ready.getLast();
                cursorTime = last.getOccurredAt();
                cursorId = last.getId();
            } else {
                sweepThrough = null;
            }
            for (var event : ready) {
                try { deliverWithBackoff(event.getId()); } catch (RuntimeException e) {
                    abortIfDatabaseUnavailable(e);
                    failed++;
                }
            }
            if (failed > 0) log.warn("MAIL_AUDIT_REPLAY_DEFERRED count={}", failed);
            long stalled = outbox.countByOutcomeAndOccurredAtBefore(MailAuditOutbox.PREPARED,
                    LocalDateTime.now().minusMinutes(10));
            if (stalled > 0) log.error("MAIL_AUDIT_UNRESOLVED_INTENTS count={}", stalled);
            long delayed = outbox.countByOutcomeAndOccurredAtBefore(MailAuditOutbox.CONFIRMED,
                    LocalDateTime.now().minusMinutes(10))
                    + outbox.countByOutcomeAndOccurredAtBefore(MailAuditOutbox.UNCERTAIN,
                    LocalDateTime.now().minusMinutes(10));
            if (delayed > 0) log.error("MAIL_AUDIT_DELIVERY_BACKLOG count={}", delayed);
        } catch (RuntimeException e) {
            log.warn("MAIL_AUDIT_REPLAY_UNAVAILABLE cause={}", e.getClass().getSimpleName());
        }
    }

    private static void abortIfDatabaseUnavailable(RuntimeException failure) {
        // Don't spend a whole batch waiting for connections when the database
        // itself is unavailable. Row-specific constraints still rotate normally.
        if (failure instanceof CannotCreateTransactionException
                || failure instanceof DataAccessResourceFailureException) throw failure;
    }

    /** Aggregate only, scoped to administrators by the controller. No event
     * detail, identities, addresses, message content or provider responses. */
    public Status status() {
        return transaction.execute(tx -> {
            long prepared = 0, confirmed = 0, uncertain = 0;
            LocalDateTime oldestPrepared = null, oldestReady = null;
            for (var group : outbox.outcomeCounts()) {
                switch (group.getOutcome()) {
                    case MailAuditOutbox.PREPARED -> {
                        prepared = group.getTotal();
                        oldestPrepared = group.getOldest();
                    }
                    case MailAuditOutbox.CONFIRMED -> {
                        confirmed = group.getTotal();
                        if (oldestReady == null || group.getOldest().isBefore(oldestReady))
                            oldestReady = group.getOldest();
                    }
                    case MailAuditOutbox.UNCERTAIN -> {
                        uncertain = group.getTotal();
                        if (oldestReady == null || group.getOldest().isBefore(oldestReady))
                            oldestReady = group.getOldest();
                    }
                    default -> log.error("MAIL_AUDIT_INVALID_STATE count={}", group.getTotal());
                }
            }
            long stale = outbox.countByOutcomeAndOccurredAtBefore(MailAuditOutbox.PREPARED,
                    LocalDateTime.now().minusMinutes(10));
            synchronized (pending) {
                int deferred = (int) pending.values().stream().filter(Pending::deferred).count();
                return new Status(prepared, confirmed, uncertain, stale, oldestPrepared,
                        oldestReady, pending.size() - deferred, deferred, localCapacity,
                        journal != null && journal.enabled(), journal == null || journal.canAcceptNew());
            }
        });
    }

    /** Bounded metadata-only queue inspection; there is intentionally no
     * endpoint to override an outcome or resend an external mail mutation. */
    public EventPage events(String outcome, int page, int size) {
        if (outcome != null && !Set.of(MailAuditOutbox.PREPARED, MailAuditOutbox.CONFIRMED,
                MailAuditOutbox.UNCERTAIN).contains(outcome))
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Trạng thái audit không hợp lệ.");
        return transaction.execute(tx -> {
            LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(10);
            var result = outbox.findEvents(outcome, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size))));
            return new EventPage(result.getContent().stream().map(e -> new EventStatus(
                    e.getId(), e.getActorId(), e.getAction(), e.getOutcome(), e.getOccurredAt(),
                    MailAuditOutbox.PREPARED.equals(e.getOutcome()) && e.getOccurredAt().isBefore(staleBefore),
                    e.getDeliveryAttempts(), e.getNextAttemptAt())).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
        });
    }

    private static String scrub(String value, int max) {
        if (value == null) return null;
        String clean = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ");
        if (clean.length() <= max) return clean;
        int end = Character.isHighSurrogate(clean.charAt(max - 1)) ? max - 1 : max;
        return clean.substring(0, end);
    }

    private static String literalIp(String value) {
        return value != null && value.length() <= 64 && value.matches("[0-9a-fA-F:.%]+") ? value : null;
    }
}
