package com.mailapp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Kiểm tra tính đầy đủ và hợp lệ của các biến môi trường bảo mật khi ứng dụng khởi động.
 * Trong profile non-dev (prod / default), nếu thiếu biến bắt buộc KEYSTORE_PASSWORD,
 * ứng dụng sẽ lập tức dừng khởi động và báo rõ tên biến bị thiếu.
 */
@Component
public class SecurityStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupValidator.class);

    public SecurityStartupValidator(Environment env) {
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");
        boolean isTest = Arrays.asList(env.getActiveProfiles()).contains("test");

        if (!isDev && !isTest) {
            String keystorePass = env.getProperty("KEYSTORE_PASSWORD");
            if (keystorePass == null || keystorePass.trim().isEmpty()) {
                keystorePass = env.getProperty("server.ssl.key-store-password");
            }
            if (keystorePass == null || keystorePass.trim().isEmpty()) {
                throw new IllegalStateException("Biến môi trường bắt buộc KEYSTORE_PASSWORD không được để trống.");
            }
        } else {
            log.info("Khởi động ở profile kiểm thử/phát triển (dev/test): Bỏ qua kiểm tra bắt buộc KEYSTORE_PASSWORD.");
        }
    }
}

