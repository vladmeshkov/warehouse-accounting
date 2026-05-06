package by.bsuir.warehouse.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

public final class PasswordUtil {

    private PasswordUtil() {}

    public static String hash(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть пустым");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 не поддерживается", e);
        }
    }

    public static boolean verify(String plainText, String storedHash) {
        if (plainText == null || storedHash == null) return false;
        return hash(plainText).equalsIgnoreCase(storedHash);
    }

    /**
     * Валидация пароля по требованиям безопасности.
     * @return сообщение об ошибке или null, если пароль корректен
     */
    public static String validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Пароль не может быть пустым";
        }
        if (password.length() < 8) {
            return "Пароль должен содержать не менее 8 символов";
        }
        if (!Pattern.compile("[A-Z]").matcher(password).find()) {
            return "Пароль должен содержать хотя бы одну заглавную латинскую букву (A-Z)";
        }
        if (!Pattern.compile("[a-z]").matcher(password).find()) {
            return "Пароль должен содержать хотя бы одну строчную латинскую букву (a-z)";
        }
        if (!Pattern.compile("[0-9]").matcher(password).find()) {
            return "Пароль должен содержать хотя бы одну цифру (0-9)";
        }
        if (!Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]").matcher(password).find()) {
            return "Пароль должен содержать хотя бы один специальный символ (например, ! @ # $ % ^ & *)";
        }
        return null;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}