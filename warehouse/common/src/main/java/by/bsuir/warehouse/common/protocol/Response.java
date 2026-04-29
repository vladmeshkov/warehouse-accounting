package by.bsuir.warehouse.common.protocol;

import java.io.Serializable;

/**
 * Ответ сервера клиенту.
 *
 * Структура:
 *   success    — true если операция выполнена успешно
 *   message    — описание результата или текст ошибки
 *   data       — полезные данные (список объектов, одиночный объект и т.д.)
 *   token      — токен сессии (возвращается только при LOGIN)
 *
 * Фабричные методы для удобного создания:
 *   Response.ok(data)
 *   Response.ok("Сообщение об успехе")
 *   Response.error("Текст ошибки")
 */
public class Response implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final Serializable data;
    private final String token;

    private Response(boolean success, String message, Serializable data, String token) {
        this.success = success;
        this.message = message;
        this.data    = data;
        this.token   = token;
    }

    // ── Фабричные методы ──────────────────────────────────────────────────

    public static Response ok(Serializable data) {
        return new Response(true, "OK", data, null);
    }

    public static Response ok(String message) {
        return new Response(true, message, null, null);
    }

    public static Response ok(String message, Serializable data) {
        return new Response(true, message, data, null);
    }

    public static Response withToken(String token, Serializable data) {
        return new Response(true, "Авторизация успешна", data, token);
    }

    public static Response error(String message) {
        return new Response(false, message, null, null);
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public boolean isSuccess()      { return success; }
    public String getMessage()      { return message; }
    public Serializable getData()   { return data; }
    public String getToken()        { return token; }

    @Override
    public String toString() {
        return "Response{success=" + success + ", message='" + message + "'}";
    }
}
