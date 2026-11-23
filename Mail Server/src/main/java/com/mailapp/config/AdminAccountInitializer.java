package com.mailapp.config;

import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Đảm bảo tài khoản ADMIN khởi tạo có mật khẩu lấy từ biến môi trường ADMIN_INITIAL_PASSWORD.
 * Bắt buộc đặt must_change_password=true khi tạo mới.
 * CHỈ tạo tài khoản admin khi CHƯA có user nào có role ADMIN.
 * Không bao giờ ghi đè mật khẩu hay must_change_password của admin đã tồn tại ở lần khởi động sau.
 */
@Component
@Order(1)
public class AdminAccountInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    @Value("${ADMIN_INITIAL_PASSWORD:${mailapp.admin.initial-password:}}")
    private String adminInitialPassword;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminAccountInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        // Kiểm tra xem đã có bất kỳ tài khoản ADMIN nào tồn tại trong hệ thống chưa
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Hệ thống đã tồn tại tài khoản có quyền ADMIN. Bỏ qua khởi tạo tài khoản admin ban đầu.");
            return;
        }

        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        boolean isTest = Arrays.asList(environment.getActiveProfiles()).contains("test");

        String initialPass = adminInitialPassword;
        if (initialPass == null || initialPass.isBlank()) {
            throw new IllegalStateException("Biến môi trường bắt buộc ADMIN_INITIAL_PASSWORD không được để trống khi khởi tạo hệ thống.");
        } else if (!isDev && !isTest) {
            // Với non-dev, kiểm tra độ mạnh mật khẩu (ít nhất 8 ký tự, có cả chữ và số)
            if (initialPass.length() < 8 || !initialPass.matches(".*[a-zA-Z].*") || !initialPass.matches(".*[0-9].*")) {
                throw new IllegalStateException("Biến môi trường ADMIN_INITIAL_PASSWORD quá yếu (yêu cầu tối thiểu 8 ký tự gồm cả chữ và số).");
            }
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(initialPass));
        admin.setFullName("System Administrator");
        admin.setEmail("admin@mailapp.local");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        admin.setMustChangePassword(true);
        userRepository.save(admin);
        log.info("Đã khởi tạo tài khoản ADMIN ban đầu duy nhất (must_change_password=true).");
    }
}
