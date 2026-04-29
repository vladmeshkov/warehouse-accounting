# Клиент-серверное приложение для учёта складских операций
### с модулем прогнозирования дефицита

**БГУИР, кафедра ЭИ, дисциплина «Программирование сетевых приложений», 2026**  
Студент: Мешков Владислав Дмитриевич, группа 477901

---

## Технологический стек

| Компонент         | Технология                    |
|-------------------|-------------------------------|
| Язык              | Java 17 (LTS)                 |
| Клиент (GUI)      | JavaFX 21                     |
| Сетевой протокол  | TCP/IP сокеты, Java Serializable |
| СУБД              | MySQL 8.0                     |
| Доступ к данным   | JDBC (MySQL Connector/J 8.3)  |
| Сборка            | Apache Maven (multi-module)   |
| Тестирование      | JUnit 5 + Mockito             |
| Контейнеризация   | Docker + Docker Compose       |

## Структура проекта

```
warehouse-accounting/
├── common/          # Общие классы: модели, протокол, утилиты
│   └── src/main/java/by/bsuir/warehouse/common/
│       ├── model/   # BaseEntity, User, Product, Warehouse, Stock,
│       │            # Document, DeficitForecast и др. (11 классов)
│       ├── protocol/ # Request (Builder), Response, Action (37 команд)
│       └── util/    # PasswordUtil (SHA-256), ValidationUtil
├── server/          # Серверная часть: бизнес-логика, БД, сеть
│   └── src/main/java/by/bsuir/warehouse/server/
│       ├── config/  # ServerConfig, DBConnection (оба Singleton)
│       ├── dao/     # 8 интерфейсов DAO + 8 JDBC-реализаций
│       ├── service/ # AuthService, InventoryService, ForecastService
│       └── network/ # ServerApp, ClientHandler, RequestDispatcher
├── client/          # Клиентская JavaFX-часть
│   └── src/main/java/by/bsuir/warehouse/client/
│       ├── network/ # NetworkClient, ClientContext
│       └── controller/ # 15 контроллеров экранов
├── sql/
│   ├── init.sql     # Схема БД (12 таблиц, 3НФ)
│   └── seed.sql     # Начальные тестовые данные
├── docker-compose.yml
└── Dockerfile.server
```

## Быстрый старт

### Вариант 1: Docker Compose (рекомендуется)

```bash
# 1. Сборка проекта
mvn clean package -DskipTests

# 2. Запуск MySQL + Server в контейнерах
docker compose up -d

# 3. Запуск клиента (локально, требует Java 17 + JavaFX)
java -jar client/target/warehouse-client.jar
```

### Вариант 2: Локальный запуск

**Требования:** Java 17+, MySQL 8.0, Maven 3.8+

```bash
# 1. Создать БД и применить схему
mysql -u root -p < sql/init.sql
mysql -u root -p < sql/seed.sql

# 2. Настроить подключение
# Отредактировать server/src/main/resources/config.properties

# 3. Сборка
mvn clean package -DskipTests

# 4. Запуск сервера
java -jar server/target/warehouse-server.jar

# 5. Запуск клиента (в другом терминале)
java -jar client/target/warehouse-client.jar
```

## Тестовые учётные записи

| Логин        | Пароль         | Роль                    |
|--------------|----------------|-------------------------|
| `admin`      | `admin123`     | Администратор           |
| `manager`    | `manager123`   | Менеджер по закупкам    |
| `worker`     | `worker123`    | Кладовщик               |
| `sales`      | `sales123`     | Менеджер по продажам    |
| `accountant` | `accountant123`| Бухгалтер               |

## Запуск тестов

```bash
mvn test
```

Тесты покрывают:
- `ForecastServiceTest` — формула экспоненциального сглаживания (α=0.3), 13 тестов
- `AuthServiceTest` — аутентификация и управление сессиями, 14 тестов
- `InventoryServiceTest` — складские операции и инвентаризация, 15 тестов
- `PasswordUtilTest` — SHA-256 хэширование, 9 тестов
- `ModelProtocolTest` — модели и протокол обмена, 18 тестов

## Реализованные паттерны проектирования

| Паттерн        | Где реализован                                      |
|----------------|-----------------------------------------------------|
| **Singleton**  | `ServerConfig`, `DBConnection`, `ClientContext`     |
| **Factory Method** | `RequestDispatcher` — создание обработчиков по `Action` |
| **Builder**    | `Request.Builder` — построение запросов             |
| **Strategy**   | `ForecastService` — алгоритм прогнозирования        |
| **Observer**   | JavaFX `ObservableList` в контроллерах              |

## Алгоритм прогнозирования дефицита

Метод экспоненциального сглаживания (раздел 3.5 ПЗ):

```
S(t) = α × X(t) + (1 − α) × S(t−1)

где:
  α = 0.3  (задаётся в config.properties)
  X(t) — фактический расход за день t
  S(t) — прогнозный среднесуточный расход

daysUntilDeficit = (currentStock − minStockLevel) / S(t)
```

## Функциональные возможности (12 вариантов использования)

1. Авторизация и аутентификация с ролями
2. Управление справочником товаров
3. Управление складами
4. Приёмка товара (приходная накладная)
5. Отгрузка товара (расходная накладная)
6. Внутреннее перемещение между складами
7. Просмотр текущих остатков с фильтрацией
8. История операций (журнал документов)
9. **Прогноз дефицита** с цветовой индикацией
10. **Алерты о критических остатках** (≤7 дней)
11. Инвентаризация с актом расхождений
12. Управление пользователями (только ADMIN)
