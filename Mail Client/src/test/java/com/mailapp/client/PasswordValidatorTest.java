package com.mailapp.client;

import com.mailapp.client.util.PasswordValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử chính sách và độ mạnh mật khẩu (PasswordValidatorTest)")
class PasswordValidatorTest {

    @Test
    @DisplayName("Chính sách mật khẩu: Từ chối null, rỗng, ngắn hơn 8 ký tự")
    void testMeetsPolicy_ShortOrEmpty() {
        assertFalse(PasswordValidator.meetsPolicy(null));
        assertFalse(PasswordValidator.meetsPolicy(""));
        assertFalse(PasswordValidator.meetsPolicy("a".repeat(5) + "1"));
        assertFalse(PasswordValidator.meetsPolicy("1".repeat(7)));
        assertFalse(PasswordValidator.meetsPolicy("A" + "b".repeat(6)));
    }

    @Test
    @DisplayName("Chính sách mật khẩu: Từ chối nếu chỉ có chữ hoặc chỉ có số")
    void testMeetsPolicy_MissingLetterOrDigit() {
        assertFalse(PasswordValidator.meetsPolicy("a".repeat(8))); // 8 ký tự nhưng chỉ có chữ thường
        assertFalse(PasswordValidator.meetsPolicy("A".repeat(8))); // 8 ký tự nhưng chỉ có chữ hoa
        assertFalse(PasswordValidator.meetsPolicy("1".repeat(8))); // 8 ký tự nhưng chỉ có số
        assertFalse(PasswordValidator.meetsPolicy("!@#$%^&*()")); // Chỉ có ký tự đặc biệt
    }

    @Test
    @DisplayName("Chính sách mật khẩu: Chấp nhận mật khẩu >= 8 ký tự có cả chữ và số")
    void testMeetsPolicy_Valid() {
        assertTrue(PasswordValidator.meetsPolicy("a".repeat(7) + "1"));
        assertTrue(PasswordValidator.meetsPolicy("A" + "b".repeat(7) + "2"));
        assertTrue(PasswordValidator.meetsPolicy("A" + "b".repeat(5) + "@" + "2026"));
    }

    @Test
    @DisplayName("Độ mạnh mật khẩu: WEAK cho mật khẩu không đạt chính sách")
    void testEvaluateStrength_Weak() {
        assertEquals(PasswordValidator.Strength.WEAK, PasswordValidator.evaluateStrength(null));
        assertEquals(PasswordValidator.Strength.WEAK, PasswordValidator.evaluateStrength(""));
        assertEquals(PasswordValidator.Strength.WEAK, PasswordValidator.evaluateStrength("1".repeat(6)));
        assertEquals(PasswordValidator.Strength.WEAK, PasswordValidator.evaluateStrength("a".repeat(8)));
    }

    @Test
    @DisplayName("Độ mạnh mật khẩu: MEDIUM cho mật khẩu đạt chuẩn cơ bản")
    void testEvaluateStrength_Medium() {
        assertEquals(PasswordValidator.Strength.MEDIUM, PasswordValidator.evaluateStrength("a".repeat(4) + "1234"));
        assertEquals(PasswordValidator.Strength.MEDIUM, PasswordValidator.evaluateStrength("b".repeat(6) + "88"));
    }

    @Test
    @DisplayName("Độ mạnh mật khẩu: STRONG cho mật khẩu >= 10 ký tự gồm hoa, thường, số, ký tự đặc biệt")
    void testEvaluateStrength_Strong() {
        String strong1 = "A" + "b".repeat(5) + "@" + "C" + "2026!";
        String strong2 = "X" + "@" + "y".repeat(4) + "0" + "#2026";
        assertEquals(PasswordValidator.Strength.STRONG, PasswordValidator.evaluateStrength(strong1));
        assertEquals(PasswordValidator.Strength.STRONG, PasswordValidator.evaluateStrength(strong2));
    }
}
