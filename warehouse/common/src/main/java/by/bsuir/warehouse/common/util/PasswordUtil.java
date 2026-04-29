package by.bsuir.warehouse.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Утилита для хэширования паролей алгоритмом SHA-256.
 * Пароли никогда не хранятся и не передаются в открытом виде.
 */
public final class PasswordUtil {

    private PasswordUtil() {}

    /**
     * Вычисляет SHA-256 хэш строки и возвращает его в виде 64-символьной
     * hex-строки (нижний регистр).
     *
     * @param plainText открытый текст пароля
     * @return 64-символьный hex-хэш
     */
    public static String hash(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть пустым");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    plainText.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 гарантированно поддерживается в любой JVM
            throw new RuntimeException("SHA-256 не поддерживается", e);
        }
    }

    /**
     * Проверяет соответствие открытого пароля сохранённому хэшу.
     */
    public static boolean verify(String plainText, String storedHash) {
        if (plainText == null || storedHash == null) return false;
        return hash(plainText).equalsIgnoreCase(storedHash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
