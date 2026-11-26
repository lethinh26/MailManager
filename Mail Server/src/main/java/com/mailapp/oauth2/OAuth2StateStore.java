package com.mailapp.oauth2;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.mailapp.dto.LoginResponse;
import com.mailapp.dto.GmailAccountResponse.LinkOutcome;
import com.mailapp.model.LinkIntent;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Quản lý CSRF state ngắn hạn cho luồng Google OAuth2 (cả Liên kết Gmail và Đăng nhập Google),
 * PKCE code_verifier và phiên poll đăng nhập dùng một lần trong RAM (Caffeine).
 *
 * @author Lê Thanh Cường (Module M06)
 */
@Component
public class OAuth2StateStore {

    public enum FlowType {
        LINK_GMAIL,
        GOOGLE_LOGIN
    }

    public enum PollStatus {
        PENDING,
        SUCCESS,
        FAILED
    }

    /**
     * Dữ liệu lưu kèm mỗi state.
     */
    public record StateEntry(Long userId, String codeVerifier, Instant createdAt, FlowType flowType,
                             String pollToken, Long accountId, LinkIntent intent) {
        public StateEntry(Long userId, String codeVerifier, Instant createdAt) {
            this(userId, codeVerifier, createdAt, FlowType.LINK_GMAIL, null, null, LinkIntent.CREATE);
        }
        public StateEntry(Long userId, String codeVerifier, Instant createdAt, FlowType flowType, String pollToken) {
            this(userId, codeVerifier, createdAt, flowType, pollToken, null, LinkIntent.CREATE);
        }
        public StateEntry(Long userId, String codeVerifier, Instant createdAt, FlowType flowType,
                          String pollToken, Long accountId) {
            this(userId, codeVerifier, createdAt, flowType, pollToken, accountId,
                    accountId == null ? LinkIntent.CREATE : LinkIntent.UPSERT);
        }
    }

    public record LoginPollEntry(PollStatus status, LoginResponse loginResponse, String errorCode, String errorMessage) {}
    public record LinkPollEntry(Long userId, PollStatus status, Long accountId, LinkOutcome outcome,
                                String code, String message) {}
    public record LinkStatePair(String state, String pollToken) {}

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Cache state CSRF trong bộ nhớ với Caffeine, TTL 5 phút.
    private final Cache<String, StateEntry> cache;

    // Cache phiên chờ đăng nhập Google (key = pollToken 256-bit), TTL 5 phút, dùng 1 lần khi hoàn tất
    private final Cache<String, LoginPollEntry> loginPollCache;

    private final Cache<String, LinkPollEntry> linkPollCache;

    public OAuth2StateStore() {
        this(Ticker.systemTicker());
    }

    OAuth2StateStore(Ticker ticker) {
        cache = Caffeine.newBuilder().ticker(ticker)
                .expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10000).build();
        loginPollCache = Caffeine.newBuilder().ticker(ticker)
                .expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(5000).build();
        linkPollCache = Caffeine.newBuilder().ticker(ticker)
                .expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10000).build();
    }

    /**
     * Sinh state cho luồng Liên kết Gmail (user đã đăng nhập).
     */
    public String generateState(Long userId, String codeVerifier) {
        String state = randomToken(24);
        cache.put(state, new StateEntry(userId, codeVerifier, Instant.now(), FlowType.LINK_GMAIL, null));
        return state;
    }

    public LinkStatePair generateLinkState(Long userId, String codeVerifier, Long accountId) {
        return generateLinkState(userId, codeVerifier, accountId,
                accountId == null ? LinkIntent.CREATE : LinkIntent.UPSERT);
    }

    public LinkStatePair generateLinkState(Long userId, String codeVerifier, Long accountId, LinkIntent intent) {
        String state = randomToken(24);
        String pollToken = randomToken(32);
        cache.put(state, new StateEntry(userId, codeVerifier, Instant.now(), FlowType.LINK_GMAIL, pollToken, accountId, intent));
        linkPollCache.put(pollToken, new LinkPollEntry(userId, PollStatus.PENDING, accountId, null, null, null));
        return new LinkStatePair(state, pollToken);
    }

    public void completeLinkSuccess(String pollToken, Long accountId, LinkOutcome outcome, String message) {
        if (pollToken != null) {
            linkPollCache.asMap().computeIfPresent(pollToken, (key, entry) ->
                    new LinkPollEntry(entry.userId(), PollStatus.SUCCESS, accountId, outcome, null, message));
        }
    }

    public void completeLinkFailure(String pollToken, String code, String message) {
        if (pollToken != null) {
            linkPollCache.asMap().computeIfPresent(pollToken, (key, entry) ->
                    new LinkPollEntry(entry.userId(), PollStatus.FAILED, entry.accountId(), null, code, message));
        }
    }

    public Optional<LinkPollEntry> pollLinkSession(String pollToken, Long userId) {
        if (pollToken == null || pollToken.isBlank()) return Optional.empty();
        LinkPollEntry entry = linkPollCache.getIfPresent(pollToken);
        if (entry == null) return Optional.empty();
        // Only the authenticated owner can consume a final result.
        if (!java.util.Objects.equals(entry.userId(), userId) || entry.status() == PollStatus.PENDING) {
            return Optional.of(entry);
        }
        return Optional.ofNullable(linkPollCache.asMap().remove(pollToken));
    }

    /**
     * Sinh cặp (state, pollToken) cho luồng Đăng nhập trực tiếp bằng Google OAuth2.
     */
    public record LoginStatePair(String state, String pollToken) {}

    public LoginStatePair generateGoogleLoginState(String codeVerifier) {
        String state = randomToken(24);
        String pollToken = randomToken(32); // 256-bit không thể đoán mò
        cache.put(state, new StateEntry(null, codeVerifier, Instant.now(), FlowType.GOOGLE_LOGIN, pollToken));
        loginPollCache.put(pollToken, new LoginPollEntry(PollStatus.PENDING, null, null, null));
        return new LoginStatePair(state, pollToken);
    }

    public void completeGoogleLoginSuccess(String pollToken, LoginResponse response) {
        if (pollToken != null && !pollToken.isBlank()) {
            loginPollCache.asMap().computeIfPresent(pollToken, (key, old) ->
                    old.status() == PollStatus.PENDING ? new LoginPollEntry(PollStatus.SUCCESS, response, null, null) : old);
        }
    }

    public void completeGoogleLoginFailure(String pollToken, String errorCode, String errorMessage) {
        if (pollToken != null && !pollToken.isBlank()) {
            loginPollCache.asMap().computeIfPresent(pollToken, (key, old) ->
                    old.status() == PollStatus.PENDING ? new LoginPollEntry(PollStatus.FAILED, null, errorCode, errorMessage) : old);
        }
    }

    /**
     * Đọc trạng thái phiên đăng nhập Google. Nếu đã SUCCESS hoặc FAILED thì tiêu thụ ngay (single-use).
     */
    public Optional<LoginPollEntry> pollLoginSession(String pollToken) {
        if (pollToken == null || pollToken.isBlank()) {
            return Optional.empty();
        }
        LoginPollEntry entry = loginPollCache.getIfPresent(pollToken);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.status() == PollStatus.SUCCESS || entry.status() == PollStatus.FAILED) {
            LoginPollEntry consumed = loginPollCache.asMap().remove(pollToken);
            return Optional.ofNullable(consumed);
        }
        return Optional.of(entry);
    }

    /**
     * Kiểm tra tính hợp lệ và tiêu thụ chuỗi state (chống tấn công replay CSRF).
     */
    public Optional<StateEntry> validateAndConsumeState(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        StateEntry entry = cache.asMap().remove(state);
        if (entry != null && entry.pollToken() != null) {
            boolean pending = entry.flowType() == FlowType.GOOGLE_LOGIN
                    ? loginPollCache.getIfPresent(entry.pollToken()) != null
                    : linkPollCache.getIfPresent(entry.pollToken()) != null;
            if (!pending) return Optional.empty();
        }
        return Optional.ofNullable(entry);
    }

    private static String randomToken(int byteLen) {
        byte[] bytes = new byte[byteLen];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
