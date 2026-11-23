package com.mailapp.mail;

import com.mailapp.exception.ReauthRequiredException;
import com.mailapp.model.GmailAccount;

/**
 * Interface cung cấp Access Token hợp lệ cho Gmail OAuth2.
 * Hợp đồng giao tiếp giữa Module M06 (Google OAuth2) và Module M07 (MailConnectionFactory).
 *
 * @author Lê Thanh Cường (Module M07)
 */
public interface AccessTokenProvider {

    /**
     * Lấy Access Token còn hiệu lực cho tài khoản Gmail.
     * Tự động kiểm tra thời hạn và làm mới (refresh) nếu token sắp hết hạn (< 60s).
     *
     * @param account tài khoản Gmail
     * @return access token còn hạn sử dụng
     * @throws ReauthRequiredException khi refresh token bị Google thu hồi hoặc hết hạn
     */
    String getValidAccessToken(GmailAccount account);
}

