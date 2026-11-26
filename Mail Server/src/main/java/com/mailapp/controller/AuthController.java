package com.mailapp.controller;

import com.mailapp.dto.*;
import com.mailapp.exception.AppException;
import com.mailapp.exception.ErrorResponse;
import com.mailapp.model.User;
import com.mailapp.oauth2.OAuth2Service;
import com.mailapp.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý các yêu cầu xác thực và quản lý tài khoản người dùng:
 * - EP-A2: POST /api/auth/login (Đăng nhập, cấp JWT 24h)
 * - EP-A1: POST /api/auth/register (Đăng ký tài khoản mới)
 * - EP-A3: GET  /api/auth/me (Lấy thông tin tài khoản hiện tại)
 * - EP-A4: POST /api/auth/change-password (Đổi mật khẩu tài khoản)
 *
 * @author Lê Thanh Cường
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Các endpoint xác thực và quản lý tài khoản người dùng")
public class AuthController {

    private final AuthService authService;
    private final OAuth2Service oauth2Service;
    private final boolean trustForwardHeaders;

    @Autowired
    public AuthController(
            AuthService authService,
            @Autowired(required = false) OAuth2Service oauth2Service,
            @Value("${server.forward-headers-strategy:none}") String forwardHeadersStrategy) {
        this.authService = authService;
        this.oauth2Service = oauth2Service;
        this.trustForwardHeaders = "framework".equalsIgnoreCase(forwardHeadersStrategy)
                || "native".equalsIgnoreCase(forwardHeadersStrategy);
    }

    public AuthController(AuthService authService, String forwardHeadersStrategy) {
        this(authService, null, forwardHeadersStrategy);
    }

    /**
     * [EP-A2] Đăng nhập hệ thống và cấp phát JWT Access Token.
     */
    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Đăng nhập hệ thống (EP-A2)",
            description = "Xác thực tài khoản bằng username và password. Trả về JWT Access Token hạn 24h và thông tin người dùng. " +
                    "Hỗ trợ bảo vệ chống brute-force (giới hạn LOGIN_MAX_FAILED_ATTEMPTS / 15 phút) và kiểm tra chế độ bảo trì."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ (VALIDATION_ERROR)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sai tài khoản hoặc mật khẩu (UNAUTHORIZED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Tài khoản bị khóa (ACCOUNT_DISABLED) hoặc bảo trì (MAINTENANCE_MODE)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Vượt quá số lần thử đăng nhập (TOO_MANY_ATTEMPTS kèm header Retry-After)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = extractClientIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);

        // Header bảo mật phản hồi đăng nhập: không lưu cache token
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        headers.set(HttpHeaders.PRAGMA, "no-cache");

        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * [EP-A1] Đăng ký tài khoản người dùng mới (Công khai, có điều kiện).
     */
    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Đăng ký tài khoản mới (EP-A1)",
            description = "Đăng ký tài khoản người dùng mới. Bị chặn khi ALLOW_REGISTRATION=false (403) hoặc bảo trì (503). " +
                    "Tên đăng nhập trùng trả 409 USERNAME_TAKEN. Vai trò luôn mặc định là USER."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Đăng ký thành công",
                    content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc mật khẩu yếu",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Đăng ký bị khóa (REGISTRATION_DISABLED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tên đăng nhập đã tồn tại (USERNAME_TAKEN)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Hệ thống đang bảo trì (MAINTENANCE_MODE)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserDto> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = extractClientIp(httpRequest);
        UserDto createdUser = authService.register(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    /**
     * [EP-A3] Lấy thông tin tài khoản đang đăng nhập.
     */
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lấy thông tin tài khoản hiện tại (EP-A3)",
            description = "Trả về thông tin chi tiết của người dùng đang đăng nhập (id, username, fullName, email, role, mustChangePassword). Tuyệt đối không trả hash mật khẩu."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực (UNAUTHORIZED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }
        return ResponseEntity.ok(UserDto.from(currentUser));
    }

    /**
     * [EP-A4] Đổi mật khẩu tài khoản người dùng.
     */
    @PostMapping(value = "/change-password", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Đổi mật khẩu tài khoản (EP-A4)",
            description = "Đổi mật khẩu cho người dùng hiện tại, đặt mustChangePassword=false và cấp JWT Access Token mới."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công",
                    content = @Content(schema = @Schema(implementation = ChangePasswordResponse.class))),
            @ApiResponse(responseCode = "400", description = "Mật khẩu hiện tại sai hoặc mật khẩu mới yếu",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực người dùng",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Quá nhiều lần thử sai (Rate Limited)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChangePasswordResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest httpRequest) {

        String clientIp = extractClientIp(httpRequest);
        ChangePasswordResponse response = authService.changePassword(request, currentUser, clientIp);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        headers.set(HttpHeaders.PRAGMA, "no-cache");

        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * Khởi tạo phiên Đăng nhập trực tiếp bằng Google OAuth2 (PKCE S256).
     * Công khai (không cần JWT); dùng chung redirect URI https://localhost:8443/api/oauth2/callback.
     */
    @PostMapping(value = "/oauth2/google/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OAuth2Service.GoogleLoginStartResponse> startGoogleLogin() {
        if (oauth2Service == null) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH2_UNAVAILABLE", "Dịch vụ Google OAuth2 chưa sẵn sàng.");
        }
        return ResponseEntity.ok(oauth2Service.startGoogleLogin());
    }

    /**
     * Poll trạng thái hoàn tất đăng nhập Google OAuth2 bằng pollToken 256-bit (tiêu thụ 1 lần khi thành công).
     */
    @GetMapping(value = "/oauth2/google/poll", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OAuth2Service.GoogleLoginPollResponse> pollGoogleLogin(
            @RequestParam("pollToken") String pollToken) {
        if (oauth2Service == null) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH2_UNAVAILABLE", "Dịch vụ Google OAuth2 chưa sẵn sàng.");
        }
        OAuth2Service.GoogleLoginPollResponse response = oauth2Service.pollGoogleLogin(pollToken);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        HttpStatus status = "PENDING".equals(response.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).headers(headers).body(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (trustForwardHeaders) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
