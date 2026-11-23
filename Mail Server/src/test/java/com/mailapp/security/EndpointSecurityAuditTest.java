package com.mailapp.security;

import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử kiểm toán bảo mật có hệ thống:
 * Quét toàn bộ RequestMapping trong Spring Context và kiểm chứng rằng mọi endpoint /api/**
 * (bao gồm /api/dev/**) không nằm trong danh sách permitAll bắt buộc phải trả về 401 khi thiếu token,
 * và /api/dev/** chỉ cho phép ROLE_ADMIN truy cập, đồng thời DevDataInitializer tắt mặc định.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EndpointSecurityAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private static final Set<String> PERMIT_ALL_EXACT = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/oauth2/google/start",
            "/api/auth/oauth2/google/poll",
            "/api/oauth2/callback"
    );

    private boolean isPermitted(String path) {
        if (PERMIT_ALL_EXACT.contains(path)) {
            return true;
        }
        return path.startsWith("/api/public/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Test
    @DisplayName("Kiểm tra có hệ thống: Mọi endpoint /api/** không thuộc permitAll phải trả về 401 khi không có token")
    void allNonPermittedApiEndpointsRequireAuthentication() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        assertFalse(handlerMethods.isEmpty(), "Hệ thống phải có ít nhất một Controller được ánh xạ");

        int testedCount = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : (info.getPatternsCondition() != null ? info.getPatternsCondition().getPatterns() : Set.of());

            for (String pattern : patterns) {
                if (pattern.startsWith("/api/") && !isPermitted(pattern)) {
                    // Thay thế {id} bằng ID mẫu
                    String testPath = pattern.replaceAll("\\{[^}]+\\}", "1");

                    // Xác định HTTP method đăng ký
                    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                    MockHttpServletRequestBuilder requestBuilder;
                    if (methods.contains(RequestMethod.DELETE)) {
                        requestBuilder = delete(testPath);
                    } else if (methods.contains(RequestMethod.POST)) {
                        requestBuilder = post(testPath);
                    } else if (methods.contains(RequestMethod.PUT)) {
                        requestBuilder = put(testPath);
                    } else {
                        requestBuilder = get(testPath);
                    }

                    mockMvc.perform(requestBuilder)
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                            .andExpect(jsonPath("$.status").value(401));

                    testedCount++;
                }
            }
        }

        assertTrue(testedCount > 0, "Phải kiểm tra ít nhất 1 endpoint bảo mật (thực tế đã kiểm tra: " + testedCount + ")");
    }

    @Test
    @DisplayName("Bảo mật /api/dev/**: không token -> 401, USER thường -> 403, ADMIN -> 200; DevDataInitializer tắt mặc định")
    void devEndpointsRequireAdminAndDevSeedDisabledByDefault() throws Exception {
        // DevDataInitializer phải tắt mặc định khi không có MAILAPP_DEV_SEED=true
        assertTrue(userRepository.findByUsername("lethanhcuong").isEmpty(),
                "Không được tự động seed tài khoản mẫu lethanhcuong khi MAILAPP_DEV_SEED không bật");
        assertTrue(userRepository.findByUsername("locked_user").isEmpty(),
                "Không được tự động seed tài khoản mẫu locked_user khi MAILAPP_DEV_SEED không bật");

        // 1. Không token -> 401
        mockMvc.perform(post("/api/dev/maintenance-mode").param("enabled", "false"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/dev/setting").param("key", "ALLOW_OAUTH2_LINK").param("value", "true"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/dev/oauth2-selftest/1"))
                .andExpect(status().isUnauthorized());

        // Tạo 1 USER thường và 1 ADMIN để kiểm tra RBAC
        User normalUser = userRepository.findByUsername("audit_user").orElseGet(() -> {
            User u = new User();
            u.setUsername("audit_user");
            u.setPasswordHash(passwordEncoder.encode("UserPass123!"));
            u.setEmail("audit_user@example.com");
            u.setFullName("Audit User");
            u.setRole(Role.USER);
            u.setEnabled(true);
            u.setMustChangePassword(false);
            return userRepository.save(u);
        });
        String userJwt = jwtProvider.generateToken(normalUser);

        User adminUser = userRepository.findByUsername("audit_admin").orElseGet(() -> {
            User a = new User();
            a.setUsername("audit_admin");
            a.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
            a.setEmail("audit_admin@example.com");
            a.setFullName("Audit Admin");
            a.setRole(Role.ADMIN);
            a.setEnabled(true);
            a.setMustChangePassword(false);
            return userRepository.save(a);
        });
        String adminJwt = jwtProvider.generateToken(adminUser);

        // 2. USER thường -> 403 FORBIDDEN
        mockMvc.perform(post("/api/dev/maintenance-mode").param("enabled", "false")
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/dev/setting").param("key", "ALLOW_OAUTH2_LINK").param("value", "true")
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/dev/oauth2-selftest/1")
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isForbidden());

        // 3. ADMIN (ở profile dev) -> 200 OK
        mockMvc.perform(post("/api/dev/maintenance-mode").param("enabled", "false")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk());
    }
}

