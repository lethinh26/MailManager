-- Protect detached/managed stale GmailAccount updates and deletes.
-- Existing accounts retain their IDs, methods and all credential ciphertext.
ALTER TABLE gmail_accounts ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
