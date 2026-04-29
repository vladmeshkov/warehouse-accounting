package by.bsuir.warehouse.server.service;

import by.bsuir.warehouse.common.model.User;
import by.bsuir.warehouse.common.util.PasswordUtil;
import by.bsuir.warehouse.server.config.ServerConfig;
import by.bsuir.warehouse.server.dao.UserDAO;
import by.bsuir.warehouse.server.dao.impl.UserDAOImpl;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Сервис аутентификации и управления сессиями.
 *
 * Хранит активные сессии в ConcurrentHashMap<token, SessionEntry>.
 * Текущий пользователь доступен через ThreadLocal для каждого потока-обработчика.
 * Пароли проверяются только в виде SHA-256 хэша — никогда в открытом виде.
 */
public class AuthService {

    private static final Logger log = Logger.getLogger(AuthService.class.getName());

    /** Текущий аутентифицированный пользователь для каждого потока */
    private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

    /** Активные сессии: token → SessionEntry */
    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    private final UserDAO userDAO;
    private final int sessionTimeoutMs;

    public AuthService() {
        this.userDAO = new UserDAOImpl();
        this.sessionTimeoutMs =
                ServerConfig.getInstance().getSessionTimeoutMinutes() * 60 * 1000;
    }

    // ── Публичный API ─────────────────────────────────────────────────────

    /**
     * Проверяет логин + пароль.
     * @return токен сессии, если авторизация прошла успешно
     * @throws SecurityException если логин/пароль неверны или учётная запись заблокирована
     */
    public String login(String username, String plainPassword) {
        if (username == null || plainPassword == null) {
            throw new SecurityException("Логин и пароль не могут быть пустыми");
        }

        Optional<User> opt = userDAO.findByUsername(username.trim());
        if (opt.isEmpty()) {
            throw new SecurityException("Пользователь не найден");
        }

        User user = opt.get();
        if (!user.isActive()) {
            throw new SecurityException("Учётная запись заблокирована");
        }
        if (!PasswordUtil.verify(plainPassword, user.getPasswordHash())) {
            throw new SecurityException("Неверный пароль");
        }

        // Инвалидируем старые сессии этого пользователя
        sessions.entrySet().removeIf(e -> e.getValue().user.getId() == user.getId());

        String token = UUID.randomUUID().toString();
        sessions.put(token, new SessionEntry(user));
        log.info("Пользователь вошёл: " + username + " [" + user.getRole().getRoleName() + "]");
        return token;
    }

    /**
     * Проверяет токен и устанавливает текущего пользователя в ThreadLocal.
     * @throws SecurityException если токен недействителен или истёк
     */
    public User authenticate(String token) {
        if (token == null) throw new SecurityException("Токен не передан");

        SessionEntry entry = sessions.get(token);
        if (entry == null) throw new SecurityException("Сессия не найдена. Войдите заново.");

        if (System.currentTimeMillis() - entry.createdAt > sessionTimeoutMs) {
            sessions.remove(token);
            throw new SecurityException("Сессия истекла. Войдите заново.");
        }

        entry.createdAt = System.currentTimeMillis(); // обновляем время активности
        currentUser.set(entry.user);
        return entry.user;
    }

    /** Завершает сессию пользователя */
    public void logout(String token) {
        if (token != null) {
            SessionEntry removed = sessions.remove(token);
            if (removed != null) {
                log.info("Пользователь вышел: " + removed.user.getUsername());
            }
        }
        currentUser.remove();
    }

    /**
     * Проверяет, имеет ли текущий пользователь указанную роль.
     * @throws SecurityException если роль не совпадает
     */
    public void requireRole(User user, String... allowedRoles) {
        if (user == null) throw new SecurityException("Не аутентифицирован");
        String userRole = user.getRole().getRoleName();
        for (String role : allowedRoles) {
            if (role.equals(userRole)) return;
        }
        throw new SecurityException("Недостаточно прав. Требуется роль: "
                + String.join(" или ", allowedRoles));
    }

    /** Очищает ThreadLocal после обработки запроса */
    public static void clearCurrentUser() {
        currentUser.remove();
    }

    /** Возвращает текущего пользователя из ThreadLocal */
    public static User getCurrentUser() {
        return currentUser.get();
    }

    // ── Внутренний класс сессии ───────────────────────────────────────────

    private static class SessionEntry {
        final User user;
        long createdAt;

        SessionEntry(User user) {
            this.user = user;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
