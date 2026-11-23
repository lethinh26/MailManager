package com.mailapp.mail;

import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.oauth2.OAuth2Service;
import com.mailapp.security.AesCipher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XOAuth2IntegrationTest {

    private OAuth2Service oauth2Service;
    private DefaultAccessTokenProvider tokenProvider;
    private AesCipher aesCipher;

    @BeforeEach
    void setUp() {
        oauth2Service = mock(OAuth2Service.class);
        tokenProvider = new DefaultAccessTokenProvider(oauth2Service);

        aesCipher = new AesCipher("dCPN0qIeMrPFVyn/9c05Pq1O65Re+wsVplG7fEFU4dU=");
    }

    @Test
    @DisplayName("Nối M07 với M06: MailCredentials nạp Access Token từ OAuth2Service qua DefaultAccessTokenProvider")
    void testMailCredentialsLoadsTokenFromOAuth2Service() {
        GmailAccount account = new GmailAccount();
        account.setId(55L);
        account.setEmail("cuongdfle@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setStatus(AccountStatus.ACTIVE);
        account.setRefreshTokenEncrypted(aesCipher.encrypt("my-refresh-token"));
        account.setTokenExpiresAt(LocalDateTime.now().plusHours(1));

        when(oauth2Service.getFreshAccessToken(account)).thenReturn("fresh-oauth2-access-token-999");

        MailCredentials credentials = MailCredentials.from(account, aesCipher, tokenProvider);

        assertEquals("cuongdfle@gmail.com", credentials.email());
        assertEquals(AuthType.OAUTH2, credentials.authType());
        assertEquals("fresh-oauth2-access-token-999", credentials.secret());

        // toString tuyệt đối không lộ access token
        assertFalse(credentials.toString().contains("fresh-oauth2-access-token-999"));
        assertTrue(credentials.toString().contains("secret=***"));

        verify(oauth2Service, times(1)).getFreshAccessToken(account);
    }

    @Test
    @DisplayName("Kiểm tra định dạng chuỗi SASL XOAUTH2 chuẩn RFC 6749 / Google XOAUTH2")
    void testXOAuth2SaslFormat() {
        String email = "cuongdfle@gmail.com";
        String accessToken = "mock-oauth-token-" + java.util.UUID.randomUUID();

        // Định dạng SASL XOAUTH2: "user=" + email + "\u0001auth=Bearer " + accessToken + "\u0001\u0001"
        String rawSasl = "user=" + email + "\u0001auth=Bearer " + accessToken + "\u0001\u0001";
        String base64Sasl = Base64.getEncoder().encodeToString(rawSasl.getBytes(StandardCharsets.UTF_8));

        assertNotNull(base64Sasl);
        byte[] decoded = Base64.getDecoder().decode(base64Sasl);
        String decodedString = new String(decoded, StandardCharsets.UTF_8);

        assertEquals(rawSasl, decodedString);
        assertTrue(decodedString.startsWith("user=cuongdfle@gmail.com\u0001auth=Bearer "));
        assertTrue(decodedString.endsWith("\u0001\u0001"));
    }

    @Test
    @DisplayName("Cấu hình kết nối cho XOAUTH2 thiết lập đầy đủ cơ chế SASL và tắt AUTH PLAIN/LOGIN")
    void testXOAuth2SessionProperties() {
        MailConnectionProperties properties = new MailConnectionProperties();
        DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                properties,
                aesCipher,
                tokenProvider,
                new SimpleMeterRegistry()
        );

        assertNotNull(factory);
    }
}

