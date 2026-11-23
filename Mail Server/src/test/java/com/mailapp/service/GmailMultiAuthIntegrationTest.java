package com.mailapp.service;

import com.mailapp.dto.GmailAccountResponse.LinkOutcome;
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
import com.mailapp.repository.UserRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Kiểm thử tích hợp toàn diện cho đa phương thức đăng nhập trên cùng 1 hộp thư Gmail:
 * - Upsert 2 chiều (OAuth -> AppPassword, AppPassword -> OAuth, cùng phương thức 2 lần, biến thể dấu chấm/+tag/googlemail.com)
 * - Race condition 2 request đồng thời (xử lý an toàn qua ràng buộc DB UNIQUE)
 * - Chống IDOR (user B không đụng được tài khoản của user A) & hai user cùng liên kết 1 địa chỉ Gmail
 * - Đổi preferredMethod và gỡ phương thức (thu hồi token, xóa bí mật, yêu cầu confirmUnlink=true cho phương thức cuối)
 * - GET /api/gmail-accounts không bao giờ lộ bí mật
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GmailMultiAuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private GmailAccountRepository gmailAccountRepository;
    @Autowired private GmailAccountLinkService linkService;
    @Autowired private SystemSettingService systemSettingService;
    @Autowired private AesCipher aesCipher;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockBean private MailConnectionFactory mailConnectionFactory;
    @MockBean private OAuth2Service oauth2Service;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        systemSettingService.updateSetting("ALLOW_APP_PASSWORD_LINK", "true", null);
        systemSettingService.updateSetting("ALLOW_OAUTH2_LINK", "true", null);
        systemSettingService.updateSetting("MAX_GMAIL_PER_USER", "5", null);

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userA = createUser("multi_a_" + suffix, "multi_a_" + suffix + "@example.com");
        userB = createUser("multi_b_" + suffix, "multi_b_" + suffix + "@example.com");
        tokenA = jwtProvider.generateToken(userA);
        tokenB = jwtProvider.generateToken(userB);

        when(mailConnectionFactory.testImap(any(MailCredentials.class)))
                .thenReturn(ConnectionTestResult.ok(45));
    }

    private User createUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName(username);
        u.setPasswordHash(passwordEncoder.encode("StrongPass123!"));
        u.setRole(Role.USER);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("Upsert 2 chiều: OAuth -> AppPassword -> AppPassword lần 2 (giữ id, displayName, cả 2 bí mật, đúng outcome)")
    void upsertOAuthThenAppPasswordAndUpdateAgain() throws Exception {
        // 1. Tạo mới bằng OAuth2
        var first = linkService.upsertOAuth2(
                userA, "John.Doe@gmail.com", aesCipher.encrypt("refresh-secret-1"),
                "John Work", null, LocalDateTime.now().plusHours(1));
        assertEquals(LinkOutcome.CREATED, first.outcome());
        Long originalId = first.account().getId();

        // 2. Thêm App Password qua API với biến thể +tag & googlemail.com
        mockMvc.perform(post("/api/gmail-accounts/app-password")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "johndoe+backup@googlemail.com",
                                  "appPassword": "abcd efgh ijkl mnop",
                                  "displayName": "Ignored Display Name"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId))
                .andExpect(jsonPath("$.displayName").value("John Work"))
                .andExpect(jsonPath("$.preferredMethod").value("OAUTH2"))
                .andExpect(jsonPath("$.activeMethod").value("OAUTH2"))
                .andExpect(jsonPath("$.fallbackActive").value(false))
                .andExpect(jsonPath("$.methods.hasOAuth").value(true))
                .andExpect(jsonPath("$.methods.hasAppPassword").value(true))
                .andExpect(jsonPath("$.outcome").value("METHOD_ADDED"))
                .andExpect(jsonPath("$.message", containsString("Đã thêm")))
                .andExpect(content().string(not(containsString("abcdefghijklmnop"))))
                .andExpect(content().string(not(containsString("refresh-secret-1"))));

        // 3. Cập nhật App Password lần 2 -> METHOD_UPDATED
        mockMvc.perform(post("/api/gmail-accounts/app-password")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "j.o.h.n.d.o.e@gmail.com",
                                  "appPassword": "zzzz yyyy xxxx wwww"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId))
                .andExpect(jsonPath("$.outcome").value("METHOD_UPDATED"));

        // Kiểm tra DB chỉ có đúng 1 dòng, giữ cả 2 bí mật
        List<GmailAccount> rows = gmailAccountRepository.findByUserId(userA.getId());
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).hasOAuth());
        assertTrue(rows.get(0).hasAppPassword());
        assertEquals("zzzzyyyyxxxxwwww", aesCipher.decrypt(rows.get(0).getAppPasswordEncrypted()));
        assertEquals("refresh-secret-1", aesCipher.decrypt(rows.get(0).getRefreshTokenEncrypted()));
    }

    @Test
    @DisplayName("Upsert chiều ngược: AppPassword trước -> thêm OAuth2 sau (chuyển preferred sang OAUTH2, giữ nguyên AppPassword & displayName)")
    void upsertAppPasswordThenOAuth2() {
        var step1 = linkService.upsertAppPassword(
                userA, "reverse.user@gmail.com", aesCipher.encrypt("app-pass-rev"), "My Custom Name", "127.0.0.1");
        assertEquals(LinkOutcome.CREATED, step1.outcome());
        assertEquals(AuthType.APP_PASSWORD, step1.account().getPreferredMethod());
        Long id = step1.account().getId();

        var step2 = linkService.upsertOAuth2(
                userA, "REVERSEUSER+work@googlemail.com", aesCipher.encrypt("oauth-refresh-rev"),
                "Google Name", null, LocalDateTime.now().plusHours(1));
        assertEquals(LinkOutcome.METHOD_ADDED, step2.outcome());
        assertEquals(id, step2.account().getId());
        assertEquals("My Custom Name", step2.account().getDisplayName(), "Phải giữ nguyên displayName cũ");
        assertEquals(AuthType.OAUTH2, step2.account().getPreferredMethod(), "Mặc định OAUTH2 làm phương thức chính");
        assertTrue(step2.account().hasOAuth());
        assertTrue(step2.account().hasAppPassword());

        var step3 = linkService.upsertOAuth2(
                userA, "reverseuser@gmail.com", aesCipher.encrypt("oauth-refresh-rev-2"),
                "Google Name 2", null, LocalDateTime.now().plusHours(1));
        assertEquals(LinkOutcome.METHOD_UPDATED, step3.outcome());
        assertEquals(id, step3.account().getId());
        assertEquals(1, gmailAccountRepository.findByUserId(userA.getId()).size());
    }

    @Test
    @DisplayName("Race condition: 2 request đồng thời (OAuth2 + AppPassword) cho cùng email_key chỉ tạo đúng 1 dòng chứa cả 2 phương thức")
    void concurrentRaceHandledByUniqueConstraintAndRetry() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<GmailAccountLinkService.UpsertResult> f1 = pool.submit(() -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return linkService.upsertAppPassword(
                    userA, "race.test@gmail.com", aesCipher.encrypt("race-app-pass-16"), "Race Mail", "127.0.0.1");
        });
        Future<GmailAccountLinkService.UpsertResult> f2 = pool.submit(() -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            return linkService.upsertOAuth2(
                    userA, "racetest+1@googlemail.com", aesCipher.encrypt("race-refresh-tok"), "Race Mail",
                    null, LocalDateTime.now().plusHours(1));
        });

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        GmailAccountLinkService.UpsertResult r1 = f1.get(10, TimeUnit.SECONDS);
        GmailAccountLinkService.UpsertResult r2 = f2.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertNotNull(r1.account().getId());
        assertEquals(r1.account().getId(), r2.account().getId(), "Hai request đồng thời phải hội tụ về cùng 1 dòng DB");

        List<GmailAccount> rows = gmailAccountRepository.findByUserId(userA.getId());
        assertEquals(1, rows.size(), "Tuyệt đối không được tạo 2 dòng trùng email_key");
        assertTrue(rows.get(0).hasOAuth(), "Phải có OAuth2 sau race");
        assertTrue(rows.get(0).hasAppPassword(), "Phải có App Password sau race");
    }

    @Test
    @DisplayName("IDOR & đa người dùng: User B bị 403 trên tài khoản User A, nhưng User A và User B được phép liên kết cùng 1 Gmail")
    void idorBlockedAndTwoUsersCanLinkSameGmail() throws Exception {
        var accA = linkService.upsertOAuth2(
                userA, "shared.mailbox@gmail.com", aesCipher.encrypt("ref-a"), "A Mail", null, LocalDateTime.now().plusHours(1));
        linkService.upsertAppPassword(
                userA, "sharedmailbox@gmail.com", aesCipher.encrypt("pass-a-12345678"), "A Mail", "127.0.0.1");

        // User B liên kết CÙNG hộp thư shared.mailbox@gmail.com -> thành công, dòng độc lập!
        var accB = linkService.upsertAppPassword(
                userB, "Shared.Mailbox@gmail.com", aesCipher.encrypt("pass-b-12345678"), "B Mail", "127.0.0.1");
        assertNotEquals(accA.account().getId(), accB.account().getId());

        // User B không được đổi preferredMethod của User A -> 403
        mockMvc.perform(put("/api/gmail-accounts/" + accA.account().getId() + "/preferred-method")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredMethod\": \"APP_PASSWORD\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // User B không được gỡ phương thức của User A -> 403
        mockMvc.perform(delete("/api/gmail-accounts/" + accA.account().getId() + "/methods/APP_PASSWORD")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // User A đổi preferredMethod sang APP_PASSWORD -> 200 OK
        mockMvc.perform(put("/api/gmail-accounts/" + accA.account().getId() + "/preferred-method")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredMethod\": \"APP_PASSWORD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredMethod").value("APP_PASSWORD"))
                .andExpect(jsonPath("$.activeMethod").value("APP_PASSWORD"));
    }

    @Test
    @DisplayName("Gỡ phương thức: gỡ OAUTH2 thu hồi token & xóa bí mật; gỡ cái cuối không confirmUnlink -> 409; có confirmUnlink=true -> xóa liên kết")
    void removeMethodsLifecycleAndLastMethodGuard() throws Exception {
        var acc = linkService.upsertOAuth2(
                userA, "remove.demo@gmail.com", aesCipher.encrypt("ref-remove"), "Remove Demo", null, LocalDateTime.now().plusHours(1));
        linkService.upsertAppPassword(
                userA, "removedemo@gmail.com", aesCipher.encrypt("app-remove-1234"), "Remove Demo", "127.0.0.1");
        Long id = acc.account().getId();

        // 1. Gỡ OAUTH2 (còn APP_PASSWORD) -> revokeToken best-effort, xóa refreshTokenEncrypted, preferred chuyển sang APP_PASSWORD
        mockMvc.perform(delete("/api/gmail-accounts/" + id + "/methods/OAUTH2")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlinked").value(false))
                .andExpect(jsonPath("$.account.methods.hasOAuth").value(false))
                .andExpect(jsonPath("$.account.methods.hasAppPassword").value(true))
                .andExpect(jsonPath("$.account.preferredMethod").value("APP_PASSWORD"));

        verify(oauth2Service, times(1)).revokeToken(any(GmailAccount.class));
        GmailAccount afterRemoveOAuth = gmailAccountRepository.findById(id).orElseThrow();
        assertNull(afterRemoveOAuth.getRefreshTokenEncrypted());
        assertNotNull(afterRemoveOAuth.getAppPasswordEncrypted());

        // 2. Gỡ APP_PASSWORD (phương thức cuối cùng) KHÔNG có confirmUnlink=true -> 409 CONFIRM_UNLINK_REQUIRED
        mockMvc.perform(delete("/api/gmail-accounts/" + id + "/methods/APP_PASSWORD")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIRM_UNLINK_REQUIRED"));
        assertTrue(gmailAccountRepository.findById(id).isPresent());

        // 3. Gỡ APP_PASSWORD CÓ confirmUnlink=true -> xóa luôn liên kết
        mockMvc.perform(delete("/api/gmail-accounts/" + id + "/methods/APP_PASSWORD?confirmUnlink=true")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlinked").value(true));
        assertTrue(gmailAccountRepository.findById(id).isEmpty());
    }

    @Test
    @DisplayName("GET /api/gmail-accounts trả đầy đủ methods, preferredMethod, activeMethod, fallbackActive, status và không lộ bí mật")
    void getMyGmailAccountsReturnsMetadataWithoutSecrets() throws Exception {
        var acc = linkService.upsertOAuth2(
                userA, "meta.check@gmail.com", aesCipher.encrypt("SUPER_SECRET_REFRESH"), "Meta Mail", null, LocalDateTime.now().plusHours(1));
        linkService.upsertAppPassword(
                userA, "metacheck@gmail.com", aesCipher.encrypt("SUPER_SECRET_APP_PW"), "Meta Mail", "127.0.0.1");

        GmailAccount entity = gmailAccountRepository.findById(acc.account().getId()).orElseThrow();
        entity.setStatus(AccountStatus.NEEDS_REAUTH);
        entity.setFallbackActive(true);
        gmailAccountRepository.save(entity);

        mockMvc.perform(get("/api/gmail-accounts")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].methods.hasOAuth").value(true))
                .andExpect(jsonPath("$[0].methods.hasAppPassword").value(true))
                .andExpect(jsonPath("$[0].preferredMethod").value("OAUTH2"))
                .andExpect(jsonPath("$[0].activeMethod").value("APP_PASSWORD"))
                .andExpect(jsonPath("$[0].fallbackActive").value(true))
                .andExpect(jsonPath("$[0].status").value("NEEDS_REAUTH"))
                .andExpect(content().string(not(containsString("SUPER_SECRET_REFRESH"))))
                .andExpect(content().string(not(containsString("SUPER_SECRET_APP_PW"))))
                .andExpect(content().string(not(containsString("refreshToken"))))
                .andExpect(content().string(not(containsString("appPassword"))));
    }
}

