package com.mailapp.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class SecurityStartupValidatorTest {

    @Test
    @DisplayName("Non-dev profile thiếu KEYSTORE_PASSWORD: Báo lỗi và dừng khởi động")
    void testMissingKeystorePasswordInProdThrowsException() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new SecurityStartupValidator(prodEnv)
        );
        assertTrue(ex.getMessage().contains("KEYSTORE_PASSWORD"));
        assertTrue(ex.getMessage().contains("không được để trống"));
    }

    @Test
    @DisplayName("Non-dev profile có KEYSTORE_PASSWORD: Khởi động thành công")
    void testPresentKeystorePasswordInProdSucceeds() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        prodEnv.setProperty("KEYSTORE_PASSWORD", "SecretKeystorePass123!");

        assertDoesNotThrow(() -> new SecurityStartupValidator(prodEnv));
    }

    @Test
    @DisplayName("Dev profile thiếu KEYSTORE_PASSWORD: Bỏ qua và không ném lỗi")
    void testMissingKeystorePasswordInDevSucceeds() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");

        assertDoesNotThrow(() -> new SecurityStartupValidator(devEnv));
    }
}

