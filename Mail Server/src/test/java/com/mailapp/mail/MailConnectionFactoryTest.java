package com.mailapp.mail;

import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.security.AesCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailConnectionFactoryTest {

    @Mock
    private AesCipher aesCipher;

    @Mock
    private AccessTokenProvider accessTokenProvider;

    private DefaultMailConnectionFactory factory;
    private MailConnectionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MailConnectionProperties();
        factory = new DefaultMailConnectionFactory(properties, aesCipher, accessTokenProvider, null);
    }

    @Test
    @DisplayName("openImap ném NullPointerException khi credentials hoặc account null")
    void testOpenImapNull() {
        assertThrows(NullPointerException.class, () -> factory.openImap((MailCredentials) null));
        assertThrows(NullPointerException.class, () -> factory.openImap((GmailAccount) null));
    }

    @Test
    @DisplayName("openImap với GmailAccount tự động giải mã App Password qua AesCipher")
    void testOpenImapWithGmailAccountDecryptsPassword() {
        String sampleCipher = "cipher-" + java.util.UUID.randomUUID();
        GmailAccount account = new GmailAccount();
        account.setEmail("test@gmail.com");
        account.setAuthType(AuthType.APP_PASSWORD);
        account.setAppPasswordEncrypted(sampleCipher);

        when(aesCipher.decrypt(sampleCipher)).thenReturn("");

        // Do mật khẩu rỗng nên MailCredentials.from ném IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> factory.openImap(account));
        verify(aesCipher).decrypt(sampleCipher);
    }

    @Test
    @DisplayName("openImap với GmailAccount OAuth2 gọi AccessTokenProvider")
    void testOpenImapWithGmailAccountOAuth2() {
        GmailAccount account = new GmailAccount();
        account.setEmail("test-oauth@gmail.com");
        account.setAuthType(AuthType.OAUTH2);
        account.setStatus(AccountStatus.ACTIVE);

        when(accessTokenProvider.getValidAccessToken(account)).thenReturn("");

        assertThrows(IllegalArgumentException.class, () -> factory.openImap(account));
        verify(accessTokenProvider).getValidAccessToken(account);
    }
}
