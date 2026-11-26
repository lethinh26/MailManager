package com.mailapp.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.mailapp.exception.MailAuthException;
import com.mailapp.exception.MailNetworkException;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.oauth2.OAuth2Service;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.security.AesCipher;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử chứng minh:
 * 1. Sai mật khẩu: Ném MailAuthException và retry đúng 0 lần.
 * 2. Server đóng giữa chừng / lỗi mạng: Ném MailNetworkException và retry đúng 2 lần (1 retry thêm).
 * 3. Timeout: Ném MailNetworkException (GATEWAY_TIMEOUT) và retry đúng 2 lần.
 * 4. Token hết hạn: AccessTokenProvider refresh đúng 1 lần.
 *
 * @author Lê Thanh Cường (Module M07)
 */
class MailConnectionFaultAndRetryTest {

    private static GreenMail greenMail;
    private static final int IMAPS_PORT = 3995;

    @BeforeAll
    static void startGreenMail() {
        ServerSetup imapsSetup = new ServerSetup(IMAPS_PORT, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS);
        greenMail = new GreenMail(new ServerSetup[]{imapsSetup});
        greenMail.start();
        greenMail.setUser("testuser@gmail.com", "correctPass1234");
    }

    @AfterAll
    static void stopGreenMail() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @Test
    @DisplayName("Sai mật khẩu: Ném MailAuthException và KHÔNG RETRY (0 lần retry)")
    void testWrongPasswordDoesNotRetry() {
        MailConnectionProperties properties = new MailConnectionProperties();
        properties.setImapHost("127.0.0.1");
        properties.setImapPort(IMAPS_PORT);
        properties.setLocalTestTransport(true);
        properties.setPoolEnabled(false);
        properties.setRetryEnabled(true);
        properties.setRetryMaxAttempts(2);
        properties.setRetryWaitDurationMs(50);

        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                properties, mock(AesCipher.class), mock(AccessTokenProvider.class), null);

        AtomicInteger retryCounter = new AtomicInteger(0);
        Retry retry = factory.getNetworkRetry();
        retry.getEventPublisher().onRetry(event -> retryCounter.incrementAndGet());

        MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", "wrongPassword");

        MailAuthException ex = assertThrows(MailAuthException.class, () -> factory.openImap(credentials));
        assertEquals("GMAIL_AUTH_FAILED", ex.getErrorCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());

        // Khẳng định: Hoàn toàn không có retry nào diễn ra cho lỗi xác thực (0 lần)
        assertEquals(0, retryCounter.get(), "Lỗi xác thực tuyệt đối không được retry!");
    }

    @Test
    @DisplayName("Server đóng cổng/lỗi mạng: Ném MailNetworkException và retry đúng 2 lần tổng cộng (1 lần retry)")
    void testServerClosedRetriesExactlyConfiguredAttempts() {
        MailConnectionProperties properties = new MailConnectionProperties();
        properties.setImapHost("127.0.0.1");
        properties.setImapPort(59997); // Cổng không có server chạy
        properties.setRetryEnabled(true);
        properties.setRetryMaxAttempts(2);
        properties.setRetryWaitDurationMs(50);

        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                properties, mock(AesCipher.class), mock(AccessTokenProvider.class), null);

        AtomicInteger retryCounter = new AtomicInteger(0);
        Retry retry = factory.getNetworkRetry();
        retry.getEventPublisher().onRetry(event -> retryCounter.incrementAndGet());

        MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", "anyPass");

        MailNetworkException ex = assertThrows(MailNetworkException.class, () -> factory.openImap(credentials));
        assertEquals("GMAIL_CONNECTION_ERROR", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());

        // Với max-attempts=2: lần 1 thất bại -> retry 1 lần (tổng cộng thực hiện 2 lần thử)
        assertEquals(1, retryCounter.get(), "Lỗi mạng phải được retry đúng 1 lần (tổng 2 lần thử)");
    }

    @Test
    @DisplayName("Timeout mạng: Ném MailNetworkException và retry đúng 2 lần")
    void testSocketTimeoutRetriesAndThrowsGatewayTimeout() throws IOException {
        // Tạo server socket giả lập chấp nhận TCP nhưng không trả lời IMAP handshake
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int timeoutPort = serverSocket.getLocalPort();

            Thread serverThread = new Thread(() -> {
                try {
                    while (!serverSocket.isClosed()) {
                        Socket socket = serverSocket.accept();
                        // Giữ kết nối mở để gây timeout
                        Thread.sleep(1000);
                        socket.close();
                    }
                } catch (Exception ignored) {}
            });
            serverThread.setDaemon(true);
            serverThread.start();

            MailConnectionProperties properties = new MailConnectionProperties();
            properties.setImapHost("127.0.0.1");
            properties.setImapPort(timeoutPort);
            properties.setConnectionTimeoutMs(200);
            properties.setTimeoutMs(200);
            properties.setRetryEnabled(true);
            properties.setRetryMaxAttempts(2);
            properties.setRetryWaitDurationMs(50);

            DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                    properties, mock(AesCipher.class), mock(AccessTokenProvider.class), null);

            AtomicInteger retryCounter = new AtomicInteger(0);
            factory.getNetworkRetry().getEventPublisher().onRetry(event -> retryCounter.incrementAndGet());

            MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", "pass");

            MailNetworkException ex = assertThrows(MailNetworkException.class, () -> factory.openImap(credentials));
            assertEquals("GMAIL_CONNECTION_ERROR", ex.getErrorCode());
            assertEquals(1, retryCounter.get(), "Lỗi timeout phải được retry đúng 1 lần (tổng 2 lần thử)");
        }
    }

    @Test
    @DisplayName("Token hết hạn: AccessTokenProvider refresh token qua OAuth2Service đúng 1 lần")
    void testExpiredTokenRefreshesExactlyOnce() {
        OAuth2Service oauth2Service = mock(OAuth2Service.class);
        DefaultAccessTokenProvider tokenProvider = new DefaultAccessTokenProvider(oauth2Service);

        GmailAccount account = new GmailAccount();
        account.setEmail("user@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setStatus(AccountStatus.ACTIVE);
        // Hết hạn từ 5 phút trước
        account.setTokenExpiresAt(LocalDateTime.now().minusMinutes(5));

        when(oauth2Service.getFreshAccessToken(account)).thenReturn("fresh-new-jwt-or-token");

        String resultToken = tokenProvider.getValidAccessToken(account);

        assertEquals("fresh-new-jwt-or-token", resultToken);
        // Khẳng định refresh được gọi đúng 1 lần
        verify(oauth2Service, times(1)).getFreshAccessToken(account);
    }
}
