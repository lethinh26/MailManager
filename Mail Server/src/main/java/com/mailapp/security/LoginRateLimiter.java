package com.mailapp.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mailapp.exception.RateLimitException;
import com.mailapp.service.SystemSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ kiểm soát tần suất đăng nhập (Rate Limiter) chống Brute-force.
 * Giới hạn theo cặp (username, IP), lưu trữ trong bộ nhớ đệm Caffeine.
 *
 * Mặc định: tối đa 5 lần sai / 15 phút.
 * Đây là cơ chế khóa tạm thời phía ứng dụng, KHÔNG chỉnh sửa users.enabled=false.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final boolean enabled;
    private final int maxAttempts;
    private final SystemSettingService systemSettingService;
    private final int windowMinutes;
    private final Cache<String, AttemptEntry> cache;

    public record AttemptEntry(AtomicInteger count, Instant firstAttemptTime) {}

    @Autowired
    public LoginRateLimiter(
            @Value("${mailapp.auth.rate-limit.enabled:true}") boolean enabled,
            @Value("${mailapp.auth.rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${mailapp.auth.rate-limit.window-minutes:15}") int windowMinutes,
            SystemSettingService systemSettingService) {
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.systemSettingService = systemSettingService;
        this.windowMinutes = windowMinutes;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(windowMinutes, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    public LoginRateLimiter(boolean enabled, int maxAttempts, int windowMinutes) {
        this(enabled, maxAttempts, windowMinutes, null);
    }

    public void checkLimit(String key) {
        checkLimit("client", key);
    }

    public void recordFailedAttempt(String key) {
        recordFailedAttempt("client", key);
    }

    public void recordSuccess(String key) {
        recordSuccess("client", key);
    }

    /**
     * Kiểm tra xem cặp (username, ip) đã vượt ngưỡng cho phép hay chưa.
     * Ném RateLimitException kèm thời gian chờ còn lại nếu vượt ngưỡng.
     */
    public void checkLimit(String username, String clientIp) {
        if (!enabled) {
            return;
        }

        String key = buildKey(username, clientIp);
        AttemptEntry entry = cache.getIfPresent(key);

        int configuredLimit = systemSettingService != null
                ? systemSettingService.getLoginMaxFailedAttempts() : maxAttempts;
        int effectiveLimit = configuredLimit > 0 ? configuredLimit : maxAttempts;
        if (entry != null && entry.count().get() >= effectiveLimit) {
            long elapsedSeconds = Duration.between(entry.firstAttemptTime(), Instant.now()).toSeconds();
            long windowSeconds = windowMinutes * 60L;
            long retryAfterSeconds = Math.max(1, windowSeconds - elapsedSeconds);

            log.warn("Chặn đăng nhập do brute-force key=[{}], số lần sai={}, thời gian chờ={}s",
                    key, entry.count().get(), retryAfterSeconds);

            throw new RateLimitException(
                    "Bạn đã nhập sai mật khẩu quá " + effectiveLimit + " lần. Vui lòng thử lại sau " + retryAfterSeconds + " giây.",
                    retryAfterSeconds
            );
        }
    }

    /**
     * Ghi nhận một lần đăng nhập thất bại.
     */
    public void recordFailedAttempt(String username, String clientIp) {
        if (!enabled) {
            return;
        }

        String key = buildKey(username, clientIp);
        cache.asMap().compute(key, (k, existing) -> {
            if (existing == null) {
                return new AttemptEntry(new AtomicInteger(1), Instant.now());
            } else {
                existing.count().incrementAndGet();
                return existing;
            }
        });
    }

    /**
     * Xóa bộ đếm khi đăng nhập thành công.
     */
    public void recordSuccess(String username, String clientIp) {
        if (!enabled) {
            return;
        }
        cache.invalidate(buildKey(username, clientIp));
    }

    private String buildKey(String username, String clientIp) {
        String cleanUser = (username != null) ? username.toLowerCase().trim() : "anonymous";
        String cleanIp = (clientIp != null) ? clientIp.trim() : "0.0.0.0";
        return cleanUser + ":" + cleanIp;
    }
}

