package com.mailapp.oauth2;

import com.mailapp.dto.AuthorizeUrlResponse;
import com.mailapp.dto.LoginResponse;
import com.mailapp.model.GmailAccount;

import java.util.Optional;

/**
 * Service xử lý toàn bộ nghiệp vụ Google OAuth2.
 * Bao gồm: tạo URL uỷ quyền kèm PKCE, đổi code lấy token, làm mới access token và thu hồi quyền.
 *
 * @author Lê Thanh Cường (Module M06)
 */
public interface OAuth2Service {

    /**
     * Sinh URL uỷ quyền Google cho người dùng hiện tại kèm mã CSRF state và PKCE code_challenge.
     * Kiểm tra cờ ALLOW_OAUTH2_LINK và giới hạn số lượng Gmail của user.
     *
     * @param userId ID người dùng đang đăng nhập
     * @return DTO chứa authorizeUrl
     */
    AuthorizeUrlResponse getAuthorizeUrl(Long userId);

    default AuthorizeUrlResponse getAuthorizeUrl(Long userId, Long existingAccountId) {
        return getAuthorizeUrl(userId);
    }

    /**
     * Xử lý callback do Google chuyển hướng về trình duyệt.
     * Đổi code lấy token kèm PKCE code_verifier, xác thực email, mã hóa và lưu Refresh Token vào DB.
     *
     * @param code authorization code nhận từ Google
     * @param state CSRF state token để xác thực user
     * @param error mã lỗi từ Google nếu user hủy/từ chối
     * @param errorDescription mô tả chi tiết lỗi từ Google
     * @return Chuỗi HTML thân thiện hiển thị trên trình duyệt người dùng
     */
    String handleCallback(String code, String state, String error, String errorDescription);

    default String handleCallback(String code, String state) {
        return handleCallback(code, state, null, null);
    }

    /**
     * Lấy Access Token hợp lệ mới từ Google bằng Refresh Token đã mã hóa trong DB.
     * Tự động cache trong bộ nhớ (Caffeine) và khóa theo từng tài khoản chống race condition.
     * Nếu token bị Google từ chối (invalid_grant), tự động cập nhật trạng thái NEEDS_REAUTH và ném ReauthRequiredException.
     *
     * @param account tài khoản Gmail OAuth2
     * @return chuỗi access token còn hạn
     */
    String getFreshAccessToken(GmailAccount account);

    /**
     * Thu hồi quyền (revoke token) với máy chủ Google khi người dùng xóa liên kết.
     *
     * @param account tài khoản Gmail cần thu hồi
     */
    void revokeToken(GmailAccount account);

    /**
     * Kết quả khởi tạo luồng Đăng nhập bằng Google OAuth2 từ màn hình Đăng nhập.
     */
    record GoogleLoginStartResponse(String authorizeUrl, String pollToken) {}

    /**
     * Kết quả kiểm tra trạng thái chờ đăng nhập Google OAuth2 (PENDING / SUCCESS / FAILED).
     */
    record GoogleLoginPollResponse(String status, LoginResponse loginResponse, String code, String message) {}

    default GoogleLoginStartResponse startGoogleLogin() {
        throw new UnsupportedOperationException("Google OAuth2 login chưa được cấu hình.");
    }

    default GoogleLoginPollResponse pollGoogleLogin(String pollToken) {
        throw new UnsupportedOperationException("Google OAuth2 login chưa được cấu hình.");
    }

    /**
     * Dữ liệu ảnh đại diện người dùng do Server proxy an toàn từ Google (chống SSRF).
     */
    record AvatarPayload(byte[] bytes, String contentType) {}

    /**
     * Tải ảnh đại diện của tài khoản Gmail OAuth2 qua Server (có cache RAM, giới hạn kích thước).
     */
    default Optional<AvatarPayload> fetchAccountAvatar(GmailAccount account) {
        return Optional.empty();
    }
}
