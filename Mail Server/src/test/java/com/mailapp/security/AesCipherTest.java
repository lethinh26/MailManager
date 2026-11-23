package com.mailapp.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử tính hợp lệ và cơ chế bảo mật của AesCipher:
 * 1. Khóa hợp lệ (Base64 đúng 32 bytes): Mã hóa và giải mã thành công.
 * 2. Khóa thiếu (null hoặc rỗng) ngoài dev: Báo lỗi và dừng khởi động (IllegalStateException).
 * 3. Khóa không phải Base64: Báo lỗi (IllegalStateException).
 * 4. Khóa Base64 có độ dài sau giải mã khác 32 bytes: Báo lỗi (IllegalStateException).
 * 5. Khóa thiếu trong profile dev: Tự động sinh ngẫu nhiên và hoạt động bình thường.
 */
class AesCipherTest {

    // 32 bytes Base64 hợp lệ (256 bits)
    private static final String VALID_BASE64_32_BYTES =
            Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));

    private final AesCipher aesCipher = new AesCipher(VALID_BASE64_32_BYTES);

    @Test
    @DisplayName("Khóa hợp lệ: Mã hóa và giải mã chuỗi thành công, bảo toàn nội dung gốc")
    void testEncryptDecryptSuccess() {
        String original = "app-password-16-chars-abcd";
        String encrypted = aesCipher.encrypt(original);

        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = aesCipher.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("Khóa thiếu ngoài dev: Ném IllegalStateException nêu rõ biến AES_KEY")
    void testMissingKeyThrowsIllegalStateException() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        IllegalStateException exNull = assertThrows(IllegalStateException.class, () ->
                new AesCipher(null, prodEnv)
        );
        assertTrue(exNull.getMessage().contains("AES_KEY"));
        assertTrue(exNull.getMessage().contains("không được để trống"));

        IllegalStateException exEmpty = assertThrows(IllegalStateException.class, () ->
                new AesCipher("", prodEnv)
        );
        assertTrue(exEmpty.getMessage().contains("AES_KEY"));
    }

    @Test
    @DisplayName("Khóa không phải Base64: Ném IllegalStateException nêu rõ biến AES_KEY")
    void testInvalidBase64ThrowsIllegalStateException() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new AesCipher("not-a-valid-base64-string!!@@##", prodEnv)
        );
        assertTrue(ex.getMessage().contains("AES_KEY"));
    }

    @Test
    @DisplayName("Khóa Base64 sai độ dài (không đủ 32 bytes): Ném IllegalStateException")
    void testWrongLengthBase64ThrowsIllegalStateException() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        // 16 bytes Base64 (128 bits) -> không đủ 32 bytes cho AES-256
        String base64_16_bytes = Base64.getEncoder().encodeToString("1234567890123456".getBytes(StandardCharsets.UTF_8));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new AesCipher(base64_16_bytes, prodEnv)
        );
        assertTrue(ex.getMessage().contains("AES_KEY"));
        assertTrue(ex.getMessage().contains("32 bytes"));
    }

    @Test
    @DisplayName("Khóa thiếu trong profile dev: Tự sinh ngẫu nhiên và mã hóa/giải mã bình thường")
    void testDevProfileAutoGeneratesKey() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");

        AesCipher devCipher = new AesCipher(null, devEnv);
        String secret = "test-token-value-xyz";
        String enc = devCipher.encrypt(secret);
        assertNotNull(enc);
        assertEquals(secret, devCipher.decrypt(enc));
    }

    @Test
    @DisplayName("Xử lý chuỗi null hoặc rỗng một cách an toàn")
    void testNullOrEmpty() {
        assertNull(aesCipher.encrypt(null));
        assertEquals("", aesCipher.encrypt(""));
        assertNull(aesCipher.decrypt(null));
        assertEquals("", aesCipher.decrypt(""));
    }

    @Test
    @DisplayName("Ném exception khi chuỗi mã hóa bị hỏng hoặc không đúng định dạng Base64/IV")
    void testCorruptedData() {
        assertThrows(RuntimeException.class, () -> aesCipher.decrypt("corrupted-not-valid-base64"));
        assertThrows(RuntimeException.class, () -> aesCipher.decrypt("YWJj")); // "abc" quá ngắn cho IV 12 bytes
    }
}
