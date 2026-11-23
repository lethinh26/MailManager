package com.mailapp.security;

import com.mailapp.model.Role;
import com.mailapp.model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử tính hợp lệ của khóa bí mật HMAC trong JwtProvider:
 * 1. Khóa thiếu (null hoặc rỗng): Báo lỗi và dừng khởi động (IllegalStateException).
 * 2. Khóa ngắn (< 32 bytes): Báo lỗi và dừng khởi động (IllegalStateException).
 * 3. Khóa đủ (>= 32 bytes): Khởi tạo thành công, dùng trực tiếp làm khóa HMAC HS256, sinh và đọc claims chính xác.
 *
 * @author Lê Thanh Cường
 */
class JwtProviderTest {

    private static final long EXPIRATION_MS = 86400000L; // 24h

    @Test
    @DisplayName("Trường hợp 1 (Thiếu secret): Báo lỗi và dừng khởi động khi secret null hoặc rỗng")
    void testSecretMissingThrowsIllegalStateException() {
        IllegalStateException exNull = assertThrows(IllegalStateException.class, () ->
                new JwtProvider(null, EXPIRATION_MS)
        );
        assertTrue(exNull.getMessage().contains("không được để trống"));

        IllegalStateException exEmpty = assertThrows(IllegalStateException.class, () ->
                new JwtProvider("", EXPIRATION_MS)
        );
        assertTrue(exEmpty.getMessage().contains("không được để trống"));

        IllegalStateException exBlank = assertThrows(IllegalStateException.class, () ->
                new JwtProvider("   ", EXPIRATION_MS)
        );
        assertTrue(exBlank.getMessage().contains("không được để trống"));
    }

    @Test
    @DisplayName("Trường hợp 2 (Secret quá ngắn): Báo lỗi và dừng khởi động khi secret < 32 bytes")
    void testSecretTooShortThrowsIllegalStateException() {
        String shortSecret16Bytes = "1234567890123456"; // 16 bytes
        assertEquals(16, shortSecret16Bytes.getBytes().length);

        IllegalStateException ex16 = assertThrows(IllegalStateException.class, () ->
                new JwtProvider(shortSecret16Bytes, EXPIRATION_MS)
        );
        assertTrue(ex16.getMessage().contains("tối thiểu 32 bytes"));

        String shortSecret31Bytes = "1234567890123456789012345678901"; // 31 bytes
        assertEquals(31, shortSecret31Bytes.getBytes().length);

        IllegalStateException ex31 = assertThrows(IllegalStateException.class, () ->
                new JwtProvider(shortSecret31Bytes, EXPIRATION_MS)
        );
        assertTrue(ex31.getMessage().contains("tối thiểu 32 bytes"));
    }

    @Test
    @DisplayName("Trường hợp 3 (Secret đủ >= 32 bytes): Dùng trực tiếp làm khóa HMAC, sinh và giải mã claims chính xác")
    void testSecretValidDirectHmacKey() {
        // Chuỗi khóa đúng 32 bytes
        String validSecret32Bytes = "my-valid-secret-key-minimum-32bs";
        assertEquals(32, validSecret32Bytes.getBytes().length);

        JwtProvider provider = new JwtProvider(validSecret32Bytes, EXPIRATION_MS);

        User user = new User(99L, "cuongle", "cuongle@ptit.edu.vn", Role.ADMIN);
        String token = provider.generateToken(user);

        assertNotNull(token);
        assertTrue(token.startsWith("eyJ"), "Token JWT phải bắt đầu bằng chuỗi Base64 Header chuẩn (eyJ)");
        assertTrue(provider.validateToken(token));

        Claims claims = provider.parseClaims(token);
        assertEquals("99", claims.getSubject());
        assertEquals("cuongle", claims.get("username", String.class));
        assertEquals("ADMIN", claims.get("role", String.class));
        assertNotNull(claims.getId()); // jti
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("Trường hợp 4 (Profile dev): Tự động sinh khóa ngẫu nhiên khi thiếu secret và hoạt động bình thường")
    void testDevProfileAutoGeneratesSecret() {
        org.springframework.mock.env.MockEnvironment devEnv = new org.springframework.mock.env.MockEnvironment();
        devEnv.setActiveProfiles("dev");

        JwtProvider devProvider = new JwtProvider(null, EXPIRATION_MS, devEnv);
        User user = new User(100L, "devuser", "dev@mailapp.local", Role.USER);
        String token = devProvider.generateToken(user);

        assertNotNull(token);
        assertTrue(devProvider.validateToken(token));
        assertEquals(100L, devProvider.getUserId(token));
    }

    @Test
    @DisplayName("Trường hợp 5 (Thuật toán ký): Header JWT luôn là HS256 có chủ đích ngay cả với khóa 64 bytes")
    void testJwtHeaderAlwaysUsesHS256EvenWithLongKey() {
        // Khóa dài 64 bytes (nếu không ép Jwts.SIG.HS256 thì JJWT sẽ tự chọn HS512/HS384)
        String longSecret64Bytes = "1234567890123456789012345678901234567890123456789012345678901234";
        assertEquals(64, longSecret64Bytes.getBytes().length);

        JwtProvider provider = new JwtProvider(longSecret64Bytes, EXPIRATION_MS);
        User user = new User(101L, "algcheck", "alg@mailapp.local", Role.USER);
        String token = provider.generateToken(user);

        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(headerJson.contains("\"alg\":\"HS256\""),
                "Thuật toán ký trong JWT header phải là HS256, thực tế: " + headerJson);
        assertTrue(provider.validateToken(token));
    }
}
