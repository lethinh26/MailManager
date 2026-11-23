package com.mailapp.mail;

import com.mailapp.exception.*;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.model.ConnectionTestResult;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import jakarta.mail.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/**
 * Cài đặt mặc định của MailConnectionFactory.
 * Bộ điều phối kết nối Gmail dùng chung cho toàn bộ dự án.
 *
 * Tính năng chính:
 * 1. Hỗ trợ cả 2 cơ chế xác thực: APP_PASSWORD và OAUTH2 (SASL XOAUTH2).
 * 2. Cấu hình bảo mật TLS bắt buộc (IMAPS 993, SMTP STARTTLS 587).
 * 3. Session tạo mới độc lập theo từng kết nối, debug=false, không dùng chung session toàn cục.
 * 4. Tích hợp Resilience4j Retry CHỈ cho lỗi mạng tạm thời, không bao giờ retry lỗi xác thực.
 * 5. Đo lường chỉ số Micrometer Timer "mail.connection.open".
 * 6. Gắn correlationId vào MDC, log kết nối luôn che email và tuyệt đối không lộ secret.
 *
 * @author Lê Thanh Cường (Module M07)
 */
@Component
@Primary
public class DefaultMailConnectionFactory implements MailConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(DefaultMailConnectionFactory.class);

    private final MailConnectionProperties properties;
    private final AesCipher aesCipher;
    private final AccessTokenProvider accessTokenProvider;
    private final MeterRegistry meterRegistry;
    private final GmailAccountRepository gmailAccountRepository;
    private final AuditLogService auditLogService;
    private final Retry networkRetry;

    /**
     * Pool kết nối IMAPS theo từng tài khoản Gmail (giữ ấm TCP + TLS 1.3 + XOAUTH2),
     * giảm độ trễ mở thư từ 3-15 giây xuống dưới 500ms và tránh nghẽn handshake tới imap.gmail.com.
     */
    private record PooledStore(Store store, long lastUsedAtMs) {}
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<PooledStore>> imapPool = new ConcurrentHashMap<>();

    public DefaultMailConnectionFactory(MailConnectionProperties properties,
                                        AesCipher aesCipher,
                                        AccessTokenProvider accessTokenProvider,
                                        MeterRegistry meterRegistry) {
        this(properties, aesCipher, accessTokenProvider, meterRegistry, null, null);
    }

    @Autowired
    public DefaultMailConnectionFactory(MailConnectionProperties properties,
                                        AesCipher aesCipher,
                                        AccessTokenProvider accessTokenProvider,
                                        @Autowired(required = false) MeterRegistry meterRegistry,
                                        @Autowired(required = false) GmailAccountRepository gmailAccountRepository,
                                        @Autowired(required = false) AuditLogService auditLogService) {
        this.properties = properties;
        this.aesCipher = aesCipher;
        this.accessTokenProvider = accessTokenProvider;
        this.meterRegistry = meterRegistry;
        this.gmailAccountRepository = gmailAccountRepository;
        this.auditLogService = auditLogService;
        this.networkRetry = buildNetworkRetry(properties);
    }

    /**
     * Lấy đối tượng Resilience4j Retry nội bộ (dành cho kiểm thử hoặc giám sát nâng cao).
     */
    Retry getNetworkRetry() {
        return networkRetry;
    }

    @Override
    public ImapConnection openImap(MailCredentials credentials) {
        Objects.requireNonNull(credentials, "MailCredentials không được null");
        return executeWithMonitoring("imap", credentials.authType(), credentials.email(), () -> {
            if (properties.isRetryEnabled()) {
                return Retry.decorateCheckedSupplier(networkRetry, () -> doOpenImap(credentials)).get();
            } else {
                return doOpenImap(credentials);
            }
        });
    }

    @Override
    public ImapConnection openImap(GmailAccount account) {
        Objects.requireNonNull(account, "GmailAccount không được null");
        return executeWithFallback(account, this::openImap);
    }

    @Override
    public SmtpConnection openSmtp(MailCredentials credentials) {
        Objects.requireNonNull(credentials, "MailCredentials không được null");
        return executeWithMonitoring("smtp", credentials.authType(), credentials.email(), () -> {
            if (properties.isRetryEnabled()) {
                return Retry.decorateCheckedSupplier(networkRetry, () -> doOpenSmtp(credentials)).get();
            } else {
                return doOpenSmtp(credentials);
            }
        });
    }

    @Override
    public SmtpConnection openSmtp(GmailAccount account) {
        Objects.requireNonNull(account, "GmailAccount không được null");
        return executeWithFallback(account, this::openSmtp);
    }

    @Override
    public ConnectionTestResult testImap(MailCredentials credentials) {
        Objects.requireNonNull(credentials, "MailCredentials không được null");
        long start = System.currentTimeMillis();
        String masked = MailCredentials.maskEmail(credentials.email());

        try (ImapConnection connection = openImap(credentials)) {
            if (connection.isConnected()) {
                long duration = System.currentTimeMillis() - start;
                log.info("[MDC: {}] Thử nghiệm kết nối IMAP thành công cho: {} trong {}ms", getOrSetCorrelationId(), masked, duration);
                return ConnectionTestResult.ok(duration);
            }
            return ConnectionTestResult.networkError("Không thể hoàn tất kết nối tới máy chủ IMAP.");
        } catch (ImapDisabledException e) {
            log.warn("[MDC: {}] Kiểm tra IMAP thất bại: IMAP đang bị tắt cho {}", getOrSetCorrelationId(), masked);
            return ConnectionTestResult.imapDisabled(e.getMessage());
        } catch (MailAuthException e) {
            log.warn("[MDC: {}] Kiểm tra IMAP thất bại: Xác thực không thành công cho {}", getOrSetCorrelationId(), masked);
            String rawMsg = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage().toLowerCase() : "";
            if (rawMsg.contains("application-specific password")
                    || rawMsg.contains("second factor")
                    || rawMsg.contains("2-step")
                    || rawMsg.contains("less secure")) {
                return ConnectionTestResult.twoStepRequired(null);
            }
            return ConnectionTestResult.authFailed(e.getMessage());
        } catch (MailNetworkException e) {
            log.warn("[MDC: {}] Kiểm tra IMAP thất bại: Lỗi mạng hoặc timeout cho {}", getOrSetCorrelationId(), masked);
            return ConnectionTestResult.networkError(e.getMessage());
        } catch (Exception e) {
            log.error("[MDC: {}] Kiểm tra IMAP thất bại với lỗi không xác định cho {}", getOrSetCorrelationId(), masked, e);
            return ConnectionTestResult.networkError("Lỗi kết nối: " + e.getMessage());
        }
    }

    @Override
    public ConnectionTestResult testImap(String email, String rawAppPassword) {
        MailCredentials credentials = MailCredentials.ofAppPassword(email, rawAppPassword);
        return testImap(credentials);
    }

    @Override
    public ConnectionTestResult testImap(GmailAccount account) {
        Objects.requireNonNull(account, "GmailAccount không được null");
        long start = System.currentTimeMillis();
        try (ImapConnection conn = openImap(account)) {
            if (conn.isConnected()) {
                return ConnectionTestResult.ok(System.currentTimeMillis() - start);
            }
            return ConnectionTestResult.networkError("Không thể hoàn tất kết nối tới máy chủ IMAP.");
        } catch (ReauthRequiredException e) {
            return ConnectionTestResult.authFailed(e.getMessage());
        } catch (ImapDisabledException e) {
            return ConnectionTestResult.imapDisabled(e.getMessage());
        } catch (MailAuthException e) {
            return ConnectionTestResult.authFailed(e.getMessage());
        } catch (MailNetworkException e) {
            return ConnectionTestResult.networkError(e.getMessage());
        } catch (Exception e) {
            return ConnectionTestResult.networkError("Lỗi kết nối: " + e.getMessage());
        }
    }

    /**
     * Chọn credential theo preferredMethod (authType).
     * Nếu OAUTH2 ném ReauthRequiredException (hoặc đã ở NEEDS_REAUTH) mà hộp thư có App Password:
     * - Không retry lỗi xác thực nhiều lần với Google (tránh khóa tài khoản).
     * - Chuyển sang dùng APP_PASSWORD dự phòng đúng 1 lần cho thao tác đó.
     * - Đánh dấu fallbackActive=true, ghi audit FALLBACK_TO_APP_PASSWORD (không ghi bí mật),
     *   nhưng KHÔNG xóa trạng thái NEEDS_REAUTH của OAuth2.
     */
    private <T> T executeWithFallback(GmailAccount account, java.util.function.Function<MailCredentials, T> opener) {
        AuthType preferred = account.getPreferredMethod();
        Objects.requireNonNull(preferred, "AuthType trong GmailAccount không được null");

        if (preferred == AuthType.OAUTH2) {
            // Nếu OAuth2 đã ở trạng thái NEEDS_REAUTH trước đó và có App Password dự phòng:
            // đi thẳng sang App Password để không gọi refresh lặp lại tới Google
            if (account.getStatus() == AccountStatus.NEEDS_REAUTH && account.hasAppPassword()) {
                activateFallbackState(account, false);
                MailCredentials fallbackCreds = MailCredentials.forMethod(
                        account, AuthType.APP_PASSWORD, aesCipher, accessTokenProvider);
                return opener.apply(fallbackCreds);
            }

            try {
                MailCredentials oauthCreds = MailCredentials.forMethod(
                        account, AuthType.OAUTH2, aesCipher, accessTokenProvider);
                T connection = opener.apply(oauthCreds);
                if (account.isFallbackActive()) {
                    account.setFallbackActive(false);
                    if (gmailAccountRepository != null && account.getId() != null) {
                        gmailAccountRepository.save(account);
                    }
                }
                return connection;
            } catch (ReauthRequiredException reauthEx) {
                if (!account.hasAppPassword()) {
                    throw reauthEx;
                }
                // Giữ nguyên NEEDS_REAUTH của OAuth2, bật fallbackActive và ghi audit
                activateFallbackState(account, true);
                MailCredentials fallbackCreds = MailCredentials.forMethod(
                        account, AuthType.APP_PASSWORD, aesCipher, accessTokenProvider);
                return opener.apply(fallbackCreds);
            }
        }

        MailCredentials credentials = MailCredentials.forMethod(account, preferred, aesCipher, accessTokenProvider);
        return opener.apply(credentials);
    }

    private void activateFallbackState(GmailAccount account, boolean forceAudit) {
        String masked = MailCredentials.maskEmail(account.getEmail());
        boolean newlyActivated = !account.isFallbackActive();

        // Đảm bảo KHÔNG xóa trạng thái NEEDS_REAUTH của OAuth2
        account.setStatus(AccountStatus.NEEDS_REAUTH);
        account.setFallbackActive(true);

        if (gmailAccountRepository != null && (newlyActivated || forceAudit)) {
            try {
                gmailAccountRepository.save(account);
            } catch (Exception e) {
                log.warn("Không thể lưu trạng thái fallbackActive cho {}: {}", masked, e.getMessage());
            }
        }

        if (auditLogService != null && (forceAudit || newlyActivated)) {
            Long userId = account.getUser() != null ? account.getUser().getId() : null;
            auditLogService.log(userId, "FALLBACK_TO_APP_PASSWORD",
                    "OAuth2 cần cấp lại quyền cho " + masked + "; tự động sử dụng App Password dự phòng.", null);
        }
        log.warn("[MDC: {}] Kích hoạt App Password dự phòng cho {} (giữ nguyên NEEDS_REAUTH của OAuth2).",
                getOrSetCorrelationId(), masked);
    }

    // =========================================================================
    // Core Open Logic (Low-level Jakarta Mail)
    // =========================================================================

    private ImapConnection doOpenImap(MailCredentials credentials) throws Exception {
        String masked = MailCredentials.maskEmail(credentials.email());
        boolean usePool = properties.isPoolEnabled() && !isLocalhost(properties.getImapHost());
        String poolKey = usePool ? buildPoolKey(credentials) : null;

        // 1. Ưu tiên mượn kết nối IMAPS ấm từ pool nếu còn khỏe mạnh và chưa quá idleSeconds
        if (usePool) {
            ImapConnection reused = borrowFromPool(poolKey, masked);
            if (reused != null) {
                return reused;
            }
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", properties.getImapHost());
        props.put("mail.imaps.port", String.valueOf(properties.getImapPort()));

        // TLS cấu hình chuẩn
        props.put("mail.imaps.ssl.enable", "true");
        if (isLocalhost(properties.getImapHost())) {
            props.put("mail.imaps.ssl.trust", "*");
            props.put("mail.imaps.ssl.checkserveridentity", "false");
        } else {
            props.put("mail.imaps.ssl.checkserveridentity", "true");
            props.put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3");
        }

        // Timeouts
        props.put("mail.imaps.connectiontimeout", String.valueOf(properties.getConnectionTimeoutMs()));
        props.put("mail.imaps.timeout", String.valueOf(properties.getTimeoutMs()));
        props.put("mail.imaps.writetimeout", String.valueOf(properties.getWriteTimeoutMs()));
        // Tối ưu kích thước khối đọc IMAP (partialfetch=true, fetchsize=256KB mặc định) để stream đính kèm lớn không đệm toàn bộ vào heap
        props.put("mail.imaps.partialfetch", String.valueOf(properties.isImapPartialFetch()));
        props.put("mail.imaps.fetchsize", String.valueOf(properties.getImapFetchSize()));
        props.put("mail.imaps.peek", "true");

        if (credentials.authType() == AuthType.APP_PASSWORD) {
            props.put("mail.imaps.auth", "true");
        } else if (credentials.authType() == AuthType.OAUTH2) {
            props.put("mail.imaps.auth.mechanisms", "XOAUTH2");
            props.put("mail.imaps.sasl.enable", "true");
            props.put("mail.imaps.sasl.mechanisms", "XOAUTH2");
            props.put("mail.imaps.auth.login.disable", "true");
            props.put("mail.imaps.auth.plain.disable", "true");
        }

        // Tạo Session mới mỗi lần mở, debug=false
        Session session = Session.getInstance(props);
        session.setDebug(false);

        Store store = session.getStore("imaps");
        try {
            store.connect(properties.getImapHost(), properties.getImapPort(), credentials.email(), credentials.secret());
            if (usePool) {
                return new ImapConnection(store, masked, conn -> returnToPool(poolKey, conn));
            }
            return new ImapConnection(store, masked);
        } catch (Exception e) {
            if (store != null && store.isConnected()) {
                try { store.close(); } catch (Exception ignored) {}
            }
            throw mapException(e, credentials.email());
        }
    }

    private ImapConnection borrowFromPool(String poolKey, String masked) {
        ConcurrentLinkedDeque<PooledStore> deque = imapPool.get(poolKey);
        if (deque == null) return null;
        long maxIdleMs = Math.max(10, properties.getPoolIdleSeconds()) * 1000L;
        long now = System.currentTimeMillis();

        PooledStore entry;
        while ((entry = deque.pollFirst()) != null) {
            boolean expired = (now - entry.lastUsedAtMs()) > maxIdleMs;
            if (!expired && entry.store().isConnected()) {
                log.debug("Tái sử dụng kết nối IMAPS từ pool cho: {}", masked);
                return new ImapConnection(entry.store(), masked, conn -> returnToPool(poolKey, conn));
            }
            try {
                if (entry.store().isConnected()) entry.store().close();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void returnToPool(String poolKey, ImapConnection conn) {
        if (conn == null || conn.isBroken() || !conn.isConnected()) {
            if (conn != null) conn.forceCloseStore();
            return;
        }
        int maxPerAccount = Math.max(1, properties.getPoolMaxPerAccount());
        ConcurrentLinkedDeque<PooledStore> deque = imapPool.computeIfAbsent(poolKey, k -> new ConcurrentLinkedDeque<>());
        if (deque.size() >= maxPerAccount) {
            conn.forceCloseStore();
            return;
        }
        deque.offerFirst(new PooledStore(conn.getStore(), System.currentTimeMillis()));
    }

    private String buildPoolKey(MailCredentials credentials) {
        String secretSig;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(credentials.secret()).getBytes(StandardCharsets.UTF_8));
            secretSig = Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            secretSig = String.valueOf(Objects.hashCode(credentials.secret()));
        }
        return properties.getImapHost() + ":" + properties.getImapPort()
                + "|" + credentials.email().toLowerCase()
                + "|" + credentials.authType()
                + "|" + secretSig;
    }

    @PreDestroy
    public void shutdownPool() {
        for (ConcurrentLinkedDeque<PooledStore> deque : imapPool.values()) {
            PooledStore entry;
            while ((entry = deque.pollFirst()) != null) {
                try {
                    if (entry.store().isConnected()) entry.store().close();
                } catch (Exception ignored) {}
            }
        }
        imapPool.clear();
    }

    private SmtpConnection doOpenSmtp(MailCredentials credentials) throws Exception {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", properties.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(properties.getSmtpPort()));

        props.put("mail.smtp.auth", "true");
        if (isLocalhost(properties.getSmtpHost())) {
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.ssl.checkserveridentity", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            props.put("mail.smtp.ssl.checkserveridentity", "true");
        }

        // Timeouts
        props.put("mail.smtp.connectiontimeout", String.valueOf(properties.getConnectionTimeoutMs()));
        props.put("mail.smtp.timeout", String.valueOf(properties.getTimeoutMs()));
        props.put("mail.smtp.writetimeout", String.valueOf(properties.getWriteTimeoutMs()));

        if (credentials.authType() == AuthType.OAUTH2) {
            props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
            props.put("mail.smtp.sasl.enable", "true");
            props.put("mail.smtp.sasl.mechanisms", "XOAUTH2");
            props.put("mail.smtp.auth.login.disable", "true");
            props.put("mail.smtp.auth.plain.disable", "true");
        }

        // Tạo Session mới mỗi lần mở, debug=false
        Session session;
        if (credentials.authType() == AuthType.APP_PASSWORD) {
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(credentials.email(), credentials.secret());
                }
            });
        } else {
            session = Session.getInstance(props);
        }
        session.setDebug(false);

        Transport transport = session.getTransport("smtp");
        try {
            transport.connect(properties.getSmtpHost(), properties.getSmtpPort(), credentials.email(), credentials.secret());
            return new SmtpConnection(transport, session, MailCredentials.maskEmail(credentials.email()));
        } catch (Exception e) {
            if (transport != null && transport.isConnected()) {
                try { transport.close(); } catch (Exception ignored) {}
            }
            throw mapException(e, credentials.email());
        }
    }

    // =========================================================================
    // Resilience4j, Metrics & Logging Helpers
    // =========================================================================

    private boolean isLocalhost(String host) {
        return "127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "host.docker.internal".equalsIgnoreCase(host);
    }

    private Retry buildNetworkRetry(MailConnectionProperties props) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(props.getRetryMaxAttempts())
                .waitDuration(Duration.ofMillis(props.getRetryWaitDurationMs()))
                .retryOnException(this::isNetworkException)
                .ignoreExceptions(
                        MailAuthException.class,
                        ReauthRequiredException.class,
                        ImapDisabledException.class,
                        AuthenticationFailedException.class
                )
                .build();
        return Retry.of("mailNetworkRetry", config);
    }

    private boolean isNetworkException(Throwable throwable) {
        if (throwable instanceof MailNetworkException) {
            return true;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException ||
                current instanceof ConnectException ||
                current instanceof UnknownHostException ||
                current instanceof SSLHandshakeException ||
                current instanceof SSLException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T> T executeWithMonitoring(String protocol, AuthType authType, String email, CheckedSupplier<T> supplier) {
        String correlationId = getOrSetCorrelationId();
        String masked = MailCredentials.maskEmail(email);
        long start = System.currentTimeMillis();
        boolean success = false;

        log.debug("[MDC: {}] Bắt đầu mở kết nối {} cho email: {}, authType: {}", correlationId, protocol, masked, authType);

        try {
            T result = supplier.get();
            success = true;
            long duration = System.currentTimeMillis() - start;
            log.info("[MDC: {}] Mở kết nối {} thành công cho: {} trong {}ms", correlationId, protocol, masked, duration);
            recordMetric(protocol, authType, true, duration);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("[MDC: {}] Mở kết nối {} thất bại cho: {} sau {}ms. Nguyên nhân: {}",
                    correlationId, protocol, masked, duration, e.getMessage());
            recordMetric(protocol, authType, false, duration);

            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new MailNetworkException("Không thể hoàn tất kết nối tới máy chủ Mail.", e);
        }
    }

    private void recordMetric(String protocol, AuthType authType, boolean success, long durationMs) {
        if (meterRegistry != null) {
            try {
                Timer.builder("mail.connection.open")
                        .description("Thời gian mở kết nối Mail (IMAP / SMTP)")
                        .tag("protocol", protocol)
                        .tag("auth_type", authType != null ? authType.name().toLowerCase() : "unknown")
                        .tag("result", success ? "success" : "error")
                        .register(meterRegistry)
                        .record(durationMs, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {}
        }
    }

    private String getOrSetCorrelationId() {
        String cid = MDC.get("correlationId");
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("correlationId", cid);
        }
        return cid;
    }

    private MailConnectionException mapException(Exception e, String email) {
        if (e instanceof MailConnectionException mce) {
            return mce;
        }

        Throwable current = e;
        while (current != null) {
            if (current instanceof AuthenticationFailedException afe) {
                String msg = afe.getMessage() != null ? afe.getMessage().toLowerCase() : "";
                if (msg.contains("imap is disabled") || msg.contains("web browser") || msg.contains("please log in via your web browser")) {
                    return new ImapDisabledException(
                            "Tài khoản Gmail chưa bật tính năng IMAP. Vào Gmail Web -> Cài đặt -> Chuyển tiếp và POP/IMAP -> Bật IMAP.", afe);
                }
                return new MailAuthException("Xác thực Gmail thất bại. Vui lòng kiểm tra lại App Password hoặc quyền truy cập.", afe);
            }
            if (current instanceof SocketTimeoutException ste) {
                return new MailNetworkException("Hết thời gian chờ kết nối tới máy chủ Gmail (Socket Timeout).", ste, true);
            }
            if (current instanceof ConnectException || current instanceof UnknownHostException) {
                return new MailNetworkException("Không thể kết nối đến máy chủ Gmail (Lỗi mạng hoặc DNS). Vui lòng thử lại sau.", current, false);
            }
            if (current instanceof SSLHandshakeException || current instanceof SSLException) {
                return new MailNetworkException("Lỗi bắt tay bảo mật TLS với máy chủ Gmail.", current, false);
            }
            current = current.getCause();
        }

        return new MailNetworkException("Không thể kết nối đến máy chủ Gmail: lỗi giao thức mạng.", e, false);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Throwable;
    }
}
