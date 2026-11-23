package com.mailapp.mail.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử bảo mật HTML Sanitizer & Filename Sanitizer (Chống XSS, Path Traversal)")
class MailSecurityAndSanitizerTest {

    private final HtmlSanitizerService sanitizer = new HtmlSanitizerService();

    @Test
    @DisplayName("FilenameSanitizer: Chặn ../../x.exe, ký tự điều khiển và sinh Content-Disposition RFC 5987")
    void testFilenameSanitization() {
        assertEquals("x.exe", FilenameSanitizer.sanitize("../../x.exe"));
        assertEquals("evil.bat", FilenameSanitizer.sanitize("..\\..\\Windows\\System32\\evil\r\n.bat"));
        assertEquals("báo cáo đồ án.pdf", FilenameSanitizer.sanitize("báo cáo đồ án.pdf"));
        assertEquals("attachment.bin", FilenameSanitizer.sanitize("   ...  "));

        String cd = FilenameSanitizer.buildContentDisposition("báo cáo.pdf");
        assertTrue(cd.startsWith("attachment; filename=\""));
        assertTrue(cd.contains("filename*=UTF-8''"));

        assertEquals("application/octet-stream", FilenameSanitizer.safeDownloadContentType("text/html; charset=UTF-8"));
        assertEquals("application/octet-stream", FilenameSanitizer.safeDownloadContentType("image/svg+xml"));
        assertEquals("application/pdf", FilenameSanitizer.safeDownloadContentType("application/pdf"));
    }

    @Test
    @DisplayName("Hồi quy FilenameSanitizer: Chặn tên thiết bị Windows (CON, NUL, AUX, COM1, LPT9) và cắt tên quá dài")
    void testFilenameSanitizer_WindowsReservedNamesAndLongNames() {
        assertEquals("file_CON", FilenameSanitizer.sanitize("CON"));
        assertEquals("file_CON.txt", FilenameSanitizer.sanitize("CON.txt"));
        assertEquals("file_nul.pdf", FilenameSanitizer.sanitize("nul.pdf"));
        assertEquals("file_COM1.docx", FilenameSanitizer.sanitize("COM1.docx"));
        assertEquals("file_LPT9.zip", FilenameSanitizer.sanitize("LPT9.zip"));

        String veryLong = "Bao_cao_do_an_lap_trinh_mang_" + "a".repeat(220) + ".pdf";
        String truncated = FilenameSanitizer.sanitize(veryLong);
        assertTrue(truncated.length() <= 180);
        assertTrue(truncated.endsWith(".pdf"));
    }

    @Test
    @DisplayName("HtmlSanitizerService: Chặn XSS, chặn ảnh tracker khi loadRemote=false, giữ ảnh cid:, giới hạn kích thước")
    void testHtmlSanitizer() {
        String malicious = "<p onclick=\"alert(1)\">Hi</p><script>evil()</script><a href=\"javascript:alert(1)\">x</a>"
                + "<img src=\"cid:img1\"/><img src=\"https://spy.example/1x1.png\"/>";

        HtmlSanitizerService.SanitizeResult blocked = sanitizer.sanitize(malicious, false);
        assertTrue(blocked.hasBlockedRemoteImages());
        assertFalse(blocked.html().contains("<script"));
        assertFalse(blocked.html().contains("onclick"));
        assertFalse(blocked.html().contains("javascript:"));
        assertTrue(blocked.html().contains("cid:img1"));
        assertFalse(blocked.html().contains("src=\"https://spy.example/1x1.png\""));

        HtmlSanitizerService.SanitizeResult allowed = sanitizer.sanitize(malicious, true);
        assertFalse(allowed.hasBlockedRemoteImages());
        assertTrue(allowed.html().contains("https://spy.example/1x1.png"));

        // Kiểm tra cờ truncated khi vượt 512 KB
        String huge = "<p>" + "A".repeat(HtmlSanitizerService.MAX_BODY_CHARS + 100) + "</p>";
        HtmlSanitizerService.SanitizeResult trunc = sanitizer.sanitize(huge, false);
        assertTrue(trunc.truncated());
    }
}

