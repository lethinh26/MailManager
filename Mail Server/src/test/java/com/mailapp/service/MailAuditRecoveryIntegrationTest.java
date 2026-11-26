package com.mailapp.service;

import com.mailapp.exception.AppException;
import com.mailapp.model.MailAuditOutbox;
import com.mailapp.repository.AuditLogRepository;
import com.mailapp.repository.MailAuditOutboxRepository;
import com.mailapp.repository.UserRepository;
import com.mailapp.service.impl.MailAuditRecovery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mailapp.security.AesCipher;
import com.mailapp.service.impl.MailAuditReceiptJournal;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real H2 transactions on a dedicated database; no auth endpoint or mail transport. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mail_audit_recovery;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "mail.audit.replay-delay-ms=3600000"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MailAuditRecoveryIntegrationTest {
    @Autowired MailAuditRecovery recovery;
    @Autowired MailAuditOutboxRepository outbox;
    @Autowired AuditLogRepository audits;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AesCipher cipher;
    @TempDir Path receiptRoot;

    @BeforeEach
    @AfterEach
    void resetOwnedDatabase() {
        jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS fixture_reject_audit");
        jdbc.execute("ALTER TABLE mail_audit_outbox DROP CONSTRAINT IF EXISTS fixture_reject_outbox");
        outbox.deleteAll();
        audits.deleteAll();
    }

    private String prepare() {
        return recovery.prepare(999_999L, "SEND_MAIL", "gmailId=10, recipientCount=1, attachmentCount=0", "127.0.0.1");
    }

    private long count(String action) {
        return audits.findByActionOrderByCreatedAtDesc(action).size();
    }

    @Test
    void confirmedEventSurvivesAuditFailureAndConcurrentReplayAfterRestart() throws Exception {
        String event = prepare();
        var occurredAt = outbox.findById(event).orElseThrow().getOccurredAt();
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT fixture_reject_audit CHECK (action <> 'SEND_MAIL')");
        assertDoesNotThrow(() -> recovery.finish(event, true, null));
        assertEquals(MailAuditOutbox.CONFIRMED, outbox.findById(event).orElseThrow().getOutcome());
        recovery.replay();
        assertEquals(0, count("SEND_MAIL"));
        assertTrue(outbox.existsById(event));
        jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT fixture_reject_audit");
        jdbc.update("UPDATE mail_audit_outbox SET next_attempt_at=NULL"); // advance only the isolated test fixture
        // New service objects have empty in-memory maps: only persisted intent
        // is used, and concurrent workers must not duplicate the event.
        var fresh = new MailAuditRecovery(outbox, audits, users, manager);
        var second = new MailAuditRecovery(outbox, audits, users, manager);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(fresh::replay);
            var b = pool.submit(second::replay);
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }
        assertFalse(outbox.existsById(event));
        assertEquals(1, count("SEND_MAIL"));
        var saved = audits.findByActionOrderByCreatedAtDesc("SEND_MAIL").getFirst();
        assertNull(saved.getUser()); // Deleted/missing actor must not drop the event.
        assertEquals(event, saved.getMailEventId());
        assertEquals(999_999L, saved.getMailActorId());
        assertEquals("gmailId=10, recipientCount=1, attachmentCount=0", saved.getDetail());
        assertEquals(occurredAt.withNano(0), saved.getCreatedAt().withNano(0));
        fresh.finish(event, true, null); // A repeated confirmation cannot create a new event.
        fresh.replay();
        assertEquals(1, count("SEND_MAIL"));
    }

    @Test
    void unresolvedIntentNeverBecomesSuccessAndUncertainOutcomeIsSeparate() {
        String event = prepare();
        recovery.replay();
        assertEquals(MailAuditOutbox.PREPARED, outbox.findById(event).orElseThrow().getOutcome());
        assertEquals(0, count("SEND_MAIL"));
        recovery.finish(event, false, null);
        recovery.replay();
        assertFalse(outbox.existsById(event));
        assertEquals(0, count("SEND_MAIL"));
        assertEquals(1, count("MAIL_OPERATION_UNCERTAIN"));
        assertTrue(audits.findByActionOrderByCreatedAtDesc("MAIL_OPERATION_UNCERTAIN")
                .getFirst().getDetail().startsWith("operation=SEND_MAIL;"));
    }

    @Test
    void outcomeStorageFailureRemainsPendingAndSameProcessRecoversConfirmation() {
        String event = prepare();
        jdbc.execute("ALTER TABLE mail_audit_outbox ADD CONSTRAINT fixture_reject_outbox CHECK (outcome <> 'CONFIRMED')");
        assertDoesNotThrow(() -> recovery.finish(event, true, null));
        assertEquals(MailAuditOutbox.PREPARED, outbox.findById(event).orElseThrow().getOutcome());
        assertEquals(0, count("SEND_MAIL"));
        jdbc.execute("ALTER TABLE mail_audit_outbox DROP CONSTRAINT fixture_reject_outbox");
        recovery.replay();
        recovery.replay();
        assertFalse(outbox.existsById(event));
        assertEquals(1, count("SEND_MAIL"));
    }

    @Test
    void rejectedPrepareFailsClosedBeforeAnyExternalOperationCanStart() {
        jdbc.execute("ALTER TABLE mail_audit_outbox ADD CONSTRAINT fixture_reject_outbox CHECK (outcome <> 'PREPARED')");
        AppException failure = assertThrows(AppException.class, this::prepare);
        assertEquals("AUDIT_UNAVAILABLE", failure.getCode());
        assertEquals(503, failure.getStatus().value());
        assertEquals(0, outbox.count());
        assertEquals(0, count("SEND_MAIL"));
    }

    @Test
    void stuckOldestBatchCannotStarveLaterDeliverableEvent() {
        var now = LocalDateTime.now().minusHours(1);
        var stuck = new ArrayList<MailAuditOutbox>();
        for (int i = 0; i < 32; i++) {
            var row = new MailAuditOutbox(UUID.randomUUID().toString(), null, "SEND_MAIL",
                    "recipientCount=1", null, now.plusSeconds(i));
            row.finish(MailAuditOutbox.CONFIRMED, null);
            stuck.add(row);
        }
        outbox.saveAllAndFlush(stuck);
        var good = new MailAuditOutbox(UUID.randomUUID().toString(), null, "MAIL_FLAG_CHANGE",
                "gmailId=10, uid=7, seen=true", null, now.plusMinutes(1));
        good.finish(MailAuditOutbox.CONFIRMED, null);
        outbox.saveAndFlush(good);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT fixture_reject_audit CHECK (action <> 'SEND_MAIL')");
        var fresh = new MailAuditRecovery(outbox, audits, users, manager);
        fresh.replay();
        assertEquals(33, outbox.count());
        fresh.replay();
        assertEquals(32, outbox.count());
        assertEquals(1, count("MAIL_FLAG_CHANGE"));
        assertEquals(0, count("SEND_MAIL"));
        assertEquals(32, fresh.status().confirmed());
        jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT fixture_reject_audit");
        jdbc.update("UPDATE mail_audit_outbox SET next_attempt_at=NULL");
        fresh.replay();
        fresh.replay();
        assertEquals(0, outbox.count());
        assertEquals(32, count("SEND_MAIL"));
        assertEquals(1, count("MAIL_FLAG_CHANGE"));
    }

    @Test
    void outcomeOutageBackpressuresBeforeRemoteMutationAndRecoversAllReservedSlots() {
        var limited = new MailAuditRecovery(outbox, audits, users, manager, 4);
        var events = new ArrayList<String>();
        jdbc.execute("ALTER TABLE mail_audit_outbox ADD CONSTRAINT fixture_reject_outbox CHECK (outcome = 'PREPARED')");
        for (int i = 0; i < 4; i++) {
            events.add(limited.prepare(null, "SEND_MAIL", "recipientCount=1", null));
        }
        assertEquals(4, limited.status().localInFlight());
        assertEquals(4, limited.status().prepared());
        for (String event : events) limited.finish(event, true, null);
        assertEquals(4, limited.status().localDeferred());
        assertEquals(0, limited.status().localInFlight());
        assertEquals(0, count("SEND_MAIL"));
        var denied = assertThrows(AppException.class,
                () -> limited.prepare(null, "SEND_MAIL", "recipientCount=1", null));
        assertEquals("AUDIT_UNAVAILABLE", denied.getCode());
        assertEquals(503, denied.getStatus().value());
        assertEquals(4, outbox.count()); // no extra durable intent / no remote operation admitted
        limited.replay();
        assertEquals(4, limited.status().localDeferred());
        jdbc.execute("ALTER TABLE mail_audit_outbox DROP CONSTRAINT fixture_reject_outbox");
        // A conflicting repeat cannot replace a previously observed outcome.
        limited.finish(events.getFirst(), false, null);
        limited.replay();
        assertEquals(4, count("SEND_MAIL"));
        assertEquals(0, count("MAIL_OPERATION_UNCERTAIN"));
        assertEquals(0, outbox.count());
        assertEquals(0, limited.status().localDeferred());
        String admitted = limited.prepare(null, "MAIL_FLAG_CHANGE", "gmailId=10, uid=7", null);
        limited.finish(admitted, false, null);
        assertEquals(1, count("MAIL_OPERATION_UNCERTAIN"));
    }

    @Test
    void staleIntentRemainsUnknownAfterRestartAndStatusIsAggregateOnly() throws Exception {
        var occurredAt = LocalDateTime.now().minusMinutes(11);
        var unresolved = new MailAuditOutbox(UUID.randomUUID().toString(), null, "MAIL_DELETE",
                "gmailId=10, uid=7", null, occurredAt);
        outbox.saveAndFlush(unresolved);
        var fresh = new MailAuditRecovery(outbox, audits, users, manager);
        fresh.replay();
        var status = fresh.status();
        assertEquals(1, status.prepared());
        assertEquals(1, status.stalePrepared());
        assertEquals(0, status.confirmed());
        assertEquals(0, status.uncertain());
        assertEquals(0, status.localDeferred());
        assertEquals(0, status.localInFlight());
        assertNotNull(status.oldestPreparedAt());
        assertNull(status.oldestReadyAt());
        assertEquals(0, count("MAIL_DELETE"));
        assertEquals(MailAuditOutbox.PREPARED, outbox.findById(unresolved.getId()).orElseThrow().getOutcome());
        assertThrows(IllegalArgumentException.class, () -> unresolved.finish("SUCCESS", null));
        var events = fresh.events(MailAuditOutbox.PREPARED, 0, 10);
        assertEquals(1, events.totalElements());
        assertEquals(unresolved.getId(), events.content().getFirst().eventId());
        assertTrue(events.content().getFirst().stalePrepared());
        assertThrows(AppException.class, () -> fresh.events("SUCCESS", 0, 10));
        mvc.perform(get("/api/admin/mail-audit/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.prepared").doesNotExist());
        mvc.perform(get("/api/admin/mail-audit/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void receiptSurvivesProcessObjectRestartWhenOutcomeCommitWasRejected() throws Exception {
        String id;
        Path dir = receiptRoot.resolve("private");
        try (var journal = new MailAuditReceiptJournal(cipher, dir.toString())) {
            var service = new MailAuditRecovery(outbox, audits, users, manager, 2, 10_000, journal);
            id = service.prepare(null, "MAIL_FLAG_CHANGE", "gmailId=7, uid=9", "127.0.0.1");
            jdbc.execute("ALTER TABLE mail_audit_outbox ADD CONSTRAINT fixture_reject_outbox CHECK (outcome = 'PREPARED')");
            service.finish(id, true, "gmailId=7, uid=9, seen=true");
            assertEquals(1, service.status().localDeferred());
            assertEquals(MailAuditOutbox.PREPARED, outbox.findById(id).orElseThrow().getOutcome());
            assertEquals(0, count("MAIL_FLAG_CHANGE"));
            assertTrue(Files.isRegularFile(dir.resolve(id + ".receipt"))
                    || Files.isRegularFile(dir.resolve(id + ".pending")));
        }
        jdbc.execute("ALTER TABLE mail_audit_outbox DROP CONSTRAINT fixture_reject_outbox");
        try (var journal = new MailAuditReceiptJournal(cipher, dir.toString())) {
            var service = new MailAuditRecovery(outbox, audits, users, manager, 2, 10_000, journal);
            assertEquals(1, service.status().localDeferred());
            service.replay();
            assertEquals(0, outbox.count());
            assertEquals(1, count("MAIL_FLAG_CHANGE"));
            assertEquals(id, audits.findByActionOrderByCreatedAtDesc("MAIL_FLAG_CHANGE").getFirst().getMailEventId());
            assertEquals(0, service.status().localDeferred());
            assertTrue(journal.recover().isEmpty());
            service.replay();
            assertEquals(1, count("MAIL_FLAG_CHANGE"));
        }
    }

    @Test
    void deliveryBackoffIsDurableAndNotResetByNewServiceInstance() {
        String event = prepare();
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT fixture_reject_audit CHECK (action <> 'SEND_MAIL')");
        recovery.finish(event, true, null);
        var failed = outbox.findById(event).orElseThrow();
        assertEquals(1, failed.getDeliveryAttempts());
        assertNotNull(failed.getNextAttemptAt());
        jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT fixture_reject_audit");
        var restarted = new MailAuditRecovery(outbox, audits, users, manager);
        restarted.replay();
        assertEquals(0, count("SEND_MAIL"));
        assertEquals(failed.getNextAttemptAt(), outbox.findById(event).orElseThrow().getNextAttemptAt());
        jdbc.update("UPDATE mail_audit_outbox SET next_attempt_at=NULL");
        restarted.replay();
        assertEquals(1, count("SEND_MAIL"));
        assertEquals(0, outbox.count());
    }

    @Test
    void corruptReceiptBlocksNewIntentsAndCannotInventSuccess() throws Exception {
        Path dir = receiptRoot.resolve("corrupt");
        String id;
        try (var journal = new MailAuditReceiptJournal(cipher, dir.toString())) {
            var service = new MailAuditRecovery(outbox, audits, users, manager, 2, 10_000, journal);
            id = service.prepare(null, "SEND_MAIL", "recipientCount=1", null);
            journal.write(new MailAuditReceiptJournal.Receipt(id, MailAuditOutbox.CONFIRMED, null));
        }
        Path receipt = Files.exists(dir.resolve(id + ".receipt")) ? dir.resolve(id + ".receipt") : dir.resolve(id + ".pending");
        Files.writeString(receipt, "not-an-authenticated-receipt");
        try (var journal = new MailAuditReceiptJournal(cipher, dir.toString())) {
            var restarted = new MailAuditRecovery(outbox, audits, users, manager, 2, 10_000, journal);
            assertFalse(restarted.status().receiptJournalAvailable());
            assertEquals("AUDIT_UNAVAILABLE", assertThrows(AppException.class,
                    () -> restarted.prepare(null, "SEND_MAIL", "recipientCount=1", null)).getCode());
            restarted.replay();
            assertEquals(0, count("SEND_MAIL"));
            assertEquals(MailAuditOutbox.PREPARED, outbox.findById(id).orElseThrow().getOutcome());
            assertTrue(Files.exists(receipt));
        }
    }

    @Test
    void runtimeReceiptFailureBlocksAdmissionAndNeverDeletesConflictingSibling() throws Exception {
        Path dir = receiptRoot.resolve("runtime");
        try (var journal = new MailAuditReceiptJournal(cipher, dir.toString())) {
            var service = new MailAuditRecovery(outbox, audits, users, manager, 2, 10_000, journal);
            String id = service.prepare(null, "SEND_MAIL", "recipientCount=1", null);
            // Synthetic fault: the write cannot complete because a corrupt
            // pending sibling already exists. No production files or mail.
            Path damaged = dir.resolve(id + ".pending");
            Files.writeString(damaged, "incomplete synthetic receipt");
            jdbc.execute("ALTER TABLE mail_audit_outbox ADD CONSTRAINT fixture_reject_outbox CHECK (outcome = 'PREPARED')");
            service.finish(id, true, null);
            assertFalse(service.status().receiptJournalAvailable());
            assertEquals(1, service.status().localDeferred());
            assertEquals(MailAuditOutbox.PREPARED, outbox.findById(id).orElseThrow().getOutcome());
            assertEquals(0, count("SEND_MAIL"));
            assertEquals("AUDIT_UNAVAILABLE", assertThrows(AppException.class,
                    () -> service.prepare(null, "SEND_MAIL", "recipientCount=1", null)).getCode());

            // DB recovers first. The original in-memory observation is still
            // valid, so it must be recorded exactly once without erasing the
            // damaged file or claiming the journal is healthy.
            jdbc.execute("ALTER TABLE mail_audit_outbox DROP CONSTRAINT fixture_reject_outbox");
            service.replay();
            assertEquals(1, count("SEND_MAIL"));
            assertFalse(service.status().receiptJournalAvailable());
            assertEquals(1, service.status().localDeferred());
            assertTrue(Files.isRegularFile(damaged));
            service.replay();
            assertEquals(1, count("SEND_MAIL"));
            assertTrue(Files.isRegularFile(damaged));

            // Remove only the fault file that this test created. The normal
            // service never deletes invalid receipts automatically.
            Files.delete(damaged);
            service.replay();
            assertTrue(service.status().receiptJournalAvailable());
            assertEquals(0, service.status().localDeferred());
            assertEquals(1, count("SEND_MAIL"));
        }
    }

    @Test
    void recoveredReceiptMustStillValidateBothSiblingsBeforeFinalization() throws Exception {
        Path dir = receiptRoot.resolve("sibling");
        String id;
        var first = new MailAuditReceiptJournal(cipher, dir.toString());
        try (first) {
            var service = new MailAuditRecovery(outbox, audits, users, manager, 2, 10_000, first);
            id = service.prepare(null, "SEND_MAIL", "recipientCount=1", null);
            first.write(new MailAuditReceiptJournal.Receipt(id, MailAuditOutbox.CONFIRMED, null));
        }
        try (var journal = new MailAuditReceiptJournal(cipher, dir.toString())) {
            var service = new MailAuditRecovery(outbox, audits, users, manager, 2, 10_000, journal);
            Path finalName = dir.resolve(id + ".receipt");
            Path pendingName = dir.resolve(id + ".pending");
            Path sibling = Files.exists(finalName) ? pendingName : finalName;
            Files.writeString(sibling, "conflicting synthetic sibling");
            service.replay();
            assertEquals(MailAuditOutbox.PREPARED, outbox.findById(id).orElseThrow().getOutcome());
            assertEquals(0, count("SEND_MAIL"));
            assertFalse(service.status().receiptJournalAvailable());
            assertTrue(Files.exists(sibling));
            assertTrue(Files.exists(finalName) && Files.exists(pendingName));
        }
    }

    @Test
    void unreservedFinishCannotTurnPreparedIntentIntoSuccess() {
        String event = prepare();
        var restarted = new MailAuditRecovery(outbox, audits, users, manager);
        restarted.finish(event, true, null);
        restarted.replay();
        assertEquals(MailAuditOutbox.PREPARED, outbox.findById(event).orElseThrow().getOutcome());
        assertEquals(0, count("SEND_MAIL"));
    }
}
