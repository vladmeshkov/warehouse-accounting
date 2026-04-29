package by.bsuir.warehouse.server.service;

import by.bsuir.warehouse.common.model.Role;
import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.util.PasswordUtil;
import by.bsuir.warehouse.server.dao.UserDAO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты для AuthService.
 *
 * Тестируют:
 *   1. Успешный вход (корректные логин + пароль)
 *   2. Отказ при неверном пароле
 *   3. Отказ при несуществующем пользователе
 *   4. Отказ при заблокированной учётной записи
 *   5. Валидность/истечение токена
 *   6. Проверку ролей
 *   7. Выход из системы
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — тесты аутентификации и управления сессиями")
class AuthServiceTest {

    @Mock private UserDAO userDAO;

    private AuthService authService;

    private static final String PLAIN_PASSWORD = "testPassword123";
    private static final String HASHED_PASSWORD = PasswordUtil.hash(PLAIN_PASSWORD);

    @BeforeEach
    void setUp() throws Exception {
        authService = new AuthService();
        setField(authService, "userDAO", userDAO);
    }

    @AfterEach
    void tearDown() {
        AuthService.clearCurrentUser();
    }

    // ── Тесты успешного входа ─────────────────────────────────────────────

    @Test
    @DisplayName("login: корректные данные → возвращает непустой токен")
    void login_validCredentials_returnsToken() {
        User user = buildUser("admin", HASHED_PASSWORD, Role.ADMIN, true);
        when(userDAO.findByUsername("admin")).thenReturn(Optional.of(user));

        String token = authService.login("admin", PLAIN_PASSWORD);

        assertNotNull(token, "Токен не должен быть null");
        assertFalse(token.isBlank(), "Токен не должен быть пустым");
    }

    @Test
    @DisplayName("login: успешный вход → authenticate() возвращает пользователя")
    void login_success_authenticateReturnsUser() {
        User user = buildUser("admin", HASHED_PASSWORD, Role.ADMIN, true);
        when(userDAO.findByUsername("admin")).thenReturn(Optional.of(user));

        String token = authService.login("admin", PLAIN_PASSWORD);
        User authenticated = authService.authenticate(token);

        assertNotNull(authenticated);
        assertEquals("admin", authenticated.getUsername());
        assertEquals(Role.ADMIN, authenticated.getRole().getRoleName());
    }

    @Test
    @DisplayName("login: токен уникален при каждом входе")
    void login_multipleLogins_differentTokens() {
        User user = buildUser("admin", HASHED_PASSWORD, Role.ADMIN, true);
        when(userDAO.findByUsername("admin")).thenReturn(Optional.of(user));

        String token1 = authService.login("admin", PLAIN_PASSWORD);
        String token2 = authService.login("admin", PLAIN_PASSWORD);

        assertNotEquals(token1, token2, "Токены разных сессий должны отличаться");
    }

    // ── Тесты неверных данных ─────────────────────────────────────────────

    @Test
    @DisplayName("login: неверный пароль → SecurityException")
    void login_wrongPassword_throwsSecurityException() {
        User user = buildUser("admin", HASHED_PASSWORD, Role.ADMIN, true);
        when(userDAO.findByUsername("admin")).thenReturn(Optional.of(user));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> authService.login("admin", "wrongPassword"),
                "Должно бросить SecurityException при неверном пароле");
        assertTrue(ex.getMessage().contains("пароль"),
                "Сообщение должно упоминать пароль");
    }

    @Test
    @DisplayName("login: несуществующий пользователь → SecurityException")
    void login_unknownUser_throwsSecurityException() {
        when(userDAO.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(SecurityException.class,
                () -> authService.login("unknown", PLAIN_PASSWORD),
                "Должно бросить SecurityException для несуществующего пользователя");
    }

    @Test
    @DisplayName("login: заблокированная учётная запись → SecurityException")
    void login_inactiveUser_throwsSecurityException() {
        User user = buildUser("blocked", HASHED_PASSWORD, Role.WAREHOUSE_WORKER, false);
        when(userDAO.findByUsername("blocked")).thenReturn(Optional.of(user));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> authService.login("blocked", PLAIN_PASSWORD));
        assertTrue(ex.getMessage().toLowerCase().contains("блок"),
                "Сообщение должно упоминать блокировку");
    }

    @Test
    @DisplayName("login: пустой логин → SecurityException")
    void login_nullUsername_throwsException() {
        assertThrows(SecurityException.class,
                () -> authService.login(null, PLAIN_PASSWORD));
    }

    @Test
    @DisplayName("login: пустой пароль → SecurityException")
    void login_nullPassword_throwsException() {
        assertThrows(SecurityException.class,
                () -> authService.login("admin", null));
    }

    // ── Тесты токена/сессии ───────────────────────────────────────────────

    @Test
    @DisplayName("authenticate: недействительный токен → SecurityException")
    void authenticate_invalidToken_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> authService.authenticate("invalid-token-xyz"));
    }

    @Test
    @DisplayName("authenticate: null токен → SecurityException")
    void authenticate_nullToken_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> authService.authenticate(null));
    }

    @Test
    @DisplayName("authenticate: после logout токен недействителен")
    void authenticate_afterLogout_throwsSecurityException() {
        User user = buildUser("admin", HASHED_PASSWORD, Role.ADMIN, true);
        when(userDAO.findByUsername("admin")).thenReturn(Optional.of(user));

        String token = authService.login("admin", PLAIN_PASSWORD);
        authService.logout(token);

        assertThrows(SecurityException.class,
                () -> authService.authenticate(token),
                "После logout токен должен быть недействителен");
    }

    // ── Тесты проверки ролей ──────────────────────────────────────────────

    @Test
    @DisplayName("requireRole: пользователь с нужной ролью → без исключения")
    void requireRole_correctRole_noException() {
        User user = buildUser("manager", HASHED_PASSWORD, Role.PURCHASE_MANAGER, true);
        assertDoesNotThrow(() ->
                authService.requireRole(user, Role.ADMIN, Role.PURCHASE_MANAGER));
    }

    @Test
    @DisplayName("requireRole: пользователь с неподходящей ролью → SecurityException")
    void requireRole_wrongRole_throwsSecurityException() {
        User user = buildUser("worker", HASHED_PASSWORD, Role.WAREHOUSE_WORKER, true);
        assertThrows(SecurityException.class,
                () -> authService.requireRole(user, Role.ADMIN));
    }

    @Test
    @DisplayName("requireRole: null пользователь → SecurityException")
    void requireRole_nullUser_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> authService.requireRole(null, Role.ADMIN));
    }

    @Test
    @DisplayName("requireRole: бухгалтер пытается выполнить операцию кладовщика → запрет")
    void requireRole_accountantAsWarehouseWorker_denied() {
        User accountant = buildUser("buh", HASHED_PASSWORD, Role.ACCOUNTANT, true);
        assertThrows(SecurityException.class,
                () -> authService.requireRole(accountant,
                        Role.WAREHOUSE_WORKER, Role.ADMIN));
    }

    // ── Тесты ThreadLocal ─────────────────────────────────────────────────

    @Test
    @DisplayName("clearCurrentUser: очищает ThreadLocal")
    void clearCurrentUser_removesFromThreadLocal() {
        User user = buildUser("admin", HASHED_PASSWORD, Role.ADMIN, true);
        when(userDAO.findByUsername("admin")).thenReturn(Optional.of(user));

        String token = authService.login("admin", PLAIN_PASSWORD);
        authService.authenticate(token); // устанавливает ThreadLocal

        AuthService.clearCurrentUser();
        assertNull(AuthService.getCurrentUser(),
                "После clearCurrentUser() ThreadLocal должен быть null");
    }

    // ── Вспомогательные методы ────────────────────────────────────────────

    private User buildUser(String username, String hash, String roleName, boolean active) {
        Role role = new Role(1, roleName);
        User user = new User();
        user.setId(1);
        user.setUsername(username);
        user.setPasswordHash(hash);
        user.setRole(role);
        user.setFullName("Test User");
        user.setActive(active);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
