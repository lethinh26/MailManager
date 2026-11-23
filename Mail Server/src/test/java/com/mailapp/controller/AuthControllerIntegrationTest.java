package com.mailapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailapp.dto.LoginRequest;
import com.mailapp.dto.LoginResponse;
import com.mailapp.dto.UserDto;
import com.mailapp.exception.AppException;
import com.mailapp.exception.GlobalExceptionHandler;
import com.mailapp.exception.RateLimitException;
import com.mailapp.model.Role;
import com.mailapp.repository.UserRepository;
import com.mailapp.security.JtiDenylistService;
import com.mailapp.security.JwtAuthenticationFilter;
import com.mailapp.security.JwtProvider;
import com.mailapp.security.SecurityConfig;
import com.mailapp.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JtiDenylistService denylistService;

    @Test
    @DisplayName("POST /api/auth/login thành công trả 200 OK kèm JWT và Cache-Control: no-store")
    void testLoginSuccess() throws Exception {
        UserDto userDto = new UserDto(1L, "admin", "Admin System", "admin@mailapp.com", Role.ADMIN, false);
        LoginResponse response = LoginResponse.of("mock-token-xyz", 86400L, userDto);

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(response);

        LoginRequest request = new LoginRequest("admin", "AdminPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.accessToken").value("mock-token-xyz"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("POST /api/auth/login dữ liệu sai ràng buộc trả 400 Bad Request và code VALIDATION_ERROR")
    void testLoginValidationError() throws Exception {
        LoginRequest invalidRequest = new LoginRequest("a", ""); // username < 3 chars, password rỗng

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/login sai thông tin trả 401 Unauthorized và code INVALID_CREDENTIALS")
    void testLoginInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Sai tên đăng nhập hoặc mật khẩu."));

        LoginRequest request = new LoginRequest("cuongle", "WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/auth/login tài khoản bị khóa trả 403 Forbidden và code ACCOUNT_LOCKED")
    void testLoginAccountLocked() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new AppException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."));

        LoginRequest request = new LoginRequest("locked_user", "CorrectPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("POST /api/auth/login bị rate limit trả 429 Too Many Requests kèm header Retry-After")
    void testLoginRateLimited() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new RateLimitException("Vượt quá số lần thử đăng nhập.", 600L));

        LoginRequest request = new LoginRequest("cuongle", "any");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    @DisplayName("POST /api/auth/login khi hệ thống bảo trì trả 503 Service Unavailable")
    void testLoginMaintenanceMode() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new AppException(HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE_MODE", "Hệ thống đang bảo trì."));

        LoginRequest request = new LoginRequest("normal_user", "ValidPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MAINTENANCE_MODE"));
    }

    @Test
    @DisplayName("Header X-Forwarded-For giả bị bỏ qua khi server.forward-headers-strategy mặc định tắt")
    void testFakeXForwardedForIgnoredWhenForwardHeadersDisabled() throws Exception {
        UserDto userDto = new UserDto(1L, "user1", "User One", "user1@mailapp.com", Role.USER, false);
        LoginResponse response = LoginResponse.of("mock-token", 86400L, userDto);

        // Khi forward headers strategy mặc định tắt (none), IP truyền vào authService PHẢI là remoteAddr (127.0.0.1),
        // KHÔNG PHẢI là IP giả trong X-Forwarded-For
        when(authService.login(any(LoginRequest.class), org.mockito.ArgumentMatchers.eq("127.0.0.1"))).thenReturn(response);

        LoginRequest request = new LoginRequest("user1", "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.195")
                        .header("X-Real-IP", "198.51.100.22")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(authService).login(any(LoginRequest.class), org.mockito.ArgumentMatchers.eq("127.0.0.1"));
    }
}

