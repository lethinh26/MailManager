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
 * Claims gồm: sub (username), uid (stable user ID), username, role, jti, iat, exp.
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
            @Value("${mailapp.jwt.secret:${MAILAPP_JWT_SECRET:${JWT_SECRET:}}}") String secret,
            @Value("${mailapp.jwt.expiration-ms:86400000}") long expirationMs) {
        this(secret, expirationMs, null);
    }

    @Autowired
    public JwtProvider(
            @Value("${mailapp.jwt.secret:${MAILAPP_JWT_SECRET:${JWT_SECRET:}}}") String secret,
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
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("identityVersion", 2)
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
        var jwt = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
        if (!"HS256".equals(jwt.getHeader().getAlgorithm()) || jwt.getPayload().getExpiration() == null) {
            throw new IllegalArgumentException("Invalid JWT algorithm or expiration");
        }
        return jwt.getPayload();
    }

    /**
     * Kiểm tra token có hợp lệ và còn hạn sử dụng hay không.
     */
    public boolean validateToken(String token) {
        try {
            resolveUserId(parseClaims(token));
            return true;
        } catch (Exception e) {
            log.debug("JWT token không hợp lệ hoặc đã hết hạn: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public Long getUserId(String token) {
        return resolveUserId(parseClaims(token));
    }

    public Long resolveUserId(Claims claims) {
        Object version = claims.get("identityVersion");
        String subject = claims.getSubject();
        Object rawUid = claims.get("uid");
        Long uid = rawUid == null ? null : positiveId(rawUid);
        if (version == null) {
            // Previously issued tokens had the numeric user ID as subject.
            long legacyId = positiveId(subject);
            if (uid != null && uid != legacyId) {
                throw new IllegalArgumentException("Conflicting JWT user identifiers");
            }
            return legacyId;
        }
        if (!(version instanceof Integer || version instanceof Long)
                || ((Number) version).longValue() != 2L || uid == null
                || !(rawUid instanceof Integer || rawUid instanceof Long)
                || subject == null || subject.isBlank()
                || !subject.equals(claims.get("username"))) {
            throw new IllegalArgumentException("Invalid JWT identity claims");
        }
        return uid;
    }

    private static long positiveId(Object value) {
        if (!(value instanceof String || value instanceof Integer || value instanceof Long)
                || !value.toString().matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("Invalid JWT user ID");
        }
        return Long.parseLong(value.toString());
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
