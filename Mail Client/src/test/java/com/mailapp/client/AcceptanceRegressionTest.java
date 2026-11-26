package com.mailapp.client;

import com.mailapp.client.model.LoginResponse;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.MailClientDtos.MailAddressDto;
import com.mailapp.client.model.MailClientDtos.MailDetailResponse;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.ui.frames.MailboxViewModel;
import com.mailapp.client.util.ComposeValidation;
import com.mailapp.client.util.FileUtil;
import com.mailapp.client.util.SessionStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AcceptanceRegressionTest {
    @Test
    void replyPlanHonorsReplyToExcludesSelfAndBccAndKeepsSafeThread() {
        var reply = new MailAddressDto(null, "reply@example.test");
        var copy = new MailAddressDto(null, "copy@example.test");
        var self = new MailAddressDto(null, "My.Name+tag@googlemail.com");
        var source = new MailDetailResponse(1, 1, new MailAddressDto(null, "sender@example.test"),
                List.of(self, reply, copy), List.of(new MailAddressDto(null, "COPY@example.test")),
                List.of(new MailAddressDto(null, "private@example.test")), List.of(reply), null,
                "<message@example.test>", null, "<root@example.test> <message@example.test>", "Subject",
                null, "<p>Hello &amp; world</p><img src='https://example.test/image'>", false, true, List.of(), null);
        var plan = ComposeValidation.replyPlan(source, "myname@gmail.com", true);
        assertEquals(List.of("reply@example.test"), plan.to());
        assertEquals(List.of("copy@example.test"), plan.cc());
        assertEquals("<message@example.test>", plan.inReplyTo());
        assertEquals("<root@example.test> <message@example.test>", plan.references());
        assertEquals("Hello & world", ComposeValidation.quoteText(source));
    }

    @Test
    void replyWithoutSenderOrMessageIdDoesNotProduceBogusHeader() {
        var source = new MailDetailResponse(1, 1, new MailAddressDto(null, null), List.of(), List.of(), List.of(), List.of(),
                null, null, null, "<root@example.test>\r\nX-Injected: value", "", "plain body", null,
                false, false, List.of(), null);
        var plan = ComposeValidation.replyPlan(source, "self@example.test", false);
        assertTrue(plan.to().isEmpty());
        assertNull(plan.inReplyTo());
        assertEquals("<root@example.test>", plan.references());
        assertEquals("plain body", ComposeValidation.quoteText(source));
    }
    @Test
    void composeDetectsChangedSelectedAttachmentAndRejectsUnsafeLinks(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("attachment.txt"), "selected");
        var stamp = ComposeValidation.AttachmentStamp.inspect(file.toFile());
        assertTrue(stamp.matches(file.toFile()));
        Files.writeString(file, "changed contents");
        assertFalse(stamp.matches(file.toFile()));
        Files.delete(file);
        assertFalse(stamp.matches(file.toFile()));
        assertThrows(IOException.class, () -> ComposeValidation.AttachmentStamp.inspect(dir.toFile()));
        assertEquals("https://example.test/?x=1&amp;y=2", ComposeValidation.safeLinkHref(" https://example.test/?x=1&y=2 "));
        assertThrows(IllegalArgumentException.class, () -> ComposeValidation.safeLinkHref("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> ComposeValidation.safeLinkHref("https://user@example.test/"));
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    @Test
    void loginVerifiesAuthoritativeUserBeforePublishingToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean verified = new AtomicBoolean();
        server.createContext("/api/auth/login", ex -> json(ex, 200,
                """
                {"accessToken":"test-session-token","tokenType":"Bearer","expiresIn":86400,
                "user":{"id":7,"username":"tester","role":"USER","mustChangePassword":false}}
                """));
        server.createContext("/api/auth/me", ex -> {
            verified.set("Bearer test-session-token".equals(ex.getRequestHeaders().getFirst("Authorization")));
            json(ex, verified.get() ? 200 : 401,
                    """
                    {"id":7,"username":"tester","role":"ADMIN","mustChangePassword":true}
                    """);
        });
        server.start();
        try {
            ApiClient client = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
            LoginResponse response = client.verifyLogin(client.requestLogin("tester", "fake-password"));
            assertTrue(verified.get());
            assertEquals("ADMIN", response.user().role());
            assertTrue(response.user().mustChangePassword());
            assertNull(client.getAccessToken());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void oauthPollTracksExactLinkSessionEvenIfMailboxAlreadyExists() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger polls = new AtomicInteger();
        AtomicBoolean correctTarget = new AtomicBoolean();
        server.createContext("/api/oauth2/authorize-url", ex -> {
            correctTarget.set("intent=UPSERT&accountId=42".equals(ex.getRequestURI().getQuery())
                    && "Bearer fake-jwt".equals(ex.getRequestHeaders().getFirst("Authorization")));
            json(ex, 200, "{\"authorizeUrl\":\"https://accounts.google.com/o/oauth2/v2/auth\",\"pollToken\":\"opaque-poll\"}");
        });
        server.createContext("/api/oauth2/link/poll", ex -> {
            boolean correct = "pollToken=opaque-poll".equals(ex.getRequestURI().getQuery())
                    && "Bearer fake-jwt".equals(ex.getRequestHeaders().getFirst("Authorization"));
            if (!correct) { json(ex, 404, "{}"); return; }
            if (polls.incrementAndGet() == 1) json(ex, 202, "{\"status\":\"PENDING\"}");
            else json(ex, 200, "{\"status\":\"SUCCESS\",\"accountId\":42,\"outcome\":\"METHOD_UPDATED\"}");
        });
        server.start();
        try {
            ApiClient client = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
            client.setAccessToken("fake-jwt");
            var start = client.startOAuth2Link(42L);
            var result = MailboxViewModel.pollForOAuthLink(() -> client.pollOAuth2Link(start.pollToken()),
                    3, 0, new AtomicBoolean(), null);
            assertTrue(correctTarget.get());
            assertEquals("SUCCESS", result.status());
            assertEquals(42L, result.accountId());
            assertEquals(2, polls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void oauthPollReportsCallbackFailureAndTimeoutWithoutInferringSuccess() throws Exception {
        var denied = MailboxViewModel.pollForOAuthLink(
                () -> new ApiClient.OAuthLinkPollDto("FAILED", "ACCESS_DENIED", "Denied", null),
                5, 0, new AtomicBoolean(), null);
        assertEquals("ACCESS_DENIED", denied.code());
        var timeout = MailboxViewModel.pollForOAuthLink(
                () -> new ApiClient.OAuthLinkPollDto("PENDING", null, null, null),
                2, 0, new AtomicBoolean(), null);
        assertEquals("TIMEOUT", timeout.code());
    }

    @Test
    void firstSaveChooserDirectoryExistsUnderConfiguredRoot(@TempDir Path dir) throws Exception {
        Path downloads = dir.resolve("configured-downloads");
        Path expected = FileUtil.attachmentDirectory(downloads, "fixture@test.local");
        assertFalse(Files.exists(expected));
        Path prepared = FileUtil.prepareAttachmentDirectory(downloads, "fixture@test.local");
        assertEquals(expected.toAbsolutePath().normalize(), prepared);
        assertTrue(Files.isDirectory(prepared));
        Path existing = prepared.resolve("existing.txt");
        Files.writeString(existing, "keep");
        assertEquals(prepared, FileUtil.prepareAttachmentDirectory(downloads, "fixture@test.local"));
        assertEquals("keep", Files.readString(existing));
    }

    @Test
    void uniqueDownloadsAndSessionCacheNeverOverwriteOrLeakAcrossSessions(@TempDir Path dir) throws Exception {
        Path original = dir.resolve("report.txt");
        Files.writeString(original, "original");
        ApiClient.DownloadResult saved = FileUtil.saveUnique(original, temporary -> {
            Files.writeString(temporary, "new");
            return new ApiClient.DownloadResult(temporary.toFile(), 3, "checksum");
        });
        Path downloaded = saved.file().toPath();
        assertEquals("report (1).txt", downloaded.getFileName().toString());
        assertEquals("original", Files.readString(original));
        assertEquals("new", Files.readString(downloaded));

        SessionStore session = SessionStore.getInstance();
        session.clearSession();
        try {
            session.saveSession("first-session", null);
            long generation = session.getGeneration();
            var key = new SessionStore.AttachmentKey(2L, "INBOX", 9, 3, 0);
            session.rememberDownloadedAttachment(generation, key, downloaded);
            assertEquals(downloaded.toAbsolutePath(), session.findDownloadedAttachment(key));
            assertNull(session.findDownloadedAttachment(new SessionStore.AttachmentKey(2L, "INBOX", 10, 3, 0)));
            Files.delete(downloaded);
            assertNull(session.findDownloadedAttachment(key));
            session.saveSession("second-session", null);
            session.rememberDownloadedAttachment(generation, key, original);
            assertNull(session.findDownloadedAttachment(key));
        } finally {
            session.clearSession();
        }
    }

    @Test
    void failedDownloadRemovesTemporaryAndReservedFiles(@TempDir Path dir) throws Exception {
        Path original = Files.writeString(dir.resolve("report.txt"), "original");
        assertThrows(IOException.class, () -> FileUtil.saveUnique(original, temporary -> {
            Files.writeString(temporary, "partial");
            throw new IOException("network closed");
        }));
        try (var files = Files.list(dir)) {
            assertEquals(List.of(original), files.toList());
        }
        assertEquals("original", Files.readString(original));
    }

    @Test
    void composeRejectsInvalidRecipientsOversizedBodyAndChangedFiles(@TempDir Path dir) throws Exception {
        assertEquals(List.of("a@example.com", "b@example.com"), ComposeValidation.parseRecipients("a@example.com; b@example.com"));
        assertThrows(IllegalArgumentException.class, () -> ComposeValidation.parseRecipients("a@example.com; invalid"));
        assertThrows(IllegalArgumentException.class, () -> ComposeValidation.parseRecipients("a@example.com\r\nb@example.com"));
        var to = List.of("a@example.com");
        assertNotNull(ComposeValidation.validate(to, List.of("bad"), List.of(), "ok", "body", List.of(), 1));
        assertNotNull(ComposeValidation.validate(to, List.of(), List.of(), "a".repeat(201), "body", List.of(), 1));
        assertNotNull(ComposeValidation.validate(to, List.of(), List.of(), "ok", "é".repeat(600_000), List.of(), 1));
        Path file = Files.writeString(dir.resolve("doc.txt"), "small");
        assertNull(ComposeValidation.validate(to, List.of(), List.of(), "ok", "body", List.of(file.toFile()), 1));
        try (RandomAccessFile random = new RandomAccessFile(file.toFile(), "rw")) { random.setLength(1024 * 1024 + 1); }
        assertNotNull(ComposeValidation.validate(to, List.of(), List.of(), "ok", "body", List.of(file.toFile()), 1));
        Files.delete(file);
        assertNotNull(ComposeValidation.validate(to, List.of(), List.of(), "ok", "body", List.of(file.toFile()), 1));
    }

    @Test
    void retiredClientNeverInheritsNextUsersToken() {
        ApiClient old = new ApiClient("http://localhost:19999");
        old.setAccessToken("old-token");
        ApiClient fresh = old.newUnauthenticatedClient();
        assertNull(fresh.getAccessToken());
        old.clearAccessToken();
        fresh.setAccessToken("new-token");
        assertNull(old.getAccessToken());
        assertEquals("new-token", fresh.getAccessToken());
    }

    @Test
    void publicPageSizeAndHttpsOnlyRules() {
        assertEquals(50, new PublicConfigDto(true,true,true,false,true,5,25,50).effectiveMailPageSize());
        assertEquals(20, new PublicConfigDto(true,true,true,false,true,5,25,0).effectiveMailPageSize());
        assertThrows(IllegalArgumentException.class, () -> new ApiClient("http://localhost.example.com:8443"));
        assertThrows(IllegalArgumentException.class, () -> new ApiClient("http://example.com"));
    }
}
