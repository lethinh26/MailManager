package com.mailapp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailNormalizerTest {

    @Test
    @DisplayName("Chuẩn hóa Gmail: bỏ khoảng trắng, chữ hoa, dấu chấm, +tag và đổi googlemail.com -> gmail.com")
    void normalizeGmailVariants() {
        assertEquals("johndoe@gmail.com", EmailNormalizer.normalizeKey("  John.Doe@Gmail.COM "));
        assertEquals("johndoe@gmail.com", EmailNormalizer.normalizeKey("j.o.h.n.d.o.e+work@gmail.com"));
        assertEquals("johndoe@gmail.com", EmailNormalizer.normalizeKey("John.Doe+news+2026@GoogleMail.com"));
        assertEquals("cuongdfle@gmail.com", EmailNormalizer.normalizeKey("cuong.df.le+ptit@googlemail.com"));
    }

    @Test
    @DisplayName("Tên miền Workspace/khác: chỉ hạ chữ thường và bỏ khoảng trắng, giữ nguyên dấu chấm và +tag")
    void normalizeWorkspaceAndOtherDomains() {
        assertEquals("john.doe+work@ptit.edu.vn", EmailNormalizer.normalizeKey("  John.Doe+Work@PTIT.EDU.VN  "));
        assertEquals("a.b@company.io", EmailNormalizer.normalizeKey("A.B@Company.IO"));
        assertEquals("ab@company.io", EmailNormalizer.normalizeKey("ab@company.io"));
    }

    @Test
    @DisplayName("Xử lý an toàn với null, chuỗi rỗng hoặc thiếu ký tự @")
    void normalizeEdgeCases() {
        assertEquals("", EmailNormalizer.normalizeKey(null));
        assertEquals("", EmailNormalizer.normalizeKey("   "));
        assertEquals("notanemail", EmailNormalizer.normalizeKey("  NotAnEmail  "));
    }
}
