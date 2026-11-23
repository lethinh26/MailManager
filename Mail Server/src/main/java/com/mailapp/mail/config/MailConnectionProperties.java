package com.mailapp.mail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cấu hình thuộc tính kết nối Mail (IMAP / SMTP).
 * Các giá trị mặc định trỏ về máy chủ Gmail, có thể cấu hình đè qua application.properties
 * (ví dụ: trỏ về GreenMail localhost khi test).
 *
 * @author Lê Thanh Cường (Module M07)
 */
@Component
public class MailConnectionProperties {

    @Value("${mail.gmail.imap.host:imap.gmail.com}")
    private String imapHost = "imap.gmail.com";

    @Value("${mail.gmail.imap.port:993}")
    private int imapPort = 993;

    @Value("${mail.gmail.smtp.host:smtp.gmail.com}")
    private String smtpHost = "smtp.gmail.com";

    @Value("${mail.gmail.smtp.port:587}")
    private int smtpPort = 587;

    @Value("${mail.gmail.connection-timeout-ms:12000}")
    private int connectionTimeoutMs = 12000;

    @Value("${mail.gmail.timeout-ms:35000}")
    private int timeoutMs = 35000;

    @Value("${mail.gmail.write-timeout-ms:35000}")
    private int writeTimeoutMs = 35000;

    @Value("${mail.gmail.retry.enabled:true}")
    private boolean retryEnabled = true;

    @Value("${mail.gmail.retry.max-attempts:2}")
    private int retryMaxAttempts = 2;

    @Value("${mail.gmail.retry.wait-duration-ms:300}")
    private long retryWaitDurationMs = 300;

    @Value("${mail.gmail.pool.enabled:true}")
    private boolean poolEnabled = true;

    @Value("${mail.gmail.pool.idle-seconds:60}")
    private int poolIdleSeconds = 60;

    @Value("${mail.gmail.pool.max-per-account:2}")
    private int poolMaxPerAccount = 2;

    @Value("${mail.gmail.imap.partial-fetch:true}")
    private boolean imapPartialFetch = true;

    @Value("${mail.gmail.imap.fetch-size:262144}")
    private int imapFetchSize = 262144;

    public String getImapHost() { return imapHost; }
    public void setImapHost(String imapHost) { this.imapHost = imapHost; }

    public int getImapPort() { return imapPort; }
    public void setImapPort(int imapPort) { this.imapPort = imapPort; }

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int smtpPort) { this.smtpPort = smtpPort; }

    public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(int connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public int getWriteTimeoutMs() { return writeTimeoutMs; }
    public void setWriteTimeoutMs(int writeTimeoutMs) { this.writeTimeoutMs = writeTimeoutMs; }

    public boolean isRetryEnabled() { return retryEnabled; }
    public void setRetryEnabled(boolean retryEnabled) { this.retryEnabled = retryEnabled; }

    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }

    public long getRetryWaitDurationMs() { return retryWaitDurationMs; }
    public void setRetryWaitDurationMs(long retryWaitDurationMs) { this.retryWaitDurationMs = retryWaitDurationMs; }

    public boolean isPoolEnabled() { return poolEnabled; }
    public void setPoolEnabled(boolean poolEnabled) { this.poolEnabled = poolEnabled; }

    public int getPoolIdleSeconds() { return poolIdleSeconds; }
    public void setPoolIdleSeconds(int poolIdleSeconds) { this.poolIdleSeconds = poolIdleSeconds; }

    public int getPoolMaxPerAccount() { return poolMaxPerAccount; }
    public void setPoolMaxPerAccount(int poolMaxPerAccount) { this.poolMaxPerAccount = poolMaxPerAccount; }

    public boolean isImapPartialFetch() { return imapPartialFetch; }
    public void setImapPartialFetch(boolean imapPartialFetch) { this.imapPartialFetch = imapPartialFetch; }

    public int getImapFetchSize() { return imapFetchSize; }
    public void setImapFetchSize(int imapFetchSize) { this.imapFetchSize = imapFetchSize; }
}

