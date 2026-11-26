package com.mailapp.mail;

import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.oauth2.OAuth2Service;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Triển khai AccessTokenProvider kết nối trực tiếp với OAuth2Service thật (Module M06).
 *
 * @author Lê Thanh Cường (Module M07)
 */
@Component
@Primary
public class DefaultAccessTokenProvider implements AccessTokenProvider {

    private final OAuth2Service oauth2Service;

    public DefaultAccessTokenProvider(OAuth2Service oauth2Service) {
        this.oauth2Service = oauth2Service;
    }

    @Override
    public String getValidAccessToken(GmailAccount account) {
        Objects.requireNonNull(account, "GmailAccount không được null");
        if (!account.hasOAuth() && account.getAuthType() != AuthType.OAUTH2) {
            throw new IllegalArgumentException("Tài khoản chưa có phương thức xác thực OAuth2.");
        }
        return oauth2Service.getFreshAccessToken(account);
    }
}
