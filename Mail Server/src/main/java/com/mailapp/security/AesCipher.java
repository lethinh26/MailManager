package com.mailapp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Mã hóa / giải mã đối xứng AES-256-GCM cho thông tin nhạy cảm (App Password, Refresh Token).
 * Khóa bí mật lấy từ biến môi trường AES_KEY.
 *
 * Quy tắc an toàn:
 * 1. Khóa AES_KEY bắt buộc phải là chuỗi Base64 giải mã ra đúng 32 bytes (256 bits).
 * 2. Ở profile non-dev: Nếu thiếu hoặc sai định dạng, app dừng khởi động ngay và thông báo tên biến.
 * 3. Ở profile dev: Nếu thiếu, tự sinh ngẫu nhiên cho phiên hiện tại và ghi log cảnh báo (không dùng hằng số).
 * 4. Tuyệt đối không log khóa hay nội dung bí mật.
 *
 * TODO: thay bằng bản của Thịnh (M01)
 */
@Component
public class AesCipher {

    private static final Logger log = LoggerFactory.getLogger(AesCipher.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesCipher(@Value("${mailapp.aes.key:${MAILAPP_AES_KEY:${AES_KEY:}}}") String rawKey) {
        this(rawKey, null);
    }

    @Autowired
    public AesCipher(
            @Value("${mailapp.aes.key:${MAILAPP_AES_KEY:${AES_KEY:}}}") String rawKey,
            @Nullable Environment env) {
        this.secretKey = deriveKey(rawKey, env);
    }

    private SecretKey deriveKey(String rawKey, @Nullable Environment env) {
        boolean isDev = env != null && Arrays.asList(env.getActiveProfiles()).contains("dev");

        if (rawKey == null || rawKey.trim().isEmpty()) {
            if (isDev) {
                byte[] rand = new byte[32];
                secureRandom.nextBytes(rand);
                log.warn("CẢNH BÁO [DEV PROFILE]: Biến môi trường AES_KEY chưa được cấu hình. Hệ thống đã tự động sinh khóa ngẫu nhiên tạm thời cho phiên làm việc này.");
                return new SecretKeySpec(rand, "AES");
            } else {
                throw new IllegalStateException("Biến môi trường bắt buộc AES_KEY không được để trống.");
            }
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(rawKey.trim());
            if (decoded.length != 32) {
                throw new IllegalStateException("Biến môi trường bắt buộc AES_KEY phải là chuỗi Base64 giải mã ra đúng 32 bytes (256 bits), hiện tại: " + decoded.length + " bytes.");
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Biến môi trường bắt buộc AES_KEY phải là chuỗi Base64 giải mã ra đúng 32 bytes (256 bits).", e);
        }
    }

    /**
     * Mã hóa chuỗi văn bản thuần sang chuỗi Base64 (chứa IV 12 bytes + ciphertext + tag).
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi mã hóa dữ liệu với AES-GCM", e);
        }
    }

    /**
     * Giải mã chuỗi Base64 về chuỗi văn bản thuần.
     */
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) {
            return encryptedBase64;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            if (combined.length < IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / Byte.SIZE) {
                throw new IllegalArgumentException("Dữ liệu mã hóa không hợp lệ");
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);

            int cipherTextLength = combined.length - IV_LENGTH_BYTES;
            byte[] cipherText = new byte[cipherTextLength];
            System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherTextLength);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi giải mã dữ liệu với AES-GCM", e);
        }
    }
}
