-- ==============================================================================
-- Flyway Database Migration V2: Bổ sung cột last_verified_at cho gmail_accounts
-- Theo dõi thời điểm kiểm tra/xác thực kết nối gần nhất (EP-G1/EP-G2/OAuth2)
-- ==============================================================================
ALTER TABLE gmail_accounts
    ADD COLUMN last_verified_at TIMESTAMP NULL AFTER token_expires_at;

