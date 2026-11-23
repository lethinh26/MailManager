package com.mailapp.config;

import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Khởi tạo dữ liệu người dùng mẫu CHỈ khi chạy ở profile dev VÀ bật tường minh biến MAILAPP_DEV_SEED=true
 * (mặc định TẮT để không tạo tài khoản mật khẩu công khai trong môi trường demo/LAN/prod).
 *
 * @author Lê Thanh Cường
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "mailapp.dev.seed", havingValue = "true", matchIfMissing = false)
public class DevDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("==> [DEV PROFILE] Khởi tạo dữ liệu người dùng mẫu cho môi trường dev...");

        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("AdminPassword123!"));
            admin.setFullName("Quản trị viên Hệ thống");
            admin.setEmail("admin@ptit.edu.vn");
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            admin.setMustChangePassword(false);
            userRepository.save(admin);
            log.info("==> Đã tạo user mẫu: admin");
        }

        if (userRepository.findByUsername("lethanhcuong").isEmpty()) {
            User user = new User();
            user.setUsername("lethanhcuong");
            user.setPasswordHash(passwordEncoder.encode("UserPassword123!"));
            user.setFullName("Lê Thanh Cường");
            user.setEmail("cuongle@ptit.edu.vn");
            user.setRole(Role.USER);
            user.setEnabled(true);
            user.setMustChangePassword(false);
            userRepository.save(user);
            log.info("==> Đã tạo user mẫu: lethanhcuong");
        }

        if (userRepository.findByUsername("locked_user").isEmpty()) {
            User lockedUser = new User();
            lockedUser.setUsername("locked_user");
            lockedUser.setPasswordHash(passwordEncoder.encode("LockedUserPass123!"));
            lockedUser.setFullName("Tài khoản Bị Khóa");
            lockedUser.setEmail("locked@ptit.edu.vn");
            lockedUser.setRole(Role.USER);
            lockedUser.setEnabled(false); // Bị khóa
            lockedUser.setMustChangePassword(false);
            userRepository.save(lockedUser);
            log.info("==> Đã tạo user mẫu: locked_user");
        }
    }
}
