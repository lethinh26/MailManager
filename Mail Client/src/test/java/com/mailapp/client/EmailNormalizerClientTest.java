package com.mailapp.client;

import com.mailapp.client.util.EmailNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailNormalizerClientTest {

    @Test
    @DisplayName("Client EmailNormalizer khớp 100% với Server (dấu chấm, +tag, googlemail.com, chữ hoa, Workspace)")
    void clientNormalizerMatchesServerRules() {
        assertEquals("johndoe@gmail.com", EmailNormalizer.normalizeKey("  John.Doe@Gmail.COM "));
        assertEquals("johndoe@gmail.com", EmailNormalizer.normalizeKey("j.o.h.n.d.o.e+work@gmail.com"));
        assertEquals("johndoe@gmail.com", EmailNormalizer.normalizeKey("John.Doe+tag@googlemail.com"));
        assertEquals("john.doe+work@ptit.edu.vn", EmailNormalizer.normalizeKey(" John.Doe+Work@PTIT.EDU.VN "));
        assertEquals("a.b@company.io", EmailNormalizer.normalizeKey("A.B@Company.IO"));
        assertEquals("", EmailNormalizer.normalizeKey(null));
        assertEquals("", EmailNormalizer.normalizeKey("   "));
    }
}
