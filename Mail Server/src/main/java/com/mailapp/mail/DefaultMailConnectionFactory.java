package com.mailapp.mail;

import com.mailapp.exception.*;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.model.ConnectionTestResult;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.repository.GmailAccountSnapshot;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    private record PooledStore(Store store, long lastUsedNanos) {}
    private final Object poolLock = new Object();
    private final Map<String, Deque<PooledStore>> imapPool = new HashMap<>();
    private boolean poolClosed;
    private ScheduledExecutorService poolMaintenance;

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
    public Store createImapStore(GmailAccount account) throws MessagingException {
        return createSession(account, Protocol.IMAP).getStore("imaps");
    }

    @Override
    public Transport createSmtpTransport(GmailAccount account) throws MessagingException {
        return createSession(account, Protocol.SMTP).getTransport("smtp");
    }

    @Override
    public Session createSession(GmailAccount account, Protocol protocol) {
        Objects.requireNonNull(account, "GmailAccount không được null");
        Objects.requireNonNull(protocol, "Protocol không được null");
        GmailAccountSnapshot.requireCurrent(gmailAccountRepository, account);
        // This adapter does not claim a successful login or change fallback
        // state before the caller performs authentication. Use open* for
        // actual connection orchestration, including fallback and retry.
        MailCredentials credentials = MailCredentials.forMethod(account, account.getPreferredMethod(),
                aesCipher, accessTokenProvider);
        boolean imap = protocol == Protocol.IMAP;
        String name = imap ? "imaps" : "smtp";
        String host = imap ? properties.getImapHost() : properties.getSmtpHost();
        int port = imap ? properties.getImapPort() : properties.getSmtpPort();
        Properties props = imap ? imapProperties(credentials, false) : smtpProperties(credentials);
        props.setProperty("mail." + name + ".user", credentials.email());
        return scopedSession(props, credentials, name, host, port);
    }

    private Session scopedSession(Properties props, MailCredentials credentials, String name, String host, int port) {
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                // Do not supply credentials for another host, protocol, port
                // or user if a caller accidentally overrides the defaults.
                if (!name.equalsIgnoreCase(getRequestingProtocol())
                        || getRequestingSite() == null
                        || (!host.equalsIgnoreCase(getRequestingSite().getHostName())
                            && !host.equals(getRequestingSite().getHostAddress()))
                        || (getRequestingPort() != -1 && getRequestingPort() != port)
                        || !credentials.email().equals(getDefaultUserName())) return null;
                return new PasswordAuthentication(credentials.email(), credentials.secret());
            }
        });
        session.setDebug(false);
        return session;
    }

    @Override
    public ImapConnection openImap(MailCredentials credentials) {
        return openImap(credentials, null);
    }

    private ImapConnection openImap(MailCredentials credentials, Long mailboxId) {
        Objects.requireNonNull(credentials, "MailCredentials không được null");
        return executeWithMonitoring("imap", credentials.authType(), credentials.email(), () -> {
            if (properties.isRetryEnabled()) {
                return Retry.decorateCheckedSupplier(networkRetry, () -> doOpenImap(credentials, false, mailboxId)).get();
            } else {
                return doOpenImap(credentials, false, mailboxId);
            }
        });
    }

    @Override
    public ImapConnection openImap(GmailAccount account) {
        Objects.requireNonNull(account, "GmailAccount không được null");
        return executeWithFallback(account, credentials -> openImap(credentials, account.getId()));
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

        // Probe submitted credentials with a fresh, unpooled connection, without
        // retrying network or authentication failures.
        try (ImapConnection connection = openImapProbe(credentials)) {
            if (connection.isConnected()) {
                long duration = System.currentTimeMillis() - start;
                log.info("[MDC: {}] Thử nghiệm kết nối IMAP thành công cho: {} trong {}ms", getOrSetCorrelationId(), masked, duration);
                return ConnectionTestResult.ok(duration);
            }
            return ConnectionTestResult.networkError("Không thể hoàn tất kết nối tới máy chủ IMAP.");
        } catch (ReauthRequiredException e) {
            return ConnectionTestResult.authFailed(e.getMessage());
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
            log.error("[MDC: {}] Kiểm tra IMAP thất bại với lỗi không xác định cho {}: {}",
                    getOrSetCorrelationId(), masked, e.getClass().getSimpleName());
            return ConnectionTestResult.networkError("Không thể hoàn tất kết nối tới máy chủ IMAP.");
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
        try (ImapConnection conn = executeWithFallback(account, this::openImapProbe)) {
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
            return ConnectionTestResult.networkError("Không thể hoàn tất kết nối tới máy chủ IMAP.");
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
    private <T extends AutoCloseable> T executeWithFallback(GmailAccount account, java.util.function.Function<MailCredentials, T> opener) {
        AuthType preferred = account.getPreferredMethod();
        Objects.requireNonNull(preferred, "AuthType trong GmailAccount không được null");
        GmailAccountSnapshot.requireCurrent(gmailAccountRepository, account);

        if (preferred == AuthType.OAUTH2) {
            // Nếu OAuth2 đã ở trạng thái NEEDS_REAUTH trước đó và có App Password dự phòng:
            // đi thẳng sang App Password để không gọi refresh lặp lại tới Google
            if (account.getStatus() == AccountStatus.NEEDS_REAUTH) {
                if (!account.hasAppPassword()) throw new ReauthRequiredException();
                return openFallback(account, opener);
            }

            try {
                MailCredentials oauthCreds = MailCredentials.forMethod(
                        account, AuthType.OAUTH2, aesCipher, accessTokenProvider);
                T connection = opener.apply(oauthCreds);
                try {
                    GmailAccountSnapshot.requireCurrent(gmailAccountRepository, account);
                    if (account.isFallbackActive()) {
                        account.setFallbackActive(false);
                        if (gmailAccountRepository != null && account.getId() != null) {
                            persistAccountState(account);
                        }
                    }
                    return connection;
                } catch (RuntimeException | Error e) {
                    // A successful open followed by a persistence failure must
                    // never abandon a live lease.
                    if (connection instanceof ImapConnection imap) imap.markBroken();
                    try { connection.close(); } catch (Exception ignored) {}
                    throw e;
                }
            } catch (ReauthRequiredException reauthEx) {
                if (account.getStatus() != AccountStatus.NEEDS_REAUTH || account.isFallbackActive()) {
                    account.setStatus(AccountStatus.NEEDS_REAUTH);
                    account.setFallbackActive(false);
                    persistAccountState(account);
                }
                if (!account.hasAppPassword()) throw reauthEx;
                return openFallback(account, opener);
            }
        }

        MailCredentials credentials = MailCredentials.forMethod(account, preferred, aesCipher, accessTokenProvider);
        T connection = opener.apply(credentials);
        try {
            GmailAccountSnapshot.requireCurrent(gmailAccountRepository, account);
            return connection;
        } catch (RuntimeException | Error e) {
            if (connection instanceof ImapConnection imap) imap.markBroken();
            try { connection.close(); } catch (Exception ignored) {}
            throw e;
        }
    }

    private <T extends AutoCloseable> T openFallback(GmailAccount account, java.util.function.Function<MailCredentials, T> opener) {
        String masked = MailCredentials.maskEmail(account.getEmail());
        T connection;
        try {
            GmailAccountSnapshot.requireCurrent(gmailAccountRepository, account);
            MailCredentials fallbackCreds = MailCredentials.forMethod(
                    account, AuthType.APP_PASSWORD, aesCipher, accessTokenProvider);
            connection = opener.apply(fallbackCreds);
        } catch (RuntimeException e) {
            // A failed backup is not an active fallback.
            if (account.isFallbackActive()) {
                account.setFallbackActive(false);
                persistAccountState(account);
            }
            throw e;
        }

        try {
            GmailAccountSnapshot.requireCurrent(gmailAccountRepository, account);
            if (!account.isFallbackActive()) {
                account.setFallbackActive(true);
                persistAccountState(account);
                if (auditLogService != null) {
                    Long userId = account.getUser() != null ? account.getUser().getId() : null;
                    auditLogService.log(userId, "FALLBACK_TO_APP_PASSWORD",
                            "OAuth2 cần cấp lại quyền cho " + masked + "; đã kết nối bằng App Password dự phòng.", (String) null);
                }
            }
            log.debug("Đã mở App Password dự phòng cho {} (giữ NEEDS_REAUTH).", masked);
            return connection;
        } catch (RuntimeException | Error e) {
            // Do not release an authenticated connection to the caller if its
            // associated account state could not be committed.
            if (connection instanceof ImapConnection imap) imap.markBroken();
            try { connection.close(); } catch (Exception ignored) {}
            throw e;
        }
    }

    private void persistAccountState(GmailAccount account) {
        if (gmailAccountRepository == null || account.getId() == null) return;
        GmailAccountSnapshot.saveState(gmailAccountRepository, account);
    }

    // =========================================================================
    // Core Open Logic (Low-level Jakarta Mail)
    // =========================================================================

    private ImapConnection openImapProbe(MailCredentials credentials) {
        return executeWithMonitoring("imap", credentials.authType(), credentials.email(),
                () -> doOpenImap(credentials, true, null));
    }

    private ImapConnection doOpenImap(MailCredentials credentials, boolean probe, Long mailboxId) throws Exception {
        String masked = MailCredentials.maskEmail(credentials.email());
        // Loopback has no special pooling behavior; ordinary fixtures set
        // pool.enabled=false, and the lease fixture explicitly enables it.
        boolean usePool = !probe && properties.isPoolEnabled();
        String poolKey = usePool ? buildPoolKey(credentials, mailboxId) : null;

        // 1. Ưu tiên mượn kết nối IMAPS ấm từ pool nếu còn khỏe mạnh và chưa quá idleSeconds
        if (usePool) {
            ImapConnection reused = borrowFromPool(poolKey, masked);
            if (reused != null) {
                return reused;
            }
        }

        Session session = Session.getInstance(imapProperties(credentials, probe));
        session.setDebug(false);

        Store store = session.getStore("imaps");
        try {
            store.connect(properties.getImapHost(), properties.getImapPort(), credentials.email(), credentials.secret());
            if (usePool) {
                return new ImapConnection(store, masked, conn -> returnToPool(poolKey, conn));
            }
            return new ImapConnection(store, masked);
        } catch (Exception e) {
            closeStore(store);
            throw mapException(e, credentials.authType());
        }
    }

    private Properties imapProperties(MailCredentials credentials, boolean probe) {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", properties.getImapHost());
        props.put("mail.imaps.port", String.valueOf(properties.getImapPort()));

        // TLS cấu hình chuẩn
        props.put("mail.imaps.ssl.enable", "true");
        if (allowLocalTestTransport(properties.getImapHost())) {
            // GreenMail's disposable self-signed certificate; never trust "*".
            props.put("mail.imaps.ssl.trust", properties.getImapHost());
            props.put("mail.imaps.ssl.checkserveridentity", "false");
        } else {
            props.put("mail.imaps.ssl.checkserveridentity", "true");
            props.put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3");
        }

        // Timeouts
        props.put("mail.imaps.connectiontimeout", String.valueOf(probe ? probeTimeout(properties.getConnectionTimeoutMs()) : properties.getConnectionTimeoutMs()));
        props.put("mail.imaps.timeout", String.valueOf(probe ? probeTimeout(properties.getTimeoutMs()) : properties.getTimeoutMs()));
        props.put("mail.imaps.writetimeout", String.valueOf(probe ? probeTimeout(properties.getWriteTimeoutMs()) : properties.getWriteTimeoutMs()));
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

        return props;
    }

    private ImapConnection borrowFromPool(String poolKey, String masked) {
        while (true) {
            PooledStore entry;
            synchronized (poolLock) {
                if (poolClosed) return null;
                Deque<PooledStore> deque = imapPool.get(poolKey);
                if (deque == null) return null;
                entry = deque.pollFirst();
                if (deque.isEmpty()) imapPool.remove(poolKey);
            }
            if (entry == null) return null;
            try {
                if (!expired(entry, System.nanoTime()) && entry.store().isConnected()) {
                    log.debug("Tái sử dụng kết nối IMAPS từ pool cho: {}", masked);
                    return new ImapConnection(entry.store(), masked, conn -> returnToPool(poolKey, conn));
                }
            } catch (Exception ignored) {}
            closeStore(entry.store());
        }
    }

    private void returnToPool(String poolKey, ImapConnection conn) {
        if (conn == null || !conn.isReusable()) {
            if (conn != null) conn.forceCloseStore();
            return;
        }
        int maxPerAccount = Math.max(1, properties.getPoolMaxPerAccount());
        synchronized (poolLock) {
            if (!poolClosed && properties.isPoolEnabled()) {
                Deque<PooledStore> deque = imapPool.computeIfAbsent(poolKey, k -> new ArrayDeque<>());
                if (deque.size() < maxPerAccount) {
                    deque.offerFirst(new PooledStore(conn.storeForRelease(), System.nanoTime()));
                    return;
                }
            }
        }
        conn.forceCloseStore();
    }

    private String buildPoolKey(MailCredentials credentials, Long mailboxId) {
        String secretSig;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(credentials.secret()).getBytes(StandardCharsets.UTF_8));
            secretSig = Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        return properties.getImapHost() + ":" + properties.getImapPort()
                + "|" + (mailboxId == null ? "direct" : mailboxId)
                + "|" + credentials.email().toLowerCase(Locale.ROOT)
                + "|" + credentials.authType()
                + "|" + secretSig;
    }

    private boolean expired(PooledStore entry, long nowNanos) {
        return nowNanos - entry.lastUsedNanos() >= TimeUnit.SECONDS.toNanos(Math.max(10, properties.getPoolIdleSeconds()));
    }

    @PostConstruct
    void startPoolMaintenance() {
        synchronized (poolLock) {
            if (poolClosed || !properties.isPoolEnabled() || poolMaintenance != null) return;
            poolMaintenance = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread t = new Thread(runnable, "mail-imap-idle-reaper");
                t.setDaemon(true);
                return t;
            });
            poolMaintenance.scheduleWithFixedDelay(() -> evictExpiredIdleStores(System.nanoTime()), 1, 1, TimeUnit.SECONDS);
        }
    }

    void evictExpiredIdleStores(long nowNanos) {
        List<Store> expired = new ArrayList<>();
        synchronized (poolLock) {
            for (var iterator = imapPool.entrySet().iterator(); iterator.hasNext();) {
                Deque<PooledStore> deque = iterator.next().getValue();
                deque.removeIf(entry -> {
                    if (!expired(entry, nowNanos)) return false;
                    expired.add(entry.store());
                    return true;
                });
                if (deque.isEmpty()) iterator.remove();
            }
        }
        expired.forEach(DefaultMailConnectionFactory::closeStore);
    }

    private static void closeStore(Store store) {
        if (store != null) {
            try { store.close(); } catch (Exception ignored) {}
        }
    }

    @PreDestroy
    public void shutdownPool() {
        List<Store> remaining = new ArrayList<>();
        synchronized (poolLock) {
            poolClosed = true;
            if (poolMaintenance != null) poolMaintenance.shutdownNow();
            imapPool.values().forEach(deque -> deque.forEach(entry -> remaining.add(entry.store())));
            imapPool.clear();
        }
        remaining.forEach(DefaultMailConnectionFactory::closeStore);
    }

    private SmtpConnection doOpenSmtp(MailCredentials credentials) throws Exception {
        Properties props = smtpProperties(credentials);
        // Tạo Session mới mỗi lần mở, debug=false
        Session session;
        if (credentials.authType() == AuthType.APP_PASSWORD) {
            session = scopedSession(props, credentials, "smtp", properties.getSmtpHost(), properties.getSmtpPort());
        } else {
            session = Session.getInstance(props);
        }
        session.setDebug(false);

        Transport transport = session.getTransport("smtp");
        try {
            transport.connect(properties.getSmtpHost(), properties.getSmtpPort(), credentials.email(), credentials.secret());
            return new SmtpConnection(transport, session, MailCredentials.maskEmail(credentials.email()));
        } catch (Exception e) {
            if (transport != null) {
                try { transport.close(); } catch (Exception ignored) {}
            }
            throw mapException(e, credentials.authType());
        }
    }

    private Properties smtpProperties(MailCredentials credentials) {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", properties.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(properties.getSmtpPort()));

        props.put("mail.smtp.auth", "true");
        if (allowLocalTestTransport(properties.getSmtpHost())) {
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.starttls.required", "false");
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

        return props;
    }

    // =========================================================================
    // Resilience4j, Metrics & Logging Helpers
    // =========================================================================

    static int probeTimeout(int configuredMs) {
        return configuredMs > 0 ? Math.min(10_000, configuredMs) : 10_000;
    }

    private static boolean isLoopback(String host) {
        return "127.0.0.1".equals(host) || "::1".equals(host) || "localhost".equalsIgnoreCase(host);
    }

    boolean allowLocalTestTransport(String host) {
        return properties.isLocalTestTransport() && isLoopback(host);
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
                    correlationId, protocol, masked, duration, e.getClass().getSimpleName());
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

    private MailConnectionException mapException(Exception e, AuthType authType) {
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
                if (authType == AuthType.OAUTH2) {
                    return new ReauthRequiredException("Gmail không chấp nhận quyền OAuth2. Vui lòng cấp lại quyền truy cập.", afe);
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
