package com.mailapp.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Dịch vụ danh sách jti bị thu hồi (Denylist) để vô hiệu hóa JWT token ngay lập tức
 * (ví dụ: khi Admin khóa tài khoản user hoặc đổi mật khẩu).
 * Mặc định tắt (mailapp.auth.jwt.denylist-enabled=false).
 *
 * TODO: tích hợp với JwtFilter của Thịnh (M01)
 */
@Service
public class JtiDenylistService {

    private static final Logger log = LoggerFactory.getLogger(JtiDenylistService.class);

    private final boolean enabled;
    private final Cache<String, Boolean> denylistCache;

    public JtiDenylistService(@Value("${mailapp.auth.jwt.denylist-enabled:false}") boolean enabled) {
        this.enabled = enabled;
        this.denylistCache = Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(50_000)
                .build();
    }

    /**
     * Đưa một mã định danh token (jti) vào danh sách thu hồi.
     */
    public void revoke(String jti, Instant expiresAt) {
        if (!enabled || jti == null || jti.isBlank()) {
            return;
        }
        denylistCache.put(jti, Boolean.TRUE);
        log.info("Đã đưa token vào danh sách thu hồi");
    }

    /**
     * Kiểm tra xem JTI có nằm trong danh sách đã bị thu hồi hay không.
     */
    public boolean isRevoked(String jti) {
        if (!enabled || jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(denylistCache.getIfPresent(jti));
    }

    public boolean isEnabled() {
        return enabled;
    }
}

