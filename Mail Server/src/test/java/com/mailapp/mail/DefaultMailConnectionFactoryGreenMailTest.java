package com.mailapp.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.mailapp.exception.MailAuthException;
import com.mailapp.mail.config.MailConnectionProperties;
import com.mailapp.mail.model.ConnectionTestResult;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AuthType;
import com.mailapp.security.AesCipher;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultMailConnectionFactoryGreenMailTest {

    private static GreenMail greenMail;
    private static final int IMAPS_PORT = 3993;
    private static final int SMTP_PORT = 3587;

    private DefaultMailConnectionFactory factory;
    private MailConnectionProperties properties;
    private AesCipher aesCipher;
    private AccessTokenProvider accessTokenProvider;

    @BeforeAll
    static void startGreenMail() {
        ServerSetup imapsSetup = new ServerSetup(IMAPS_PORT, "127.0.0.1", ServerSetup.PROTOCOL_IMAPS);
        ServerSetup smtpSetup = new ServerSetup(SMTP_PORT, "127.0.0.1", ServerSetup.PROTOCOL_SMTP);

        greenMail = new GreenMail(new ServerSetup[]{imapsSetup, smtpSetup});
        greenMail.start();
        greenMail.setUser("testuser@gmail.com", "validAppPassword16");
    }

    @AfterAll
    static void stopGreenMail() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @BeforeEach
    void setUp() {
        properties = new MailConnectionProperties();
        properties.setImapHost("127.0.0.1");
        properties.setImapPort(IMAPS_PORT);
        properties.setSmtpHost("127.0.0.1");
        properties.setSmtpPort(SMTP_PORT);
        properties.setConnectionTimeoutMs(3000);
        properties.setTimeoutMs(3000);
        properties.setWriteTimeoutMs(3000);
        properties.setRetryEnabled(true);
        properties.setRetryMaxAttempts(2);
        properties.setRetryWaitDurationMs(100);

        aesCipher = mock(AesCipher.class);
        accessTokenProvider = mock(AccessTokenProvider.class);

        factory = new DefaultMailConnectionFactory(properties, aesCipher, accessTokenProvider, null);
    }

    @Test
    @DisplayName("openImap kết nối thành công với App Password hợp lệ trên GreenMail")
    void testOpenImapSuccess() throws Exception {
        MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", "validAppPassword16");

        try (ImapConnection connection = factory.openImap(credentials)) {
            assertNotNull(connection);
            assertTrue(connection.isConnected());
            assertNotNull(connection.folder("INBOX"));
        }
    }

    @Test
    @DisplayName("openImap ném MailAuthException khi sai mật khẩu")
    void testOpenImapAuthFailed() {
        MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", "wrong-password");

        assertThrows(MailAuthException.class, () -> factory.openImap(credentials));
    }

    @Test
    @DisplayName("openSmtp gửi MimeMessage thành công qua GreenMail")
    void testOpenSmtpSendSuccess() throws Exception {
        MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", "validAppPassword16");

        try (SmtpConnection connection = factory.openSmtp(credentials)) {
            assertNotNull(connection);
            assertTrue(connection.isConnected());

            MimeMessage message = connection.createMimeMessage();
            message.setFrom(new InternetAddress("testuser@gmail.com"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("recipient@example.com"));
            message.setSubject("Test Subject");
            message.setText("Hello GreenMail!");

            connection.sendMessage(message);
        }

        assertTrue(greenMail.waitForIncomingEmail(3000, 1));
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertEquals(1, received.length);
        assertEquals("Test Subject", received[0].getSubject());
    }

    @Test
    @DisplayName("testImap trả về ConnectionTestResult.OK khi đúng thông tin")
    void testTestImapOk() {
        MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", "validAppPassword16");

        ConnectionTestResult result = factory.testImap(credentials);

        assertNotNull(result);
        assertEquals(ConnectionTestResult.Status.OK, result.status());
        assertTrue(result.isSuccessful());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    @DisplayName("testImap trả về ConnectionTestResult.AUTH_FAILED khi sai thông tin, không ném exception")
    void testTestImapAuthFailed() {
        MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", "wrong-password");

        ConnectionTestResult result = factory.testImap(credentials);

        assertNotNull(result);
        assertEquals(ConnectionTestResult.Status.AUTH_FAILED, result.status());
        assertFalse(result.isSuccessful());
    }

    @Test
    @DisplayName("testImap trả về NETWORK_ERROR khi server đóng cổng không phản hồi")
    void testTestImapNetworkError() {
        properties.setImapPort(49999); // Cổng không có service chạy

        MailCredentials credentials = MailCredentials.ofAppPassword("testuser@gmail.com", "validAppPassword16");
        ConnectionTestResult result = factory.testImap(credentials);

        assertNotNull(result);
        assertEquals(ConnectionTestResult.Status.NETWORK_ERROR, result.status());
        assertFalse(result.isSuccessful());
    }
}

