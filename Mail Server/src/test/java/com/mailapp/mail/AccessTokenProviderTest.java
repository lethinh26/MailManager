package com.mailapp.mail;

import com.mailapp.exception.ReauthRequiredException;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.oauth2.OAuth2Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessTokenProviderTest {

    @Mock
    private OAuth2Service oauth2Service;

    private DefaultAccessTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultAccessTokenProvider(oauth2Service);
    }

    @Test
    @DisplayName("Lấy valid access token thành công từ OAuth2Service")
    void testGetValidAccessTokenSuccess() {
        GmailAccount account = new GmailAccount();
        account.setEmail("oauth@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(30));

        when(oauth2Service.getFreshAccessToken(account)).thenReturn("new-refreshed-access-token");

        String token = provider.getValidAccessToken(account);

        assertEquals("new-refreshed-access-token", token);
        verify(oauth2Service).getFreshAccessToken(account);
    }

    @Test
    @DisplayName("Ném ReauthRequiredException khi OAuth2Service báo lỗi hết hạn hoặc bị thu hồi")
    void testRefreshFailedThrowsReauthRequired() {
        GmailAccount account = new GmailAccount();
        account.setEmail("revoked@gmail.com");
        account.setAuthType(AuthType.OAUTH2);

        when(oauth2Service.getFreshAccessToken(account)).thenThrow(new ReauthRequiredException("Token revoked"));

        assertThrows(ReauthRequiredException.class, () ->
            provider.getValidAccessToken(account)
        );
    }
}
