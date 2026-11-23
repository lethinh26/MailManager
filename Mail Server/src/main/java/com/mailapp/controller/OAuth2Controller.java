package com.mailapp.controller;

import com.mailapp.dto.AuthorizeUrlResponse;
import com.mailapp.exception.AppException;
import com.mailapp.model.User;
import com.mailapp.oauth2.OAuth2Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý các luồng Google OAuth2 (EP-O1 và EP-O2).
 *
 * @author Lê Thanh Cường (Module M06)
 */
@RestController
@RequestMapping("/api/oauth2")
public class OAuth2Controller {

    private final OAuth2Service oauth2Service;

    public OAuth2Controller(OAuth2Service oauth2Service) {
        this.oauth2Service = oauth2Service;
    }

    /**
     * [EP-O1] Lấy URL uỷ quyền Google OAuth2 cho người dùng đang đăng nhập.
     * Yêu cầu xác thực Bearer JWT.
     */
    @GetMapping("/authorize-url")
    public ResponseEntity<AuthorizeUrlResponse> getAuthorizeUrl(
            @RequestParam(value = "accountId", required = false) Long accountId,
            @AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }
        AuthorizeUrlResponse response = (accountId != null)
                ? oauth2Service.getAuthorizeUrl(currentUser.getId(), accountId)
                : oauth2Service.getAuthorizeUrl(currentUser.getId());
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<AuthorizeUrlResponse> getAuthorizeUrl(User currentUser) {
        return getAuthorizeUrl(null, currentUser);
    }

    /**
     * [EP-O2] Endpoint callback do Google chuyển hướng về trình duyệt sau khi hoàn tất cấp quyền.
     * Công khai (permitAll), không nhận JWT. Bảo vệ bằng CSRF state và PKCE code_verifier.
     * Luôn trả về HTTP 200 kèm HTML thân thiện hiển thị trực tiếp cho người dùng.
     */
    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription) {

        String resultHtml = oauth2Service.handleCallback(code, state, error, errorDescription);
        return ResponseEntity.ok(resultHtml);
    }
}
