package com.mailapp.config;

import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Kiểm thử AdminAccountInitializer (Task 2 - Khởi tạo tài khoản ADMIN an toàn)")
class AdminAccountInitializerTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("Khởi động 2 lần: Đổi mật khẩu giữa 2 lần, mật khẩu mới phải còn nguyên và không bị ghi đè")
    void testAdminPreservedAcrossRestarts() {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        Environment environment = Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        AdminAccountInitializer initializer = new AdminAccountInitializer(userRepository, passwordEncoder, environment);
        ReflectionTestUtils.setField(initializer, "adminInitialPassword", "InitialAdminPass123");

        // LẦN KHỞI ĐỘNG 1: Chưa có tài khoản ADMIN nào
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        User[] createdUserHolder = new User[1];
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            createdUserHolder[0] = u;
            return u;
        });

        initializer.run();

        // Xác nhận lần 1: User ADMIN được tạo với mật khẩu ban đầu và mustChangePassword=true
        assertNotNull(createdUserHolder[0]);
        assertEquals("admin", createdUserHolder[0].getUsername());
        assertEquals(Role.ADMIN, createdUserHolder[0].getRole());
        assertTrue(createdUserHolder[0].isMustChangePassword());
        assertTrue(passwordEncoder.matches("InitialAdminPass123", createdUserHolder[0].getPasswordHash()));

        // GIỮA 2 LẦN: Người dùng đổi mật khẩu mới và hoàn tất đổi mật khẩu
        User existingAdmin = createdUserHolder[0];
        existingAdmin.setPasswordHash(passwordEncoder.encode("MyNewSecretPassword2026!"));
        existingAdmin.setMustChangePassword(false);

        // LẦN KHỞI ĐỘNG 2: Hệ thống đã có tài khoản ADMIN
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingAdmin));

        // Chạy lại initializer
        initializer.run();

        // Xác minh: save() không được gọi lại ở lần 2 (không ghi đè)
        verify(userRepository, times(1)).save(any(User.class));

        // Mật khẩu mới vẫn còn nguyên, mustChangePassword vẫn là false
        assertTrue(passwordEncoder.matches("MyNewSecretPassword2026!", existingAdmin.getPasswordHash()),
                "Mật khẩu mới của admin phải còn nguyên vẹn sau khi server khởi động lại!");
        assertFalse(passwordEncoder.matches("InitialAdminPass123", existingAdmin.getPasswordHash()),
                "Mật khẩu ban đầu không được xuất hiện lại!");
        assertFalse(existingAdmin.isMustChangePassword(),
                "Cờ mustChangePassword không được bị reset về true khi khởi động lại!");
    }

    @Test
    @DisplayName("Non-dev: Dừng khởi động nếu thiếu ADMIN_INITIAL_PASSWORD khi cần tạo admin")
    void testNonDevThrowsWhenMissingPassword() {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        Environment environment = Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        AdminAccountInitializer initializer = new AdminAccountInitializer(userRepository, passwordEncoder, environment);
        ReflectionTestUtils.setField(initializer, "adminInitialPassword", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, initializer::run);
        assertTrue(ex.getMessage().contains("ADMIN_INITIAL_PASSWORD"));
    }

    @Test
    @DisplayName("Non-dev: Dừng khởi động nếu ADMIN_INITIAL_PASSWORD quá yếu")
    void testNonDevThrowsWhenWeakPassword() {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        Environment environment = Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        AdminAccountInitializer initializer = new AdminAccountInitializer(userRepository, passwordEncoder, environment);
        ReflectionTestUtils.setField(initializer, "adminInitialPassword", "weak1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, initializer::run);
        assertTrue(ex.getMessage().contains("quá yếu"));
    }
}

