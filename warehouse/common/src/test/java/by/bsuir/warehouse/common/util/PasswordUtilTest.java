package by.bsuir.warehouse.common.util;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты утилиты хэширования паролей SHA-256.
 */
@DisplayName("PasswordUtil — тесты хэширования паролей")
class PasswordUtilTest {

    @Test
    @DisplayName("hash: возвращает 64-символьную hex-строку")
    void hash_returns64HexChars() {
        String hash = PasswordUtil.hash("password123");
        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA-256 должен давать 64 hex-символа");
        assertTrue(hash.matches("[0-9a-f]+"), "Хэш должен содержать только hex-символы");
    }

    @Test
    @DisplayName("hash: одинаковый пароль → одинаковый хэш")
    void hash_sameInput_sameOutput() {
        String h1 = PasswordUtil.hash("mySecret");
        String h2 = PasswordUtil.hash("mySecret");
        assertEquals(h1, h2, "Хэш должен быть детерминированным");
    }

    @Test
    @DisplayName("hash: разные пароли → разные хэши")
    void hash_differentInputs_differentOutputs() {
        String h1 = PasswordUtil.hash("password1");
        String h2 = PasswordUtil.hash("password2");
        assertNotEquals(h1, h2);
    }

    @Test
    @DisplayName("hash: известный вектор — SHA-256 для 'abc'")
    void hash_knownVector_abc() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2ec73b00361bbef0469348423f656fbc1e2d
        // Ожидаемое значение из стандарта NIST
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        // Примечание: у нас UTF-8 кодировка, это стандартное SHA-256("abc")
        assertEquals(expected, PasswordUtil.hash("abc"),
                "Хэш для 'abc' должен соответствовать стандарту SHA-256");
    }

    @Test
    @DisplayName("verify: верный пароль → true")
    void verify_correctPassword_returnsTrue() {
        String hash = PasswordUtil.hash("correctPassword");
        assertTrue(PasswordUtil.verify("correctPassword", hash));
    }

    @Test
    @DisplayName("verify: неверный пароль → false")
    void verify_wrongPassword_returnsFalse() {
        String hash = PasswordUtil.hash("correctPassword");
        assertFalse(PasswordUtil.verify("wrongPassword", hash));
    }

    @Test
    @DisplayName("verify: null пароль → false (без исключения)")
    void verify_nullPassword_returnsFalse() {
        String hash = PasswordUtil.hash("password");
        assertFalse(PasswordUtil.verify(null, hash));
    }

    @Test
    @DisplayName("verify: null хэш → false (без исключения)")
    void verify_nullHash_returnsFalse() {
        assertFalse(PasswordUtil.verify("password", null));
    }

    @Test
    @DisplayName("hash: пустой пароль → IllegalArgumentException")
    void hash_emptyPassword_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hash(""));
    }

    @Test
    @DisplayName("hash: null → IllegalArgumentException")
    void hash_null_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hash(null));
    }

    @Test
    @DisplayName("hash: кириллические символы хэшируются корректно")
    void hash_cyrillicPassword_worksCorrectly() {
        String hash = PasswordUtil.hash("Пароль123");
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(PasswordUtil.verify("Пароль123", hash));
    }
}
