package com.mailapp.client.util;

import java.awt.Color;

public class PasswordValidator {

    public enum Strength {
        WEAK("Yếu (Cần ít nhất 8 ký tự gồm cả chữ và số)", new Color(220, 53, 69), 25),
        MEDIUM("Trung bình (Đạt chuẩn)", new Color(255, 193, 7), 65),
        STRONG("Mạnh (Rất an toàn)", new Color(40, 167, 69), 100);

        private final String label;
        private final Color color;
        private final int percentage;

        Strength(String label, Color color, int percentage) {
            this.label = label;
            this.color = color;
            this.percentage = percentage;
        }

        public String getLabel() { return label; }
        public Color getColor() { return color; }
        public int getPercentage() { return percentage; }
    }

    public static boolean meetsPolicy(String password) {
        if (password == null || password.length() < 8
                || password.length() > 72
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*[0-9].*");
        return hasLetter && hasDigit;
    }

    public static Strength evaluateStrength(String password) {
        if (password == null || password.isEmpty()) {
            return Strength.WEAK;
        }

        if (!meetsPolicy(password)) {
            return Strength.WEAK;
        }

        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*[0-9].*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

        if (password.length() >= 10 && hasUpper && hasLower && hasDigit && hasSpecial) {
            return Strength.STRONG;
        }

        return Strength.MEDIUM;
    }
}

