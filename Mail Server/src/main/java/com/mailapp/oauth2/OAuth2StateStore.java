package com.mailapp.oauth2;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mailapp.dto.LoginResponse;
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
    public record StateEntry(Long userId, String codeVerifier, Instant createdAt, FlowType flowType, String pollToken) {
        public StateEntry(Long userId, String codeVerifier, Instant createdAt) {
            this(userId, codeVerifier, createdAt, FlowType.LINK_GMAIL, null);
        }
    }

    public record LoginPollEntry(PollStatus status, LoginResponse loginResponse, String errorCode, String errorMessage) {}

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Cache state CSRF trong bộ nhớ với Caffeine, TTL 10 phút
    private final Cache<String, StateEntry> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    // Cache phiên chờ đăng nhập Google (key = pollToken 256-bit), TTL 5 phút, dùng 1 lần khi hoàn tất
    private final Cache<String, LoginPollEntry> loginPollCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();

    /**
     * Sinh state cho luồng Liên kết Gmail (user đã đăng nhập).
     */
    public String generateState(Long userId, String codeVerifier) {
        String state = randomToken(24);
        cache.put(state, new StateEntry(userId, codeVerifier, Instant.now(), FlowType.LINK_GMAIL, null));
        return state;
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
            loginPollCache.put(pollToken, new LoginPollEntry(PollStatus.SUCCESS, response, null, null));
        }
    }

    public void completeGoogleLoginFailure(String pollToken, String errorCode, String errorMessage) {
        if (pollToken != null && !pollToken.isBlank()) {
            loginPollCache.put(pollToken, new LoginPollEntry(PollStatus.FAILED, null, errorCode, errorMessage));
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
        return Optional.ofNullable(entry);
    }

    private static String randomToken(int byteLen) {
        byte[] bytes = new byte[byteLen];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
