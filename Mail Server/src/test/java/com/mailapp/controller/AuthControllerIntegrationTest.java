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
import com.mailapp.service.SystemSettingService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockBean
    private SystemSettingService systemSettingService;

    @Test
    void testMalformedJsonReturnsStructured400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    static Stream<Arguments> globalErrors() {
        return Stream.of(
                Arguments.of(new org.springframework.security.access.AccessDeniedException("denied"), 403, "FORBIDDEN"),
                Arguments.of(new java.util.NoSuchElementException("private internal detail"), 404, "NOT_FOUND"),
                Arguments.of(new jakarta.persistence.EntityNotFoundException("private internal detail"), 404, "NOT_FOUND"),
                Arguments.of(new org.springframework.web.multipart.MaxUploadSizeExceededException(1L), 413, "PAYLOAD_TOO_LARGE"),
                Arguments.of(new IllegalStateException("private internal detail"), 500, "INTERNAL_ERROR"));
    }

    @ParameterizedTest
    @MethodSource("globalErrors")
    void globalExceptionMappingsReturnStructuredSafeErrors(RuntimeException exception, int expectedStatus, String code) throws Exception {
        when(authService.login(any(LoginRequest.class), anyString())).thenThrow(exception);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("valid_user", "ValidPassword123"))))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.errorCode").value(code))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private internal detail"))));
    }

    @Test
    void wrongMethodAndContentTypeReturnStructuredErrors() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("wrong content type"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
        mockMvc.perform(get("/api/auth/oauth2/google/poll"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/auth/oauth2/google/poll"));
    }

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
    @DisplayName("POST /api/auth/login sai thông tin trả 401 Unauthorized và code UNAUTHORIZED")
    void testLoginInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sai tài khoản hoặc mật khẩu"));

        LoginRequest request = new LoginRequest("cuongle", "WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/auth/login tài khoản bị khóa trả 403 Forbidden và code ACCOUNT_DISABLED")
    void testLoginAccountLocked() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new AppException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."));

        LoginRequest request = new LoginRequest("locked_user", "CorrectPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
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
    @DisplayName("POST /api/auth/login khi hệ thống bảo trì trả 403 Forbidden")
    void testLoginMaintenanceMode() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new AppException(HttpStatus.FORBIDDEN, "MAINTENANCE_MODE", "Hệ thống đang bảo trì."));

        LoginRequest request = new LoginRequest("normal_user", "ValidPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
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

