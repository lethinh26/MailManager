package com.mailapp.repository;

import com.mailapp.exception.GmailAccountChangedException;
import com.mailapp.model.GmailAccount;
import org.springframework.dao.OptimisticLockingFailureException;

/** Checks metadata only: no credential reload or substitution for a stale caller. */
public final class GmailAccountSnapshot {
    private GmailAccountSnapshot() {}

    public static void requireCurrent(GmailAccountRepository repository, GmailAccount account) {
        // Unsaved accounts used by isolated factory callers have no DB revision.
        if (repository == null || account.getId() == null || account.getRevision() == null) return;
        if (account.getUser() == null || !repository.existsByIdAndUserIdAndRevision(
                account.getId(), account.getUser().getId(), account.getRevision())) {
            throw new GmailAccountChangedException();
        }
    }

    public static void saveState(GmailAccountRepository repository, GmailAccount account) {
        try {
            GmailAccount saved = repository.save(account);
            // For a persisted detached mailbox, the next operation in the
            // same request must use the version of ITS OWN committed update.
            if (account.getRevision() != null) account.acceptOwnSave(saved);
        } catch (OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new GmailAccountChangedException();
        }
    }
}
