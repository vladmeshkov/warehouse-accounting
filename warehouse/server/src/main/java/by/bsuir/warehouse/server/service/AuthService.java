package by.bsuir.warehouse.server.service;

import by.bsuir.warehouse.common.model.RegistrationStatus;
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

public class AuthService {

    private static final Logger log = Logger.getLogger(AuthService.class.getName());
    private static final ThreadLocal<User> currentUser = new ThreadLocal<>();
    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final UserDAO userDAO;
    private final int sessionTimeoutMs;

    public AuthService() {
        this.userDAO = new UserDAOImpl();
        this.sessionTimeoutMs =
                ServerConfig.getInstance().getSessionTimeoutMinutes() * 60 * 1000;
    }

    public String login(String username, String plainPassword) {
        if (username == null || plainPassword == null) {
            throw new SecurityException("Логин и пароль не могут быть пустыми");
        }

        Optional<User> opt = userDAO.findByUsername(username.trim());
        if (opt.isEmpty()) {
            throw new SecurityException("Пользователь не найден");
        }

        User user = opt.get();
        if (!PasswordUtil.verify(plainPassword, user.getPasswordHash())) {
            throw new SecurityException("Неверный пароль");
        }

        // Проверка статуса регистрации
        if (user.getRegistrationStatus() == RegistrationStatus.PENDING) {
            throw new SecurityException("Ваша заявка на регистрацию ещё не рассмотрена администратором.");
        }
        if (user.getRegistrationStatus() == RegistrationStatus.REJECTED) {
            throw new SecurityException("Ваша заявка на регистрацию отклонена. Обратитесь к администратору.");
        }
        if (!user.isActive()) {
            throw new SecurityException("Учётная запись заблокирована");
        }

        sessions.entrySet().removeIf(e -> e.getValue().user.getId() == user.getId());

        String token = UUID.randomUUID().toString();
        sessions.put(token, new SessionEntry(user));
        log.info("Пользователь вошёл: " + username + " [" + user.getRole().getRoleName() + "]");
        return token;
    }

    public void register(User user) {
        if (user == null) throw new IllegalArgumentException("Данные пользователя не могут быть пустыми");
        if (user.getUsername() == null || user.getUsername().trim().isEmpty())
            throw new IllegalArgumentException("Логин обязателен");
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty())
            throw new IllegalArgumentException("Пароль обязателен");
        if (user.getRole() == null) throw new IllegalArgumentException("Роль не выбрана");

        if (userDAO.findByUsername(user.getUsername().trim()).isPresent()) {
            throw new SecurityException("Пользователь с таким логином уже существует");
        }

        int id = userDAO.register(user);
        user.setId(id);
        log.info("Новый пользователь зарегистрирован (ожидает одобрения): " + user.getUsername());
    }

    public User authenticate(String token) {
        if (token == null) throw new SecurityException("Токен не передан");

        SessionEntry entry = sessions.get(token);
        if (entry == null) throw new SecurityException("Сессия не найдена. Войдите заново.");

        if (System.currentTimeMillis() - entry.createdAt > sessionTimeoutMs) {
            sessions.remove(token);
            throw new SecurityException("Сессия истекла. Войдите заново.");
        }

        entry.createdAt = System.currentTimeMillis();
        currentUser.set(entry.user);
        return entry.user;
    }

    public void logout(String token) {
        if (token != null) {
            SessionEntry removed = sessions.remove(token);
            if (removed != null) {
                log.info("Пользователь вышел: " + removed.user.getUsername());
            }
        }
        currentUser.remove();
    }

    public void requireRole(User user, String... allowedRoles) {
        if (user == null) throw new SecurityException("Не аутентифицирован");
        String userRole = user.getRole().getRoleName();
        for (String role : allowedRoles) {
            if (role.equals(userRole)) return;
        }
        throw new SecurityException("Недостаточно прав. Требуется роль: "
                + String.join(" или ", allowedRoles));
    }

    public static void clearCurrentUser() {
        currentUser.remove();
    }

    public static User getCurrentUser() {
        return currentUser.get();
    }

    private static class SessionEntry {
        final User user;
        long createdAt;

        SessionEntry(User user) {
            this.user = user;
            this.createdAt = System.currentTimeMillis();
        }
    }
}