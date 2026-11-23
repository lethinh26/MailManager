package com.mailapp.security;

import com.mailapp.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * Cung cấp tiện ích sinh, phân tích và kiểm tra tính hợp lệ của JWT Access Token.
 * Sử dụng thuật toán HS256 với khóa bí mật >= 256 bits từ biến môi trường JWT_SECRET.
 * Claims gồm: sub (userId), username, role, jti, iat, exp.
 *
 * Quy tắc bảo mật:
 * 1. Khóa bí mật không bao giờ dùng hằng số cố định.
 * 2. Ở profile non-dev (prod): Bắt buộc phải có JWT_SECRET >= 32 bytes, nếu thiếu sẽ dừng khởi động ngay.
 * 3. Ở profile dev: Nếu thiếu JWT_SECRET, hệ thống tự động sinh ngẫu nhiên cho phiên làm việc hiện tại và cảnh báo.
 *
 * TODO: thay bằng bản của Thịnh (M01)
 */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtProvider(
            @Value("${mailapp.jwt.secret:${JWT_SECRET:}}") String secret,
            @Value("${mailapp.jwt.expiration-ms:86400000}") long expirationMs) {
        this(secret, expirationMs, null);
    }

    @Autowired
    public JwtProvider(
            @Value("${mailapp.jwt.secret:${JWT_SECRET:}}") String secret,
            @Value("${mailapp.jwt.expiration-ms:86400000}") long expirationMs,
            @Nullable Environment env) {

        boolean isDev = env != null && Arrays.asList(env.getActiveProfiles()).contains("dev");

        if (secret == null || secret.trim().isEmpty()) {
            if (isDev) {
                byte[] rand = new byte[32];
                new SecureRandom().nextBytes(rand);
                log.warn("CẢNH BÁO [DEV PROFILE]: Biến môi trường JWT_SECRET chưa được cấu hình. Hệ thống đã tự động sinh khóa ngẫu nhiên tạm thời cho phiên làm việc này (token sẽ bị vô hiệu khi khởi động lại).");
                this.signingKey = Keys.hmacShaKeyFor(rand);
                this.expirationMs = expirationMs;
                return;
            } else {
                throw new IllegalStateException("Biến môi trường bắt buộc JWT_SECRET không được để trống.");
            }
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("Biến môi trường bắt buộc JWT_SECRET phải có độ dài tối thiểu 32 bytes (256 bits), hiện tại: " + keyBytes.length + " bytes.");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Sinh JWT Access Token cho User với thời hạn 24 giờ.
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMs);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Phân tích Claims từ token. Ném exception nếu token không hợp lệ hoặc hết hạn.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Kiểm tra token có hợp lệ và còn hạn sử dụng hay không.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT token không hợp lệ hoặc đã hết hạn: {}", e.getMessage());
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String getUsername(String token) {
        return parseClaims(token).get("username", String.class);
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000L;
    }
}
