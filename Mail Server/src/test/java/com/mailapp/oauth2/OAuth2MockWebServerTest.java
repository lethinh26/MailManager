package com.mailapp.oauth2;

import com.mailapp.config.GoogleOAuthConfig;
import com.mailapp.exception.ReauthRequiredException;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.repository.UserRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class OAuth2MockWebServerTest {

    private MockWebServer mockWebServer;
    private GoogleOAuthConfig oAuthConfig;
    private OAuth2StateStore stateStore;
    private GmailAccountRepository gmailAccountRepository;
    private UserRepository userRepository;
    private SystemSettingService systemSettingService;
    private AuditLogService auditLogService;
    private AesCipher aesCipher;
    private RestClient restClient;
    private OAuth2ServiceImpl oauth2Service;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        oAuthConfig = new GoogleOAuthConfig();
        ReflectionTestUtils.setField(oAuthConfig, "clientId", "test-client-id");
        ReflectionTestUtils.setField(oAuthConfig, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(oAuthConfig, "redirectUri", "https://localhost:8443/api/oauth2/callback");
        ReflectionTestUtils.setField(oAuthConfig, "tokenUrl", baseUrl + "token");
        ReflectionTestUtils.setField(oAuthConfig, "userinfoUrl", baseUrl + "userinfo");
        ReflectionTestUtils.setField(oAuthConfig, "revokeUrl", baseUrl + "revoke");
        ReflectionTestUtils.setField(oAuthConfig, "authUrl", baseUrl + "auth");

        restClient = RestClient.builder().build();
        stateStore = new OAuth2StateStore();
        gmailAccountRepository = mock(GmailAccountRepository.class);
        userRepository = mock(UserRepository.class);
        systemSettingService = mock(SystemSettingService.class);
        auditLogService = mock(AuditLogService.class);

        // AesCipher test với key 256-bit
        aesCipher = new AesCipher("dCPN0qIeMrPFVyn/9c05Pq1O65Re+wsVplG7fEFU4dU=");

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

        when(systemSettingService.isOAuth2LinkEnabled()).thenReturn(true);
        when(systemSettingService.getMaxGmailPerUser()).thenReturn(5);
        when(gmailAccountRepository.save(any(GmailAccount.class))).thenAnswer(inv -> {
            GmailAccount a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(100L);
            }
            return a;
        });
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Callback thành công: Đổi code kèm code_verifier, lấy email, mã hóa refresh token, lưu DB và trả HTML tiếng Việt")
    void testHandleCallbackFullSuccess() throws InterruptedException {
        Long userId = 1L;
        User user = new User(userId, "cuong", "cuong@mailapp.com", Role.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(gmailAccountRepository.countByUserId(userId)).thenReturn(0L);
        when(gmailAccountRepository.findByUserIdAndEmailKey(eq(userId), eq("cuongdfle@gmail.com"))).thenReturn(Optional.empty());

        // 1. Tạo state và code_verifier
        String codeVerifier = PkceUtil.generateCodeVerifier();
        String state = stateStore.generateState(userId, codeVerifier);

        // 2. Mock Google Token response (đầy đủ scope Gmail)
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "access_token": "mock-google-access-token-12345",
                            "expires_in": 3600,
                            "refresh_token": "mock-google-refresh-token-67890",
                            "scope": "https://mail.google.com/ openid https://www.googleapis.com/auth/userinfo.email",
                            "token_type": "Bearer"
                        }
                        """));

        // 3. Mock Google UserInfo response
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "id": "123456789",
                            "email": "cuongdfle@gmail.com",
                            "email_verified": true,
                            "name": "Lê Thanh Cường"
                        }
                        """));

        String html = oauth2Service.handleCallback("mock-auth-code", state, null, null);

        assertNotNull(html);
        assertTrue(html.contains("Liên kết thành công"));
        assertTrue(html.contains("c***@gmail.com"));
        assertTrue(html.contains("Bạn có thể đóng cửa sổ trình duyệt này và quay lại ứng dụng"));

        // Kiểm tra request đổi token gửi lên MockWebServer có code_verifier
        RecordedRequest tokenRequest = mockWebServer.takeRequest();
        assertEquals("/token", tokenRequest.getPath());
        String requestBody = tokenRequest.getBody().readUtf8();
        assertTrue(requestBody.contains("grant_type=authorization_code"));
        assertTrue(requestBody.contains("code=mock-auth-code"));
        assertTrue(requestBody.contains("code_verifier=" + codeVerifier));

        // Kiểm tra lưu DB
        verify(gmailAccountRepository).save(any(GmailAccount.class));
        verify(auditLogService).log(eq(userId), eq("LINK_GMAIL"), contains("c***@gmail.com"), any());
    }

    @Test
    @DisplayName("Callback khi trùng (user_id, email): Cập nhật refresh token cũ, không tạo dòng mới")
    void testHandleCallbackDuplicateEmailUpdatesExisting() {
        Long userId = 1L;
        User user = new User(userId, "cuong", "cuong@mailapp.com", Role.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        GmailAccount existingAccount = new GmailAccount();
        existingAccount.setId(99L);
        existingAccount.setUser(user);
        existingAccount.setEmail("cuongdfle@gmail.com");
        existingAccount.setAuthType(AuthType.OAUTH2);
        existingAccount.setRefreshTokenEncrypted("old-enc-refresh");
        existingAccount.setStatus(AccountStatus.NEEDS_REAUTH);

        when(gmailAccountRepository.findByUserIdAndEmailKey(eq(userId), eq("cuongdfle@gmail.com")))
                .thenReturn(Optional.of(existingAccount));

        String codeVerifier = PkceUtil.generateCodeVerifier();
        String state = stateStore.generateState(userId, codeVerifier);

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "access_token": "updated-access-token",
                            "expires_in": 3600,
                            "refresh_token": "updated-refresh-token",
                            "scope": "https://mail.google.com/ openid email",
                            "token_type": "Bearer"
                        }
                        """));

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "email": "cuongdfle@gmail.com",
                            "email_verified": true,
                            "name": "Lê Thanh Cường"
                        }
                        """));

        String html = oauth2Service.handleCallback("code-xyz", state, null, null);

        assertTrue(html.contains("Cập nhật ủy quyền thành công"));
        assertEquals(AccountStatus.ACTIVE, existingAccount.getStatus());
        verify(gmailAccountRepository).save(existingAccount);
    }

    @Test
    @DisplayName("getFreshAccessToken: Refresh thành công và cache vào Caffeine RAM (không gọi lại Google lần 2)")
    void testGetFreshAccessTokenCachesInMemory() {
        GmailAccount account = new GmailAccount();
        account.setId(77L);
        account.setEmail("cuongdfle@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setStatus(AccountStatus.ACTIVE);
        account.setRefreshTokenEncrypted(aesCipher.encrypt("my-refresh-token"));

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "access_token": "fresh-token-abc",
                            "expires_in": 3600,
                            "token_type": "Bearer"
                        }
                        """));

        // Lần 1: gọi tới Google Token endpoint
        String token1 = oauth2Service.getFreshAccessToken(account);
        assertEquals("fresh-token-abc", token1);
        assertEquals(1, mockWebServer.getRequestCount());

        // Lần 2: lấy từ Caffeine RAM cache, KHÔNG gọi lại Google
        String token2 = oauth2Service.getFreshAccessToken(account);
        assertEquals("fresh-token-abc", token2);
        assertEquals(1, mockWebServer.getRequestCount()); // Vẫn chỉ là 1 request!
    }

    @Test
    @DisplayName("getFreshAccessToken: Khóa đồng bộ theo accountId, 2 luồng đồng thời chỉ gọi refresh đúng 1 lần")
    void testConcurrentRefreshOnlyCallsGoogleOnce() throws Exception {
        GmailAccount account = new GmailAccount();
        account.setId(88L);
        account.setEmail("cuongdfle@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setStatus(AccountStatus.ACTIVE);
        account.setRefreshTokenEncrypted(aesCipher.encrypt("my-refresh-token-concurrent"));

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "access_token": "concurrent-fresh-token",
                            "expires_in": 3600,
                            "token_type": "Bearer"
                        }
                        """));

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String t = oauth2Service.getFreshAccessToken(account);
                    if ("concurrent-fresh-token".equals(t)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(2, successCount.get(), "Cả 2 luồng đều phải nhận được token hợp lệ");
        assertEquals(1, mockWebServer.getRequestCount(), "Google Token endpoint chỉ được gọi đúng 1 lần nhờ account lock");
    }

    @Test
    @DisplayName("getFreshAccessToken: Google trả invalid_grant thì chuyển status thành NEEDS_REAUTH và ném ReauthRequiredException")
    void testRefreshInvalidGrantSetsNeedsReauth() {
        GmailAccount account = new GmailAccount();
        account.setId(99L);
        account.setEmail("revoked@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setStatus(AccountStatus.ACTIVE);
        account.setRefreshTokenEncrypted(aesCipher.encrypt("revoked-refresh-token"));

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "error": "invalid_grant",
                            "error_description": "Token has been expired or revoked."
                        }
                        """));

        assertThrows(ReauthRequiredException.class, () ->
                oauth2Service.getFreshAccessToken(account)
        );

        assertEquals(AccountStatus.NEEDS_REAUTH, account.getStatus());
        verify(gmailAccountRepository).save(account);
        verify(auditLogService).log(any(), eq("GMAIL_NEEDS_REAUTH"), anyString(), any());
    }

    @Test
    @DisplayName("revokeToken: Gửi POST tới endpoint revoke của Google và xóa token cache")
    void testRevokeTokenSuccess() throws InterruptedException {
        GmailAccount account = new GmailAccount();
        account.setId(105L);
        account.setEmail("revoke_me@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setRefreshTokenEncrypted(aesCipher.encrypt("refresh-token-to-revoke"));

        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        oauth2Service.revokeToken(account);

        RecordedRequest revokeReq = mockWebServer.takeRequest();
        assertEquals("/revoke", revokeReq.getPath());
        String body = revokeReq.getBody().readUtf8();
        assertTrue(body.contains("token=refresh-token-to-revoke"));
    }

    @Test
    @DisplayName("Callback thiếu scope Gmail (granular consent bị bỏ tick): KHÔNG lưu DB, ghi audit LINK_GMAIL_DENIED_SCOPE, trả HTML báo lỗi")
    void testHandleCallbackMissingGmailScopeDenied() throws InterruptedException {
        Long userId = 1L;
        String codeVerifier = PkceUtil.generateCodeVerifier();
        String state = stateStore.generateState(userId, codeVerifier);

        // Google trả token nhưng chỉ có openid + email, KHÔNG có https://mail.google.com/
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "access_token": "partial-scope-token",
                            "expires_in": 3600,
                            "refresh_token": "partial-refresh-token",
                            "scope": "openid https://www.googleapis.com/auth/userinfo.email",
                            "token_type": "Bearer"
                        }
                        """));

        String html = oauth2Service.handleCallback("mock-code-partial", state, null, null);

        assertNotNull(html);
        assertTrue(html.contains("Liên kết thất bại"));
        assertTrue(html.contains("Bạn chưa cấp quyền truy cập Gmail, hãy bấm Liên kết lại và giữ nguyên các ô quyền"));

        // Đảm bảo KHÔNG lưu tài khoản vào DB và KHÔNG gọi userinfo
        verify(gmailAccountRepository, never()).save(any(GmailAccount.class));
        assertEquals(1, mockWebServer.getRequestCount(), "Chỉ gọi /token, không gọi /userinfo khi thiếu scope Gmail");

        // Đảm bảo ghi audit LINK_GMAIL_DENIED_SCOPE
        verify(auditLogService).log(eq(userId), eq("LINK_GMAIL_DENIED_SCOPE"), contains("https://mail.google.com/"), any());
    }

    @Test
    @DisplayName("Callback khi Google trả access_denied: KHÔNG gọi /token, KHÔNG lưu DB, trả HTML thông báo hủy")
    void testHandleCallbackAccessDenied() {
        String codeVerifier = PkceUtil.generateCodeVerifier();
        String state = stateStore.generateState(1L, codeVerifier);

        String html = oauth2Service.handleCallback(null, state, "access_denied", "Người dùng từ chối");

        assertNotNull(html);
        assertTrue(html.contains("Liên kết thất bại"));
        assertTrue(html.contains("từ chối"));
        verify(gmailAccountRepository, never()).save(any(GmailAccount.class));
        assertEquals(0, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("Callback với state sai/hết hạn: Chặn CSRF, KHÔNG gọi /token, KHÔNG lưu DB")
    void testHandleCallbackInvalidState() {
        String html = oauth2Service.handleCallback("some-auth-code", "invalid-or-expired-state-xyz", null, null);

        assertNotNull(html);
        assertTrue(html.contains("Liên kết thất bại"));
        assertTrue(html.contains("state"));
        verify(gmailAccountRepository, never()).save(any(GmailAccount.class));
        assertEquals(0, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("Đăng nhập Google OAuth2 khi trùng email với user mật khẩu: tự hợp nhất vào user hiện có + nâng cấp App Password -> OAuth2")
    void testGoogleLoginMergesDuplicateEmailAndUpgradesAppPassword() {
        com.mailapp.security.JwtProvider mockJwt = mock(com.mailapp.security.JwtProvider.class);
        org.springframework.security.crypto.password.PasswordEncoder mockEnc = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        OAuth2ServiceImpl loginService = new OAuth2ServiceImpl(
                oAuthConfig, stateStore, gmailAccountRepository, userRepository,
                systemSettingService, auditLogService, aesCipher, restClient, mockJwt, mockEnc
        );

        User existingUser = new User();
        existingUser.setId(42L);
        existingUser.setUsername("cuong_pass");
        existingUser.setEmail("cuongdfle@gmail.com");
        existingUser.setFullName("Lê Thanh Cường");
        existingUser.setRole(Role.ADMIN);
        existingUser.setEnabled(true);

        String existingCipher = "cipher-" + java.util.UUID.randomUUID();
        GmailAccount existingAppPass = new GmailAccount();
        existingAppPass.setId(77L);
        existingAppPass.setUser(existingUser);
        existingAppPass.setEmail("cuongdfle@gmail.com");
        existingAppPass.setAuthType(AuthType.APP_PASSWORD);
        existingAppPass.setAppPasswordEncrypted(existingCipher);
        existingAppPass.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findByEmailIgnoreCase("cuongdfle@gmail.com")).thenReturn(Optional.of(existingUser));
        when(gmailAccountRepository.findByUserIdAndEmailKey(42L, "cuongdfle@gmail.com")).thenReturn(Optional.of(existingAppPass));
        when(mockJwt.generateToken(existingUser)).thenReturn("jwt-merged-token-123");
        when(mockJwt.getExpirationSeconds()).thenReturn(86400L);

        OAuth2Service.GoogleLoginStartResponse start = loginService.startGoogleLogin();
        assertNotNull(start.authorizeUrl());
        assertNotNull(start.pollToken());
        assertEquals("PENDING", loginService.pollGoogleLogin(start.pollToken()).status());

        // Rút state từ authorizeUrl để mô phỏng Google redirect về /api/oauth2/callback
        String state = start.authorizeUrl().replaceAll(".*state=([^&]+).*", "$1");

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "access_token": "mock_merged_access_tok",
                          "refresh_token": "mock_merged_refresh_tok",
                          "expires_in": 3600,
                          "scope": "https://mail.google.com/ openid email profile",
                          "token_type": "Bearer"
                        }
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "email": "cuongdfle@gmail.com",
                          "email_verified": true,
                          "name": "Lê Thanh Cường",
                          "picture": "https://lh3.googleusercontent.com/a/sample=s96-c"
                        }
                        """));

        String html = loginService.handleCallback("code-merge-123", state, null, null);
        assertTrue(html.contains("hợp nhất tự động"));
        assertTrue(html.contains("Email Google trả về"));
        assertTrue(html.contains("c***@gmail.com"));
        assertEquals(AuthType.OAUTH2, existingAppPass.getAuthType());
        assertEquals(existingCipher, existingAppPass.getAppPasswordEncrypted(), "Giữ nguyên App Password làm dự phòng khi thêm OAuth2");
        assertEquals("https://lh3.googleusercontent.com/a/sample=s96-c", existingAppPass.getAvatarUrl());

        // Poll lần 1 nhận JWT hợp nhất của existingUser (giữ nguyên role ADMIN!)
        OAuth2Service.GoogleLoginPollResponse poll1 = loginService.pollGoogleLogin(start.pollToken());
        assertEquals("SUCCESS", poll1.status());
        assertEquals("jwt-merged-token-123", poll1.loginResponse().accessToken());
        assertEquals("cuong_pass", poll1.loginResponse().user().username());
        assertEquals(Role.ADMIN, poll1.loginResponse().user().role());

        // Poll lần 2 bị từ chối vì pollToken chỉ dùng 1 lần (single-use)
        assertThrows(com.mailapp.exception.AppException.class, () -> loginService.pollGoogleLogin(start.pollToken()));
    }

    @Test
    @DisplayName("Callback thêm OAuth2 cho hộp thư đã có App Password: HTML ghi rõ email che bớt, nói 'Đã thêm', không dùng chữ 'lỗi'")
    void testHandleCallbackAddsOAuth2ToExistingAppPassword() {
        Long userId = 1L;
        User user = new User(userId, "cuong", "cuong@mailapp.com", Role.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String existingCipher = "cipher-existing-" + java.util.UUID.randomUUID();
        GmailAccount existing = new GmailAccount();
        existing.setId(150L);
        existing.setUser(user);
        existing.setEmail("cuong.df.le@gmail.com");
        existing.setDisplayName("Hộp thư chính");
        existing.setAuthType(AuthType.APP_PASSWORD);
        existing.setAppPasswordEncrypted(existingCipher);
        existing.setStatus(AccountStatus.ACTIVE);

        when(gmailAccountRepository.findByUserIdAndEmailKey(eq(userId), eq("cuongdfle@gmail.com")))
                .thenReturn(Optional.of(existing));

        String codeVerifier = PkceUtil.generateCodeVerifier();
        String state = stateStore.generateState(userId, codeVerifier);

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "access_token": "added-access-token",
                            "expires_in": 3600,
                            "refresh_token": "added-refresh-token",
                            "scope": "https://mail.google.com/ openid email",
                            "token_type": "Bearer"
                        }
                        """));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                            "email": "cuongdfle+work@googlemail.com",
                            "email_verified": true,
                            "name": "Google Name"
                        }
                        """));

        String html = oauth2Service.handleCallback("code-add-oauth", state, null, null);
        assertTrue(html.contains("Đã thêm phương thức"));
        assertTrue(html.contains("Email Google trả về"));
        assertTrue(html.contains("c***@googlemail.com"));
        assertFalse(html.toLowerCase().contains("lỗi"), "Thông báo thêm phương thức thứ hai không được dùng chữ 'lỗi'");
        assertEquals(AuthType.OAUTH2, existing.getAuthType());
        assertEquals("Hộp thư chính", existing.getDisplayName());
        assertEquals(existingCipher, existing.getAppPasswordEncrypted());
        assertNotNull(existing.getRefreshTokenEncrypted());
    }
}

