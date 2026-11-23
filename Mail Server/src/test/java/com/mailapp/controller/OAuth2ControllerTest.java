package com.mailapp.controller;

import com.mailapp.dto.AuthorizeUrlResponse;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.oauth2.OAuth2Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ControllerTest {

    @Mock
    private OAuth2Service oauth2Service;

    private OAuth2Controller controller;

    @BeforeEach
    void setUp() {
        controller = new OAuth2Controller(oauth2Service);
    }

    @Test
    @DisplayName("GET /api/oauth2/authorize-url trả về 200 OK kèm URL khi có người dùng đăng nhập")
    void testGetAuthorizeUrl() {
        User user = new User(10L, "testuser", "test@example.com", Role.USER);
        String mockUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=xyz";
        when(oauth2Service.getAuthorizeUrl(10L)).thenReturn(new AuthorizeUrlResponse(mockUrl));

        ResponseEntity<AuthorizeUrlResponse> response = controller.getAuthorizeUrl(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockUrl, response.getBody().authorizeUrl());
    }

    @Test
    @DisplayName("GET /api/oauth2/callback trả về 200 OK với HTML khi có code và state")
    void testHandleCallbackSuccess() {
        String code = "sample-code";
        String state = "sample-state";
        String htmlContent = "<html><body>Liên kết thành công</body></html>";

        when(oauth2Service.handleCallback(code, state, null, null)).thenReturn(htmlContent);

        ResponseEntity<String> response = controller.handleCallback(code, state, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(htmlContent, response.getBody());
    }

    @Test
    @DisplayName("GET /api/oauth2/callback trả về 200 OK hiển thị HTML lỗi thân thiện khi Google trả error")
    void testHandleCallbackGoogleError() {
        String errorHtml = "<html><body>Bạn đã hủy hoặc từ chối cấp quyền truy cập tài khoản Google (access_denied).</body></html>";
        when(oauth2Service.handleCallback(null, null, "access_denied", null)).thenReturn(errorHtml);

        ResponseEntity<String> response = controller.handleCallback(null, null, "access_denied", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("access_denied"));
    }
}
