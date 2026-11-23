package com.mailapp.oauth2;

import com.mailapp.config.GoogleOAuthConfig;
import com.mailapp.dto.AuthorizeUrlResponse;
import com.mailapp.exception.AppException;
import com.mailapp.exception.ReauthRequiredException;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.repository.UserRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ServiceTest {

    @Mock
    private GoogleOAuthConfig oAuthConfig;

    @Mock
    private OAuth2StateStore stateStore;

    @Mock
    private GmailAccountRepository gmailAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AesCipher aesCipher;

    @Mock
    private RestClient restClient;

    private OAuth2ServiceImpl oauth2Service;

    @BeforeEach
    void setUp() {
        oauth2Service = new OAuth2ServiceImpl(
                oAuthConfig,
                stateStore,
                gmailAccountRepository,
                userRepository,
                systemSettingService,
                auditLogService,
                aesCipher,
                restClient
        );
    }

    @Test
    @DisplayName("getAuthorizeUrl thành công sinh URL chứa client_id, redirect_uri, offline, consent, state và PKCE S256")
    void testGetAuthorizeUrlSuccess() {
        Long userId = 1L;
        when(systemSettingService.isOAuth2LinkEnabled()).thenReturn(true);
        when(systemSettingService.getMaxGmailPerUser()).thenReturn(5);
        when(gmailAccountRepository.countByUserId(userId)).thenReturn(1L);

        when(stateStore.generateState(eq(userId), anyString())).thenReturn("mock-state-12345");
        when(oAuthConfig.getClientId()).thenReturn("my-client-id.apps.googleusercontent.com");
        when(oAuthConfig.getRedirectUri()).thenReturn("https://localhost:8443/api/oauth2/callback");
        when(oAuthConfig.getAuthUrl()).thenReturn("https://accounts.google.com/o/oauth2/v2/auth");

        AuthorizeUrlResponse response = oauth2Service.getAuthorizeUrl(userId);

        assertNotNull(response);
        String url = response.authorizeUrl();
        assertTrue(url.contains("https://accounts.google.com/o/oauth2/v2/auth"));
        assertTrue(url.contains("client_id=my-client-id.apps.googleusercontent.com"));
        assertTrue(url.contains("access_type=offline"));
        assertTrue(url.contains("prompt=consent"));
        assertTrue(url.contains("state=mock-state-12345"));
        assertTrue(url.contains("code_challenge="));
        assertTrue(url.contains("code_challenge_method=S256"));
    }

    @Test
    @DisplayName("getAuthorizeUrl ném FEATURE_DISABLED khi Admin tắt tính năng liên kết OAuth2")
    void testGetAuthorizeUrlFeatureDisabled() {
        when(systemSettingService.isOAuth2LinkEnabled()).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> oauth2Service.getAuthorizeUrl(1L));
        assertEquals("FEATURE_DISABLED", ex.getCode());
    }

    @Test
    @DisplayName("getAuthorizeUrl ném GMAIL_LIMIT_REACHED khi user đã đạt số lượng Gmail tối đa")
    void testGetAuthorizeUrlLimitReached() {
        Long userId = 1L;
        when(systemSettingService.isOAuth2LinkEnabled()).thenReturn(true);
        when(systemSettingService.getMaxGmailPerUser()).thenReturn(3);
        when(gmailAccountRepository.countByUserId(userId)).thenReturn(3L);

        AppException ex = assertThrows(AppException.class, () -> oauth2Service.getAuthorizeUrl(userId));
        assertEquals("GMAIL_LIMIT_REACHED", ex.getCode());
    }

    @Test
    @DisplayName("handleCallback trả về trang HTML lỗi khi state không hợp lệ hoặc đã hết hạn")
    void testHandleCallbackInvalidState() {
        when(stateStore.validateAndConsumeState("invalid-state")).thenReturn(java.util.Optional.empty());

        String html = oauth2Service.handleCallback("auth-code-123", "invalid-state");

        assertNotNull(html);
        assertTrue(html.contains("Liên kết thất bại"));
        assertTrue(html.contains("không hợp lệ, đã hết hạn hoặc đã được sử dụng"));
    }

    @Test
    @DisplayName("getFreshAccessToken ném ReauthRequiredException khi refreshToken rỗng")
    void testGetFreshAccessTokenMissingRefreshToken() {
        GmailAccount account = new GmailAccount();
        account.setId(1L);
        account.setEmail("test@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setRefreshTokenEncrypted("enc-token");

        when(aesCipher.decrypt("enc-token")).thenReturn("");

        assertThrows(ReauthRequiredException.class, () -> oauth2Service.getFreshAccessToken(account));
        assertEquals(AccountStatus.NEEDS_REAUTH, account.getStatus());
        verify(gmailAccountRepository).save(account);
    }

    @Test
    @DisplayName("Chống SSRF: chỉ chấp nhận URL ảnh đại diện HTTPS từ host googleusercontent.com hợp lệ")
    void testTrustedGoogleAvatarUrlAllowlist() {
        assertTrue(OAuth2ServiceImpl.isTrustedGoogleAvatarUrl("https://lh3.googleusercontent.com/a/ACg8ocSamplePhoto=s96-c"));
        assertTrue(OAuth2ServiceImpl.isTrustedGoogleAvatarUrl("https://lh5.googleusercontent.com/photo.jpg"));
        assertFalse(OAuth2ServiceImpl.isTrustedGoogleAvatarUrl("http://lh3.googleusercontent.com/a/plain-http"));
        assertFalse(OAuth2ServiceImpl.isTrustedGoogleAvatarUrl("https://169.254.169.254/latest/meta-data/"));
        assertFalse(OAuth2ServiceImpl.isTrustedGoogleAvatarUrl("https://evil.com/lh3.googleusercontent.com/pic.jpg"));
        assertFalse(OAuth2ServiceImpl.isTrustedGoogleAvatarUrl("file:///etc/passwd"));
    }
}
