-- Seed only missing settings; never overwrite a deployed administrator's value.
INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
SELECT 'ALLOW_REGISTRATION', 'true', 'Allow registration', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'ALLOW_REGISTRATION');
INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
SELECT 'ALLOW_OAUTH2_LINK', 'true', 'Allow Google OAuth2 linking', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'ALLOW_OAUTH2_LINK');
INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
SELECT 'ALLOW_APP_PASSWORD_LINK', 'true', 'Allow App Password linking', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'ALLOW_APP_PASSWORD_LINK');
INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
SELECT 'ALLOW_SEND_MAIL', 'true', 'Allow sending mail', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'ALLOW_SEND_MAIL');
INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
SELECT 'MAINTENANCE_MODE', 'false', 'Maintenance mode', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'MAINTENANCE_MODE');
INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
SELECT 'MAX_GMAIL_PER_USER', '5', 'Maximum linked mailboxes per user', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'MAX_GMAIL_PER_USER');
INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
SELECT 'MAX_ATTACHMENT_SIZE_MB', '25', 'Maximum attachment size in MB', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'MAX_ATTACHMENT_SIZE_MB');

INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
SELECT 'MAIL_PAGE_SIZE', '20', 'Default number of messages per page', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'MAIL_PAGE_SIZE');

INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
SELECT 'LOGIN_MAX_FAILED_ATTEMPTS', '5', 'Maximum failed login attempts before rate limit', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'LOGIN_MAX_FAILED_ATTEMPTS');
