package com.mailapp.security;

import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng rằng ngoài môi trường DEV (ở đây là profile prod),
 * toàn bộ các thành phần và endpoint tiện ích của dev (DevController, DevDataInitializer, Swagger UI, API Docs)
 * đều bị khóa chặt: không token -> 401, ADMIN gọi /api/dev/** -> 404 (bean không tồn tại), không seed user mẫu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:prod_test_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update",
        "mailapp.jwt.secret=ProductionTestSecretKeyMustBeAtLeast32BytesLong!",
        "mailapp.aes.key=dCPN0qIeMrPFVyn/9c05Pq1O65Re+wsVplG7fEFU4dU=",
        "server.ssl.key-store-password=testKeystorePassword123!",
        "KEYSTORE_PASSWORD=testKeystorePassword123!",
        "ADMIN_INITIAL_PASSWORD=AdminInitialPassword123!",
        "mailapp.admin.initial-password=AdminInitialPassword123!"
})
class NonDevProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Profile prod: /api/dev/** không token -> 401; ADMIN gọi -> 404 (DevController không tồn tại) và không seed mẫu")
    void devControllerIsDisabledInProd() throws Exception {
        assertTrue(userRepository.findByUsername("lethanhcuong").isEmpty());

        // Chưa đăng nhập -> bị SecurityConfig chặn 401 ngay ở lớp filter
        mockMvc.perform(post("/api/dev/maintenance-mode").param("enabled", "true"))
                .andExpect(status().isUnauthorized());

        // Có JWT ADMIN -> qua được SecurityConfig nhưng DevController không tồn tại nên trả 404
        User admin = userRepository.findByUsername("prod_admin_check").orElseGet(() -> {
            User u = new User();
            u.setUsername("prod_admin_check");
            u.setPasswordHash(passwordEncoder.encode("Adm#" + java.util.UUID.randomUUID()));
            u.setEmail("prod_admin@example.com");
            u.setFullName("Prod Admin");
            u.setRole(Role.ADMIN);
            u.setEnabled(true);
            u.setMustChangePassword(false);
            return userRepository.save(u);
        });
        String adminJwt = jwtProvider.generateToken(admin);

        mockMvc.perform(post("/api/dev/maintenance-mode").param("enabled", "true")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Profile prod: Swagger UI (/swagger-ui.html) không tồn tại và trả về 404")
    void swaggerUiIsDisabledInProd() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Profile prod: OpenAPI Docs (/v3/api-docs) không tồn tại và trả về 404")
    void openApiDocsIsDisabledInProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}

