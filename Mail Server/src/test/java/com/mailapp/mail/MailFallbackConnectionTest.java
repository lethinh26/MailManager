package com.mailapp.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.mailapp.exception.MailAuthException;
import com.mailapp.exception.ReauthRequiredException;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử cơ chế fallback OAuth2 -> App Password trong DefaultMailConnectionFactory:
 * 1. OAuth2 ném ReauthRequiredException + có App Password -> dùng App Password 1 lần, đánh dấu fallbackActive=true,
 *    ghi audit FALLBACK_TO_APP_PASSWORD (không lộ bí mật), KHÔNG xóa NEEDS_REAUTH của OAuth2.
 * 2. Lần mở tiếp theo khi đã NEEDS_REAUTH + có App Password -> đi thẳng App Password, không gọi lại Google refresh.
 * 3. Khi không có App Password -> giữ nguyên hành vi cũ (ném ReauthRequiredException).
 * 4. Khi App Password dự phòng cũng sai -> ném MailAuthException và KHÔNG retry (0 lần retry).
 */
class MailFallbackConnectionTest {

    private static GreenMail greenMail;
    private static final int IMAPS_PORT = 3997;

    @BeforeAll
    static void startGreenMail() {
        ServerSetup imapsSetup = new ServerSetup(IMAPS_PORT, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS);
        greenMail = new GreenMail(new ServerSetup[]{imapsSetup});
        greenMail.start();
        greenMail.setUser("fallbackuser@gmail.com", "backupAppPass16!");
    }

    @AfterAll
    static void stopGreenMail() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    private MailConnectionProperties greenMailProps() {
        MailConnectionProperties props = new MailConnectionProperties();
        props.setImapHost("127.0.0.1");
        props.setImapPort(IMAPS_PORT);
        props.setRetryEnabled(true);
        props.setRetryMaxAttempts(2);
        props.setRetryWaitDurationMs(30);
        props.setPoolEnabled(false);
        return props;
    }

    @Test
    @DisplayName("OAuth2 ném ReauthRequiredException + có App Password -> fallback đúng 1 lần, giữ NEEDS_REAUTH, ghi audit không lộ bí mật")
    void fallbackToAppPasswordOnceKeepsNeedsReauthAndAuditsSafely() {
        AesCipher aes = new AesCipher("dCPN0qIeMrPFVyn/9c05Pq1O65Re+wsVplG7fEFU4dU=");
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        GmailAccountRepository repo = mock(GmailAccountRepository.class);
        AuditLogService audit = mock(AuditLogService.class);

        GmailAccount account = new GmailAccount();
        account.setId(501L);
        account.setUser(new User(10L, "cuong", "cuong@example.com", Role.USER));
        account.setEmail("fallbackuser@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setRefreshTokenEncrypted(aes.encrypt("revoked-refresh-token"));
        account.setAppPasswordEncrypted(aes.encrypt("backupAppPass16!"));
        account.setStatus(AccountStatus.ACTIVE);

        when(tokenProvider.getValidAccessToken(account)).thenAnswer(inv -> {
            account.setStatus(AccountStatus.NEEDS_REAUTH);
            throw new ReauthRequiredException("invalid_grant");
        });

        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                greenMailProps(), aes, tokenProvider, null, repo, audit);

        // Lần 1: OAuth2 ném ReauthRequiredException -> fallback sang App Password thành công
        try (ImapConnection conn = factory.openImap(account)) {
            assertTrue(conn.isConnected());
        }

        assertEquals(AccountStatus.NEEDS_REAUTH, account.getStatus(), "KHÔNG được xóa trạng thái NEEDS_REAUTH của OAuth2");
        assertTrue(account.isFallbackActive(), "Phải đánh dấu fallbackActive=true");
        assertEquals(AuthType.APP_PASSWORD, account.getActiveMethod());
        assertEquals(AuthType.OAUTH2, account.getPreferredMethod());
        verify(tokenProvider, times(1)).getValidAccessToken(account);
        verify(repo, atLeastOnce()).save(account);
        verify(audit, times(1)).log(eq(10L), eq("FALLBACK_TO_APP_PASSWORD"), argThat(detail ->
                detail.contains("f***@gmail.com")
                        && !detail.contains("backupAppPass16!")
                        && !detail.contains("revoked-refresh-token")), isNull());

        // Lần 2: đã ở NEEDS_REAUTH + có App Password -> đi thẳng App Password, KHÔNG gọi Google tokenProvider thêm lần nào
        try (ImapConnection conn2 = factory.openImap(account)) {
            assertTrue(conn2.isConnected());
        }
        verify(tokenProvider, times(1)).getValidAccessToken(account);
    }

    @Test
    @DisplayName("OAuth2 ném ReauthRequiredException và KHÔNG có App Password -> giữ nguyên ReauthRequiredException (409)")
    void noFallbackWithoutAppPassword() {
        AesCipher aes = new AesCipher("dCPN0qIeMrPFVyn/9c05Pq1O65Re+wsVplG7fEFU4dU=");
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        GmailAccountRepository repo = mock(GmailAccountRepository.class);
        AuditLogService audit = mock(AuditLogService.class);

        GmailAccount account = new GmailAccount();
        account.setId(502L);
        account.setEmail("fallbackuser@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setRefreshTokenEncrypted(aes.encrypt("revoked-token"));
        account.setStatus(AccountStatus.ACTIVE);

        when(tokenProvider.getValidAccessToken(account)).thenThrow(new ReauthRequiredException("invalid_grant"));

        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                greenMailProps(), aes, tokenProvider, null, repo, audit);

        assertThrows(ReauthRequiredException.class, () -> factory.openImap(account));
        assertFalse(account.isFallbackActive());
        verify(audit, never()).log(any(), eq("FALLBACK_TO_APP_PASSWORD"), any(), any());
    }

    @Test
    @DisplayName("Khi App Password dự phòng cũng sai -> ném MailAuthException và KHÔNG retry lỗi xác thực (0 lần retry)")
    void fallbackWrongAppPasswordDoesNotRetry() {
        AesCipher aes = new AesCipher("dCPN0qIeMrPFVyn/9c05Pq1O65Re+wsVplG7fEFU4dU=");
        AccessTokenProvider tokenProvider = mock(AccessTokenProvider.class);
        GmailAccountRepository repo = mock(GmailAccountRepository.class);
        AuditLogService audit = mock(AuditLogService.class);

        GmailAccount account = new GmailAccount();
        account.setId(503L);
        account.setEmail("fallbackuser@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setRefreshTokenEncrypted(aes.encrypt("revoked-token"));
        account.setAppPasswordEncrypted(aes.encrypt("WRONG_BACKUP_PASS"));
        account.setStatus(AccountStatus.ACTIVE);

        when(tokenProvider.getValidAccessToken(account)).thenThrow(new ReauthRequiredException("invalid_grant"));

        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                greenMailProps(), aes, tokenProvider, null, repo, audit);

        AtomicInteger retryCount = new AtomicInteger(0);
        factory.getNetworkRetry().getEventPublisher().onRetry(e -> retryCount.incrementAndGet());

        assertThrows(MailAuthException.class, () -> factory.openImap(account));
        assertEquals(0, retryCount.get(), "Lỗi xác thực tuyệt đối không được retry!");
        verify(tokenProvider, times(1)).getValidAccessToken(account);
    }
}

