package com.mailapp.controller;

import com.mailapp.dto.GmailAccountResponse;
import com.mailapp.dto.LinkAppPasswordRequest;
import com.mailapp.exception.AppException;
import com.mailapp.mail.MailConnectionFactory;
import com.mailapp.mail.model.ConnectionTestResult;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.oauth2.OAuth2Service;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailAccountControllerTest {

    @Mock
    private GmailAccountRepository gmailAccountRepository;

    @Mock
    private OAuth2Service oauth2Service;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private MailConnectionFactory mailConnectionFactory;

    private AesCipher aesCipher;
    private GmailAccountController controller;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        aesCipher = new AesCipher("dCPN0qIeMrPFVyn/9c05Pq1O65Re+wsVplG7fEFU4dU=");
        controller = new GmailAccountController(
                gmailAccountRepository,
                oauth2Service,
                auditLogService,
                systemSettingService,
                mailConnectionFactory,
                aesCipher
        );

        userA = new User(1L, "user_a", "a@mailapp.com", Role.USER);
        userB = new User(2L, "user_b", "b@mailapp.com", Role.USER);
    }

    @Test
    @DisplayName("EP-G1: GET /api/gmail-accounts chỉ lấy tài khoản của chính user đăng nhập, không lộ secret")
    void testGetMyGmailAccounts() {
        GmailAccount account1 = new GmailAccount();
        account1.setId(101L);
        account1.setUser(userA);
        account1.setEmail("usera@gmail.com");
        account1.setDisplayName("User A Work");
        account1.setAuthType(AuthType.OAUTH2);
        account1.setRefreshTokenEncrypted("super-secret-encrypted-refresh-token");
        account1.setStatus(AccountStatus.ACTIVE);

        when(gmailAccountRepository.findByUserId(1L)).thenReturn(List.of(account1));

        ResponseEntity<List<GmailAccountResponse>> response = controller.getMyGmailAccounts(userA);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());

        GmailAccountResponse item = response.getBody().get(0);
        assertEquals(101L, item.id());
        assertEquals("usera@gmail.com", item.email());
        assertEquals("User A Work", item.displayName());
        assertEquals(AuthType.OAUTH2, item.authType());
        assertEquals(AccountStatus.ACTIVE, item.status());
    }

    @Test
    @DisplayName("EP-G2: POST /api/gmail-accounts/app-password thành công, test IMAP, mã hóa AES, audit LINK_GMAIL")
    void testLinkAppPasswordSuccess() {
        when(systemSettingService.isAppPasswordLinkEnabled()).thenReturn(true);
        when(systemSettingService.getMaxGmailPerUser()).thenReturn(5);
        when(gmailAccountRepository.findByUserIdAndEmailKey(1L, "work@gmail.com")).thenReturn(Optional.empty());
        when(gmailAccountRepository.countByUserId(1L)).thenReturn(0L);
        when(mailConnectionFactory.testImap(any(MailCredentials.class))).thenReturn(ConnectionTestResult.ok(120));
        when(gmailAccountRepository.save(any(GmailAccount.class))).thenAnswer(inv -> {
            GmailAccount a = inv.getArgument(0);
            a.setId(301L);
            return a;
        });

        LinkAppPasswordRequest req = new LinkAppPasswordRequest("Work@Gmail.com", "TESTPASS12345678", "Work Mail");
        assertFalse(req.toString().contains("TESTPASS12345678"), "toString() tuyệt đối không được lộ App Password");

        MockHttpServletRequest httpReq = new MockHttpServletRequest();
        httpReq.setRemoteAddr("192.168.1.15");

        ResponseEntity<GmailAccountResponse> res = controller.linkGmailWithAppPassword(req, userA, httpReq);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("Work@Gmail.com", res.getBody().email());
        assertEquals("work@gmail.com", res.getBody().emailKey());
        assertEquals(AuthType.APP_PASSWORD, res.getBody().authType());
        assertEquals(GmailAccountResponse.LinkOutcome.CREATED, res.getBody().outcome());
        assertTrue(res.getBody().methods().hasAppPassword());
        assertFalse(res.getBody().methods().hasOAuth());
        assertEquals(AccountStatus.ACTIVE, res.getBody().status());

        verify(gmailAccountRepository).save(argThat(saved ->
                saved.getAppPasswordEncrypted() != null
                        && !saved.getAppPasswordEncrypted().contains("TESTPASS12345678")
                        && "TESTPASS12345678".equals(aesCipher.decrypt(saved.getAppPasswordEncrypted()))
        ));
        verify(auditLogService).log(eq(1L), eq("LINK_GMAIL"), contains("W***@Gmail.com"), eq("192.168.1.15"));
    }

    @Test
    @DisplayName("EP-G2: Trùng (user_id, email_key) đã có OAuth2 -> thêm App Password dự phòng (METHOD_ADDED), giữ nguyên id, displayName và OAuth2")
    void testLinkAppPasswordUpdatesExisting() {
        GmailAccount existing = new GmailAccount();
        existing.setId(302L);
        existing.setUser(userA);
        existing.setEmail("dup@gmail.com");
        existing.setDisplayName("Original Name");
        existing.setAuthType(AuthType.OAUTH2);
        existing.setRefreshTokenEncrypted("ENC_OAUTH_TOKEN");
        existing.setStatus(AccountStatus.ACTIVE);

        when(systemSettingService.isAppPasswordLinkEnabled()).thenReturn(true);
        when(gmailAccountRepository.findByUserIdAndEmailKey(1L, "dup@gmail.com")).thenReturn(Optional.of(existing));
        when(mailConnectionFactory.testImap(any(MailCredentials.class))).thenReturn(ConnectionTestResult.ok(80));
        when(gmailAccountRepository.save(any(GmailAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        LinkAppPasswordRequest req = new LinkAppPasswordRequest("d.u.p+backup@googlemail.com", "NEWPASS123456789", "Updated Name");
        ResponseEntity<GmailAccountResponse> res = controller.linkGmailWithAppPassword(req, userA, new MockHttpServletRequest());

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(302L, res.getBody().id());
        assertEquals("Original Name", existing.getDisplayName(), "Phải giữ nguyên displayName cũ");
        assertEquals(AuthType.OAUTH2, existing.getAuthType(), "Phương thức chính mặc định giữ nguyên OAUTH2");
        assertEquals("ENC_OAUTH_TOKEN", existing.getRefreshTokenEncrypted(), "Không được xóa OAuth2 token");
        assertEquals("NEWPASS123456789", aesCipher.decrypt(existing.getAppPasswordEncrypted()));
        assertEquals(GmailAccountResponse.LinkOutcome.METHOD_ADDED, res.getBody().outcome());
        assertTrue(res.getBody().methods().hasOAuth());
        assertTrue(res.getBody().methods().hasAppPassword());
        assertTrue(res.getBody().message().contains("Đã thêm"));
        assertFalse(res.getBody().message().toLowerCase().contains("lỗi"));
    }

    @Test
    @DisplayName("EP-G2: Bị chặn 403 FEATURE_DISABLED khi ALLOW_APP_PASSWORD_LINK=false")
    void testLinkAppPasswordFeatureDisabled() {
        when(systemSettingService.isAppPasswordLinkEnabled()).thenReturn(false);

        LinkAppPasswordRequest req = new LinkAppPasswordRequest("a@gmail.com", "PASS123456789012", null);
        AppException ex = assertThrows(AppException.class, () ->
                controller.linkGmailWithAppPassword(req, userA, new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("FEATURE_DISABLED", ex.getCode());
        verify(mailConnectionFactory, never()).testImap(any(MailCredentials.class));
        verify(gmailAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("EP-G2: Bị chặn 409 GMAIL_LIMIT_REACHED khi vượt quá MAX_GMAIL_PER_USER")
    void testLinkAppPasswordLimitReached() {
        when(systemSettingService.isAppPasswordLinkEnabled()).thenReturn(true);
        when(systemSettingService.getMaxGmailPerUser()).thenReturn(2);
        when(gmailAccountRepository.findByUserIdAndEmailKey(1L, "third@gmail.com")).thenReturn(Optional.empty());
        when(gmailAccountRepository.countByUserId(1L)).thenReturn(2L);

        LinkAppPasswordRequest req = new LinkAppPasswordRequest("third@gmail.com", "PASS123456789012", null);
        AppException ex = assertThrows(AppException.class, () ->
                controller.linkGmailWithAppPassword(req, userA, new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("GMAIL_LIMIT_REACHED", ex.getCode());
        verify(mailConnectionFactory, never()).testImap(any(MailCredentials.class));
    }

    @Test
    @DisplayName("EP-G2: Sai App Password -> 422 GMAIL_AUTH_FAILED, không lưu DB")
    void testLinkAppPasswordWrongPassword() {
        when(systemSettingService.isAppPasswordLinkEnabled()).thenReturn(true);
        when(systemSettingService.getMaxGmailPerUser()).thenReturn(5);
        when(gmailAccountRepository.findByUserIdAndEmailKey(1L, "wrong@gmail.com")).thenReturn(Optional.empty());
        when(gmailAccountRepository.countByUserId(1L)).thenReturn(0L);
        when(mailConnectionFactory.testImap(any(MailCredentials.class)))
                .thenReturn(ConnectionTestResult.authFailed("Invalid credentials"));

        LinkAppPasswordRequest req = new LinkAppPasswordRequest("wrong@gmail.com", "WRONGPASS1234567", null);
        AppException ex = assertThrows(AppException.class, () ->
                controller.linkGmailWithAppPassword(req, userA, new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("GMAIL_AUTH_FAILED", ex.getCode());
        verify(gmailAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("EP-G2: Tài khoản Gmail chưa bật IMAP -> 422 IMAP_DISABLED, không lưu DB")
    void testLinkAppPasswordImapDisabled() {
        when(systemSettingService.isAppPasswordLinkEnabled()).thenReturn(true);
        when(systemSettingService.getMaxGmailPerUser()).thenReturn(5);
        when(gmailAccountRepository.findByUserIdAndEmailKey(1L, "noimap@gmail.com")).thenReturn(Optional.empty());
        when(gmailAccountRepository.countByUserId(1L)).thenReturn(0L);
        when(mailConnectionFactory.testImap(any(MailCredentials.class)))
                .thenReturn(ConnectionTestResult.imapDisabled("IMAP is disabled"));

        LinkAppPasswordRequest req = new LinkAppPasswordRequest("noimap@gmail.com", "VALIDPASS1234567", null);
        AppException ex = assertThrows(AppException.class, () ->
                controller.linkGmailWithAppPassword(req, userA, new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("IMAP_DISABLED", ex.getCode());
        verify(gmailAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("EP-G2: Chưa bật Xác minh 2 bước -> 422 TWO_STEP_VERIFICATION_REQUIRED")
    void testLinkAppPasswordTwoStepRequired() {
        when(systemSettingService.isAppPasswordLinkEnabled()).thenReturn(true);
        when(systemSettingService.getMaxGmailPerUser()).thenReturn(5);
        when(gmailAccountRepository.findByUserIdAndEmailKey(1L, "no2fa@gmail.com")).thenReturn(Optional.empty());
        when(gmailAccountRepository.countByUserId(1L)).thenReturn(0L);
        when(mailConnectionFactory.testImap(any(MailCredentials.class)))
                .thenReturn(ConnectionTestResult.twoStepRequired(null));

        LinkAppPasswordRequest req = new LinkAppPasswordRequest("no2fa@gmail.com", "VALIDPASS1234567", null);
        AppException ex = assertThrows(AppException.class, () ->
                controller.linkGmailWithAppPassword(req, userA, new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("TWO_STEP_VERIFICATION_REQUIRED", ex.getCode());
    }

    @Test
    @DisplayName("PATCH /api/gmail-accounts/{id}: Đổi displayName thành công và chặn IDOR")
    void testUpdateDisplayNameAndIdor() {
        GmailAccount acc = new GmailAccount();
        acc.setId(401L);
        acc.setUser(userA);
        acc.setEmail("rename@gmail.com");
        acc.setDisplayName("Old Name");
        acc.setAuthType(AuthType.OAUTH2);
        acc.setStatus(AccountStatus.ACTIVE);

        when(gmailAccountRepository.findById(401L)).thenReturn(Optional.of(acc));
        when(gmailAccountRepository.save(any(GmailAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<GmailAccountResponse> okRes = controller.updateDisplayName(401L, Map.of("displayName", "PTIT Mail"), userA);
        assertEquals(HttpStatus.OK, okRes.getStatusCode());
        assertEquals("PTIT Mail", okRes.getBody().displayName());

        // Chặn IDOR: User B không được đổi tên Gmail của User A
        AppException idor = assertThrows(AppException.class, () ->
                controller.updateDisplayName(401L, Map.of("displayName", "Hacked"), userB));
        assertEquals(HttpStatus.FORBIDDEN, idor.getStatus());
    }

    @Test
    @DisplayName("EP-G3: DELETE /api/gmail-accounts/{id} thành công khi xóa tài khoản của chính mình (có revoke OAuth2)")
    void testDeleteOwnGmailAccountSuccess() {
        GmailAccount account = new GmailAccount();
        account.setId(201L);
        account.setUser(userA);
        account.setEmail("usera_oauth@gmail.com");
        account.setAuthType(AuthType.OAUTH2);

        when(gmailAccountRepository.findById(201L)).thenReturn(Optional.of(account));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        ResponseEntity<Map<String, Object>> response = controller.deleteGmailAccount(201L, userA, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(oauth2Service).revokeToken(account);
        verify(gmailAccountRepository).delete(account);
        verify(auditLogService).log(eq(1L), eq("UNLINK_GMAIL"), contains("usera_oauth@gmail.com"), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("EP-G3: DELETE /api/gmail-accounts/{id} ném 403 FORBIDDEN khi user A cố xóa tài khoản của user B")
    void testUserCannotDeleteAnotherUsersGmail() {
        GmailAccount accountOfB = new GmailAccount();
        accountOfB.setId(202L);
        accountOfB.setUser(userB); // Thuộc về User B
        accountOfB.setEmail("userb@gmail.com");

        when(gmailAccountRepository.findById(202L)).thenReturn(Optional.of(accountOfB));

        MockHttpServletRequest request = new MockHttpServletRequest();

        AppException ex = assertThrows(AppException.class, () ->
                controller.deleteGmailAccount(202L, userA, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("FORBIDDEN", ex.getCode());
        verify(gmailAccountRepository, never()).delete(any());
        verify(oauth2Service, never()).revokeToken(any());
    }

    @Test
    @DisplayName("EP-G3: DELETE /api/gmail-accounts/{id} ném 404 ACCOUNT_NOT_FOUND khi ID không tồn tại")
    void testDeleteNonExistentAccount() {
        when(gmailAccountRepository.findById(999L)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();

        AppException ex = assertThrows(AppException.class, () ->
                controller.deleteGmailAccount(999L, userA, request)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("ACCOUNT_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("EP-G3: Lỗi revoke token từ máy chủ Google vẫn xóa thành công tài khoản cục bộ và ghi audit log")
    void testDeleteProceedsEvenWhenRevokeFails() {
        GmailAccount account = new GmailAccount();
        account.setId(203L);
        account.setUser(userA);
        account.setEmail("usera_revoke_error@gmail.com");
        account.setAuthType(AuthType.OAUTH2);

        when(gmailAccountRepository.findById(203L)).thenReturn(Optional.of(account));
        doThrow(new RuntimeException("Google 503 Service Unavailable")).when(oauth2Service).revokeToken(account);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        ResponseEntity<Map<String, Object>> response = controller.deleteGmailAccount(203L, userA, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(gmailAccountRepository).delete(account);
        verify(auditLogService).log(eq(1L), eq("REVOKE_TOKEN_FAILED"), anyString(), eq("10.0.0.1"));
        verify(auditLogService).log(eq(1L), eq("UNLINK_GMAIL"), contains("usera_revoke_error@gmail.com"), eq("10.0.0.1"));
    }
}
