-- ==============================================================================
-- Flyway Database Migration V1: Khởi tạo 4 bảng chuẩn theo thiết kế kiến trúc
-- Module M01/M02/M06/M07/M14
-- ==============================================================================

-- 1. Bảng tài khoản hệ thống (users)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    email VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

-- 2. Bảng tài khoản Gmail đã liên kết (gmail_accounts)
CREATE TABLE IF NOT EXISTS gmail_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    auth_type VARCHAR(30) NOT NULL,
    app_password_encrypted TEXT NULL,
    refresh_token_encrypted TEXT NULL,
    token_expires_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT uk_user_gmail UNIQUE (user_id, email),
    CONSTRAINT fk_gmail_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Bảng cấu hình hệ thống (system_settings)
CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_system_settings_user FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

-- 4. Bảng nhật ký hệ thống (audit_logs)
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    action VARCHAR(50) NOT NULL,
    detail VARCHAR(500),
    ip_address VARCHAR(45),
    created_at TIMESTAMP NULL,
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- ==============================================================================
-- Dữ liệu hạt giống (Seed Data)
-- ==============================================================================

-- Seed cấu hình mặc định (Mục 6.3 tài liệu thiết kế)
INSERT INTO system_settings (setting_key, setting_value, description, updated_at) VALUES
('ALLOW_REGISTRATION', 'true', 'Cho phép người dùng tự đăng ký tài khoản mới', CURRENT_TIMESTAMP),
('ALLOW_OAUTH2_LINK', 'true', 'Cho phép liên kết tài khoản Gmail bằng OAuth2', CURRENT_TIMESTAMP),
('ALLOW_APP_PASSWORD_LINK', 'true', 'Cho phép liên kết tài khoản Gmail bằng App Password', CURRENT_TIMESTAMP),
('ALLOW_SEND_MAIL', 'true', 'Cho phép gửi mail qua SMTP', CURRENT_TIMESTAMP),
('MAINTENANCE_MODE', 'false', 'Chế độ bảo trì hệ thống (chỉ Admin đăng nhập được)', CURRENT_TIMESTAMP),
('MAX_GMAIL_PER_USER', '5', 'Số tài khoản Gmail tối đa mỗi người dùng được liên kết (0 = không giới hạn)', CURRENT_TIMESTAMP),
('MAX_ATTACHMENT_SIZE_MB', '25', 'Kích thước đính kèm tối đa tính bằng MB', CURRENT_TIMESTAMP);

-- Tài khoản ADMIN ban đầu được khởi tạo an toàn bởi AdminAccountInitializer từ biến môi trường ADMIN_INITIAL_PASSWORD.
