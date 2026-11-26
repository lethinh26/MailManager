package com.mailapp.repository;

import com.mailapp.model.MailAuditOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MailAuditOutboxRepository extends JpaRepository<MailAuditOutbox, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from MailAuditOutbox e where e.id = :id")
    Optional<MailAuditOutbox> lockById(@Param("id") String id);

    interface ReadyEvent {
        String getId();
        LocalDateTime getOccurredAt();
    }

    @Query("select max(e.occurredAt) from MailAuditOutbox e where e.outcome in ('CONFIRMED', 'UNCERTAIN') and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)")
    LocalDateTime latestReadyAt(@Param("now") LocalDateTime now);

    @Query("select e.id as id, e.occurredAt as occurredAt from MailAuditOutbox e where e.outcome in ('CONFIRMED', 'UNCERTAIN') and (e.nextAttemptAt is null or e.nextAttemptAt <= :now) and e.occurredAt <= :through order by e.occurredAt, e.id")
    List<ReadyEvent> readyEvents(@Param("through") LocalDateTime through, @Param("now") LocalDateTime now, Pageable page);

    @Query("select e.id as id, e.occurredAt as occurredAt from MailAuditOutbox e where e.outcome in ('CONFIRMED', 'UNCERTAIN') and (e.nextAttemptAt is null or e.nextAttemptAt <= :now) and e.occurredAt <= :through and (e.occurredAt > :afterTime or (e.occurredAt = :afterTime and e.id > :afterId)) order by e.occurredAt, e.id")
    List<ReadyEvent> readyEventsAfter(@Param("afterTime") LocalDateTime afterTime,
                                      @Param("afterId") String afterId,
                                      @Param("through") LocalDateTime through, @Param("now") LocalDateTime now, Pageable page);

    @Query("select e from MailAuditOutbox e where (:outcome is null or e.outcome = :outcome) order by e.occurredAt, e.id")
    Page<MailAuditOutbox> findEvents(@Param("outcome") String outcome, Pageable page);

    interface OutcomeCount {
        String getOutcome();
        long getTotal();
        LocalDateTime getOldest();
    }

    @Query("select e.outcome as outcome, count(e) as total, min(e.occurredAt) as oldest from MailAuditOutbox e group by e.outcome")
    List<OutcomeCount> outcomeCounts();

    long countByOutcomeAndOccurredAtBefore(String outcome, LocalDateTime before);
}
