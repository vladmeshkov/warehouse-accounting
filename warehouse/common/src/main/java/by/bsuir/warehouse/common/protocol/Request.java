package by.bsuir.warehouse.common.protocol;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Запрос от клиента к серверу.
 *
 * Структура:
 *   action     — тип операции (Action enum)
 *   params     — строковые параметры (фильтры, идентификаторы и т.д.)
 *   payload    — произвольный Serializable-объект (сущность для сохранения)
 *   token      — токен сессии (устанавливается после LOGIN)
 *
 * Пример использования (Builder-паттерн):
 *   Request req = new Request.Builder(Action.CREATE_PRODUCT)
 *       .token(sessionToken)
 *       .payload(product)
 *       .build();
 */
public class Request implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Action action;
    private final Map<String, String> params;
    private final Serializable payload;
    private final String token;

    private Request(Builder builder) {
        this.action  = builder.action;
        this.params  = builder.params;
        this.payload = builder.payload;
        this.token   = builder.token;
    }

    public Action getAction()               { return action; }
    public Map<String, String> getParams()  { return params; }
    public Serializable getPayload()        { return payload; }
    public String getToken()                { return token; }

    /** Удобный метод получения строкового параметра */
    public String getParam(String key) {
        return params != null ? params.get(key) : null;
    }

    /** Удобный метод получения int-параметра */
    public int getIntParam(String key) {
        String v = getParam(key);
        return (v != null) ? Integer.parseInt(v) : 0;
    }

    @Override
    public String toString() {
        return "Request{action=" + action + ", params=" + params + "}";
    }

    // ── Builder (паттерн Builder) ──────────────────────────────────────────

    public static class Builder {
        private final Action action;
        private Map<String, String> params = new HashMap<>();
        private Serializable payload;
        private String token;

        public Builder(Action action) {
            this.action = action;
        }

        public Builder param(String key, String value) {
            this.params.put(key, value);
            return this;
        }

        public Builder param(String key, int value) {
            this.params.put(key, String.valueOf(value));
            return this;
        }

        public Builder payload(Serializable payload) {
            this.payload = payload;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Request build() {
            return new Request(this);
        }
    }
}
