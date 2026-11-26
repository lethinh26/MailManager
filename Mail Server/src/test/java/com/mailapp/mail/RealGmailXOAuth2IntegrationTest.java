package com.mailapp.mail;

import com.mailapp.mail.model.MailCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Explicitly opted-in live test: a short-lived mail-scope access token is
 * provided out-of-band. The lifecycle uses only its own synthetic mail.
 */
@EnabledIfEnvironmentVariable(named = "MAILAPP_REAL_GMAIL_XOAUTH2", matches = "1")
@EnabledIfEnvironmentVariable(named = "MAILAPP_REAL_GMAIL_DEDICATED", matches = "TEST-ACCOUNT")
@EnabledIfEnvironmentVariable(named = "GMAIL_TEST_EMAIL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GMAIL_TEST_OAUTH2_ACCESS_TOKEN", matches = ".+")
class RealGmailXOAuth2IntegrationTest {
    @Test
    void authenticatesImapsAndSmtpWithRealXOAuth2() throws Exception {
        RealGmailIntegrationTest.safeStage("XOAUTH2 authentication", () ->
                RealGmailIntegrationTest.verifyAuthentication(credentials()));
    }

    @Test
    void sendsAndDownloadsSyntheticAttachmentWithRealXOAuth2() throws Exception {
        RealGmailIntegrationTest.safeStage("XOAUTH2 synthetic lifecycle", () ->
                RealGmailIntegrationTest.verifyLifecycle(credentials()));
    }

    private MailCredentials credentials() {
        return MailCredentials.ofOAuth2(
                RealGmailIntegrationTest.testEmail(), System.getenv("GMAIL_TEST_OAUTH2_ACCESS_TOKEN"));
    }
}
