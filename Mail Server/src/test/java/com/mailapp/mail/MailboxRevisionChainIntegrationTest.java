package com.mailapp.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.mailapp.config.GoogleOAuthConfig;
import com.mailapp.exception.GmailAccountChangedException;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.model.*;
import com.mailapp.oauth2.OAuth2ServiceImpl;
import com.mailapp.oauth2.OAuth2StateStore;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.repository.UserRepository;
import com.mailapp.security.AesCipher;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Detached rows and genuine JPA commits; no HTTP application login or authenticated UI. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mail_revision_chain;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class MailboxRevisionChainIntegrationTest {
    @Autowired GmailAccountRepository accounts;
    @Autowired UserRepository users;
    @Autowired AesCipher cipher;
    @Autowired PasswordEncoder encoder;

    private User owner;
    private Long mailboxId;
    private final String appFixture = UUID.randomUUID().toString();
    private final String email = "revision-" + UUID.randomUUID() + "@example.test";

    @BeforeEach
    void createFixture() {
        owner = new User();
        owner.setUsername("rev_" + UUID.randomUUID().toString().replace("-", ""));
        owner.setEmail(email);
        owner.setFullName("Revision fixture");
        owner.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        owner.setRole(Role.USER);
        owner.setEnabled(true);
        owner.setMustChangePassword(false);
        owner = users.save(owner);
        GmailAccount account = new GmailAccount();
        account.setUser(owner);
        account.setEmail(email);
        account.setAuthType(AuthType.OAUTH2);
        account.setStatus(AccountStatus.ACTIVE);
        account.setRefreshTokenEncrypted(cipher.encrypt("synthetic-initial-grant"));
        account.setAppPasswordEncrypted(cipher.encrypt(appFixture));
        mailboxId = accounts.save(account).getId();
    }

    @AfterEach
    void removeFixture() {
        if (mailboxId != null) accounts.deleteById(mailboxId);
        if (owner != null && owner.getId() != null) users.deleteById(owner.getId());
    }

    private GmailAccount load() {
        return accounts.findByIdAndUserId(mailboxId, owner.getId()).orElseThrow();
    }

    private OAuth2ServiceImpl oauth(MockWebServer provider, AuditLogService audit) {
        GoogleOAuthConfig config = new GoogleOAuthConfig();
        ReflectionTestUtils.setField(config, "clientId", "fixture-client");
        ReflectionTestUtils.setField(config, "clientSecret", "fixture-only");
        ReflectionTestUtils.setField(config, "redirectUri", "https://localhost:8443/api/oauth2/callback");
        ReflectionTestUtils.setField(config, "tokenUrl", provider.url("/token").toString());
        return new OAuth2ServiceImpl(config, new OAuth2StateStore(), accounts, users,
                mock(SystemSettingService.class), audit, cipher, config.googleRestClient());
    }

    @Test
    void rotatedGrantCommitAdvancesDetachedSnapshotAndRejectsStaleBeforeProviderOrCache() throws Exception {
        try (MockWebServer provider = new MockWebServer()) {
            provider.start(java.net.InetAddress.getLoopbackAddress(), 0);
            var service = oauth(provider, mock(AuditLogService.class));
            GmailAccount first = load();
            GmailAccount stale = load();
            long before = first.getRevision();
            provider.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("{\"access_token\":\"fixture-access\",\"refresh_token\":\"fixture-rotated\",\"expires_in\":3600}"));
            assertNotNull(service.getFreshAccessToken(first));
            GmailAccount persisted = load();
            assertEquals(before + 1, persisted.getRevision());
            assertEquals(persisted.getRevision(), first.getRevision());
            assertTrue("fixture-rotated".equals(cipher.decrypt(persisted.getRefreshTokenEncrypted())));
            assertEquals(1, provider.getRequestCount());

            // No stale refresh request (including one using an old cache entry).
            assertThrows(GmailAccountChangedException.class, () -> service.getFreshAccessToken(stale));
            assertNotNull(service.getFreshAccessToken(first));
            assertEquals(1, provider.getRequestCount());

            // Same request can persist subsequent metadata using its own revision.
            first.setLastVerifiedAt(java.time.LocalDateTime.now());
            accounts.save(first);
            assertNotNull(load().getLastVerifiedAt());
            assertThrows(GmailAccountChangedException.class, () -> service.getFreshAccessToken(first));
            assertEquals(1, provider.getRequestCount());
        }
    }

    @Test
    void invalidGrantAndSuccessfulFallbackCommitSuccessiveDetachedRevisions() throws Exception {
        AuditLogService audit = mock(AuditLogService.class);
        GreenMail green = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS));
        try (MockWebServer provider = new MockWebServer()) {
            provider.start(java.net.InetAddress.getLoopbackAddress(), 0);
            green.start();
            green.setUser(email, appFixture);
            MailConnectionProperties props = new MailConnectionProperties();
            props.setImapHost("127.0.0.1");
            props.setImapPort(green.getImaps().getPort());
            props.setLocalTestTransport(true);
            props.setRetryEnabled(false);
            props.setPoolEnabled(false);
            props.setConnectionTimeoutMs(3000);
            props.setTimeoutMs(3000);
            props.setWriteTimeoutMs(3000);
            OAuth2ServiceImpl service = oauth(provider, audit);
            DefaultMailConnectionFactory factory = new DefaultMailConnectionFactory(
                    props, cipher, service::getFreshAccessToken, null, accounts, audit);
            try {
                GmailAccount snapshot = load();
                long before = snapshot.getRevision();
                provider.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                        .setBody("{\"error\":\"invalid_grant\"}"));
                jakarta.mail.Store store;
                try (ImapConnection connection = factory.openImap(snapshot)) {
                    assertTrue(connection.isConnected());
                    store = connection.getStore();
                }
                assertFalse(store.isConnected());
                GmailAccount persisted = load();
                assertEquals(before + 2, persisted.getRevision());
                assertEquals(persisted.getRevision(), snapshot.getRevision());
                assertEquals(AccountStatus.NEEDS_REAUTH, persisted.getStatus());
                assertTrue(persisted.isFallbackActive());
                assertEquals(AuthType.APP_PASSWORD, persisted.getActiveMethod());
                verify(audit, times(1)).log(eq(owner.getId()), eq("GMAIL_NEEDS_REAUTH"), anyString(), (String) isNull());
                verify(audit, times(1)).log(eq(owner.getId()), eq("FALLBACK_TO_APP_PASSWORD"), anyString(), (String) isNull());
                assertEquals(1, provider.getRequestCount());

                // Removing the fallback must invalidate the old snapshot before
                // a raw adapter or wrapper can use its credential again.
                persisted.setAppPasswordEncrypted(null);
                persisted.setFallbackActive(false);
                accounts.save(persisted);
                assertThrows(GmailAccountChangedException.class, () -> factory.openImap(snapshot));
                assertThrows(GmailAccountChangedException.class, () -> factory.createImapStore(snapshot));
                assertThrows(GmailAccountChangedException.class, () -> factory.createSmtpTransport(snapshot));
                assertEquals(1, provider.getRequestCount());
            } finally {
                factory.shutdownPool();
            }
        } finally {
            green.stop();
        }
    }
}
