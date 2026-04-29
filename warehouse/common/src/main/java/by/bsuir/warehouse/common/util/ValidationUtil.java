package by.bsuir.warehouse.common.util;

import java.math.BigDecimal;

/**
 * Утилита валидации входных данных.
 * Используется и на сервере (бизнес-правила), и на клиенте (UI-проверки).
 */
public final class ValidationUtil {

    private ValidationUtil() {}

    /** Проверяет, что строка не null и не пустая */
    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Проверяет, что строка не превышает максимальную длину */
    public static boolean exceedsLength(String s, int maxLen) {
        return s != null && s.length() > maxLen;
    }

    /** Проверяет, что количество строго положительное */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Проверяет, что количество не отрицательное */
    public static boolean isNonNegative(int value) {
        return value >= 0;
    }

    /** Проверяет формат артикула: только буквы, цифры и дефис */
    public static boolean isValidArticle(String article) {
        return article != null && article.matches("[A-Za-z0-9А-Яа-я\\-]+");
    }

    /** Проверяет формат email (упрощённый) */
    public static boolean isValidEmail(String email) {
        return email == null || email.isEmpty()
                || email.matches("^[\\w.+\\-]+@[\\w\\-]+\\.[a-zA-Z]{2,}$");
    }
}
