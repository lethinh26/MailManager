package com.mailapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotEnvAliasPrecedenceTest {
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void legacyDotEnvValuesDoNotOverrideCanonicalAliases() {
        Map<String, String> legacy = Map.of("JWT_SECRET", "legacy-jwt", "AES_KEY", "legacy-aes",
                "KEYSTORE_PASSWORD", "legacy-keystore");
        Map<String, String> canonical = Map.of("MAILAPP_JWT_SECRET", "canonical-jwt",
                "MAILAPP_AES_KEY", "canonical-aes", "MAILAPP_KEYSTORE_PASS", "canonical-keystore");
        Map<String, String> propertyAliases = Map.of("mailapp.jwt.secret", "${MAILAPP_JWT_SECRET:${JWT_SECRET:}}",
                "mailapp.aes.key", "${MAILAPP_AES_KEY:${AES_KEY:}}",
                "server.ssl.key-store-password", "${MAILAPP_KEYSTORE_PASS:${KEYSTORE_PASSWORD:}}");
        Map<String, String> original = new HashMap<>();
        legacy.keySet().forEach(key -> original.put(key, System.getProperty(key)));
        canonical.keySet().forEach(key -> original.put(key, System.getProperty(key)));
        propertyAliases.keySet().forEach(key -> original.put(key, System.getProperty(key)));
        try {
            propertyAliases.keySet().forEach(System::clearProperty);
            legacy.keySet().forEach(System::clearProperty);
            canonical.forEach(System::setProperty);
            MailServerApplication.applyDotEnvProperties(legacy);

            propertyAliases.keySet().forEach(key -> assertNull(System.getProperty(key), key));
            StandardEnvironment environment = new StandardEnvironment();
            assertEquals("canonical-jwt", environment.resolveRequiredPlaceholders(propertyAliases.get("mailapp.jwt.secret")));
            assertEquals("canonical-aes", environment.resolveRequiredPlaceholders(propertyAliases.get("mailapp.aes.key")));
            assertEquals("canonical-keystore", environment.resolveRequiredPlaceholders(propertyAliases.get("server.ssl.key-store-password")));
        } finally {
            original.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }
}
