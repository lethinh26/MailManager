package com.mailapp.config;

import com.mailapp.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cấu hình tham số Google OAuth2 Client.
 * Giá trị được nạp từ biến môi trường GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI
 * hoặc application.properties.
 *
 * @author Lê Thanh Cường (Module M06)
 */
@Configuration
public class GoogleOAuthConfig {

    @Value("${google.oauth2.client-id:${GOOGLE_CLIENT_ID:}}")
    private String clientId;

    @Value("${google.oauth2.client-secret:${GOOGLE_CLIENT_SECRET:}}")
    private String clientSecret;

    @Value("${google.oauth2.redirect-uri:${GOOGLE_REDIRECT_URI:https://localhost:8443/api/oauth2/callback}}")
    private String redirectUri;

    @Value("${google.oauth2.token-url:https://oauth2.googleapis.com/token}")
    private String tokenUrl;

    @Value("${google.oauth2.userinfo-url:https://www.googleapis.com/oauth2/v2/userinfo}")
    private String userinfoUrl;

    @Value("${google.oauth2.revoke-url:https://oauth2.googleapis.com/revoke}")
    private String revokeUrl;

    @Value("${google.oauth2.auth-url:https://accounts.google.com/o/oauth2/v2/auth}")
    private String authUrl;

    /**
     * Xác thực cấu hình Google OAuth2 khi thực thi các tác vụ OAuth2.
     * Không làm dừng ứng dụng khi khởi động nếu chưa cấu hình.
     */
    public void validateConfig() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_CONFIG_MISSING",
                    "Máy chủ chưa được cấu hình Google Client ID hoặc Client Secret (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET).");
        }
    }

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public String getTokenUrl() { return tokenUrl; }
    public String getUserinfoUrl() { return userinfoUrl; }
    public String getRevokeUrl() { return revokeUrl; }
    public String getAuthUrl() { return authUrl; }

    @Bean
    public RestClient googleRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
