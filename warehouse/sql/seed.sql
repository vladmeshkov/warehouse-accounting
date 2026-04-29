-- ─────────────────────────────────────────────────────────────────────────────
--  seed.sql — начальные данные для тестирования системы
--  Все пароли = имя пользователя + "123" (например admin → admin123)
--  SHA-256("admin123") = 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a
-- ─────────────────────────────────────────────────────────────────────────────

USE `warehouse_accounting`;

-- ── Роли ─────────────────────────────────────────────────────────────────────
INSERT INTO `role` (`role_id`, `role_name`) VALUES
    (1, 'Администратор'),
    (2, 'Менеджер по закупкам'),
    (3, 'Кладовщик'),
    (4, 'Менеджер по продажам'),
    (5, 'Бухгалтер')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`);

-- ── Пользователи ─────────────────────────────────────────────────────────────
-- Пароли (SHA-256):
--   admin123     → 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a
--   manager123   → ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f
--   worker123    → ef797c8118f02dfb649607dd5d3f8c7623048c9c063d532cc95c5ed7a898a64f
--   sales123     → a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3
--   accountant123→ 5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5
INSERT INTO `user` (`user_id`, `username`, `password_hash`, `role_id`, `full_name`, `phone`, `email`) VALUES
    (1, 'admin',
     '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a',
     1, 'Мешков Владислав Дмитриевич', '+375291234567', 'admin@warehouse.by'),
    (2, 'manager',
     'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f',
     2, 'Иванов Иван Иванович', '+375292345678', 'manager@warehouse.by'),
    (3, 'worker',
     'ef797c8118f02dfb649607dd5d3f8c7623048c9c063d532cc95c5ed7a898a64f',
     3, 'Петров Пётр Петрович', '+375293456789', 'worker@warehouse.by'),
    (4, 'sales',
     'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3',
     4, 'Сидорова Анна Викторовна', '+375294567890', 'sales@warehouse.by'),
    (5, 'accountant',
     '5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5',
     5, 'Козлова Мария Степановна', '+375295678901', 'accountant@warehouse.by')
ON DUPLICATE KEY UPDATE `full_name` = VALUES(`full_name`);

-- ── Склады ────────────────────────────────────────────────────────────────────
INSERT INTO `warehouse` (`warehouse_id`, `name`, `address`, `responsible_user_id`) VALUES
    (1, 'Склад №1 (Главный)',   'г. Минск, ул. Складская, д. 1',  3),
    (2, 'Склад №2 (Запасной)',  'г. Минск, ул. Логистическая, д. 5', NULL),
    (3, 'Склад №3 (Временный)', 'г. Минск, ул. Промышленная, д. 12', NULL)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ── Поставщики ────────────────────────────────────────────────────────────────
INSERT INTO `supplier` (`supplier_id`, `name`, `contact_person`, `phone`, `email`, `inn`) VALUES
    (1, 'ООО «ТехноСнаб»',    'Романов Алексей',    '+375291111111', 'info@technosnab.by', '100123456'),
    (2, 'ИП Борисов А.В.',    'Борисов Андрей',     '+375292222222', 'borisov@mail.ru',    '200234567'),
    (3, 'СООО «ПромОптТорг»', 'Захарова Светлана',  '+375293333333', 'opt@promtorg.by',    '300345678')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ── Покупатели ────────────────────────────────────────────────────────────────
INSERT INTO `customer` (`customer_id`, `name`, `contact_person`, `phone`, `email`) VALUES
    (1, 'ООО «АльфаТрейд»',  'Николаев Дмитрий',   '+375291000001', 'alpha@trade.by'),
    (2, 'ЗАО «БетаГрупп»',   'Волкова Ирина',      '+375292000002', 'beta@group.by'),
    (3, 'ОАО «ГаммаТехник»', 'Лебедев Константин', '+375293000003', 'gamma@technik.by')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ── Товары (15 позиций для демонстрации прогнозирования) ──────────────────────
INSERT INTO `product` (`product_id`, `article`, `name`, `category`, `unit`,
                       `purchase_price`, `selling_price`, `min_stock_level`) VALUES
    (1,  'EL-001', 'Ноутбук Dell Inspiron 15',    'Электроника',   'шт.',   750.00,  950.00,  3),
    (2,  'EL-002', 'Монитор Samsung 24"',          'Электроника',   'шт.',   180.00,  240.00,  5),
    (3,  'EL-003', 'Клавиатура механическая',      'Периферия',     'шт.',    35.00,   55.00, 10),
    (4,  'EL-004', 'Мышь беспроводная Logitech',   'Периферия',     'шт.',    18.00,   30.00, 15),
    (5,  'EL-005', 'USB-хаб 4 порта',              'Периферия',     'шт.',     8.50,   15.00, 20),
    (6,  'OF-001', 'Бумага А4 (500 листов)',        'Офисные товары','пачка',   3.20,    5.50, 50),
    (7,  'OF-002', 'Ручка шариковая синяя',         'Офисные товары','упак.',   1.80,    3.00, 30),
    (8,  'OF-003', 'Тонер для принтера HP',         'Расходники',    'шт.',    22.00,   38.00,  5),
    (9,  'ST-001', 'Стеллаж металлический 5 полок','Мебель',        'шт.',   120.00,  190.00,  2),
    (10, 'ST-002', 'Стул офисный регулируемый',    'Мебель',        'шт.',    85.00,  140.00,  3),
    (11, 'CL-001', 'Средство для уборки полов 5л', 'Хозтовары',     'бут.',    4.50,    8.00, 10),
    (12, 'CL-002', 'Перчатки латексные (100 шт.)', 'Хозтовары',     'упак.',   3.80,    6.50, 20),
    (13, 'IT-001', 'Кабель HDMI 2м',               'Кабели',        'шт.',     3.50,    7.00, 25),
    (14, 'IT-002', 'Кабель USB-C 1м',              'Кабели',        'шт.',     2.80,    5.50, 30),
    (15, 'IT-003', 'Удлинитель 5 розеток 5м',      'Электро',       'шт.',     9.00,   16.00, 10)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ── Начальные остатки ──────────────────────────────────────────────────────────
-- Некоторые позиции намеренно близки к минимуму для демонстрации прогнозирования
INSERT INTO `stock` (`warehouse_id`, `product_id`, `quantity`) VALUES
    -- Склад №1 (Главный)
    (1,  1,  12), -- Ноутбуки: 12 (мин. 3) — норма
    (1,  2,   6), -- Мониторы: 6 (мин. 5) — почти критично
    (1,  3,  45), -- Клавиатуры: 45 (мин. 10) — норма
    (1,  4,  18), -- Мыши: 18 (мин. 15) — почти критично
    (1,  5,   8), -- USB-хабы: 8 (мин. 20) — НИЖЕ МИНИМУМА
    (1,  6,  80), -- Бумага: 80 (мин. 50) — норма
    (1,  7,  25), -- Ручки: 25 (мин. 30) — НИЖЕ МИНИМУМА
    (1,  8,   4), -- Тонер: 4 (мин. 5) — НИЖЕ МИНИМУМА
    (1,  9,   5), -- Стеллажи: 5 (мин. 2) — норма
    (1, 10,   3), -- Стулья: 3 (мин. 3) — на границе
    (1, 11,  30), -- Ср. для уборки: 30 (мин. 10) — норма
    (1, 12,  12), -- Перчатки: 12 (мин. 20) — НИЖЕ МИНИМУМА
    (1, 13,  60), -- Кабель HDMI: 60 (мин. 25) — норма
    (1, 14,  55), -- Кабель USB-C: 55 (мин. 30) — норма
    (1, 15,  20), -- Удлинители: 20 (мин. 10) — норма
    -- Склад №2 (Запасной)
    (2,  1,   5), -- Ноутбуки: 5
    (2,  6, 120), -- Бумага: 120
    (2, 13,  40)  -- Кабель HDMI: 40
ON DUPLICATE KEY UPDATE `quantity` = VALUES(`quantity`);

-- ── История расхода (30 дней) для демонстрации прогнозирования ───────────────
-- Симулируем реальный расход с небольшими колебаниями
INSERT INTO `consumption_history` (`product_id`, `consumption_date`, `total_quantity`) VALUES
    -- Ноутбуки (product_id=1): ~1.2/день
    (1, DATE_SUB(CURDATE(), INTERVAL 29 DAY), 1.0),
    (1, DATE_SUB(CURDATE(), INTERVAL 28 DAY), 2.0),
    (1, DATE_SUB(CURDATE(), INTERVAL 27 DAY), 1.0),
    (1, DATE_SUB(CURDATE(), INTERVAL 20 DAY), 1.0),
    (1, DATE_SUB(CURDATE(), INTERVAL 14 DAY), 2.0),
    (1, DATE_SUB(CURDATE(), INTERVAL  7 DAY), 1.0),
    (1, DATE_SUB(CURDATE(), INTERVAL  3 DAY), 1.0),
    (1, DATE_SUB(CURDATE(), INTERVAL  1 DAY), 1.0),
    -- Мониторы (product_id=2): ~0.8/день — критично (6 осталось, мин. 5)
    (2, DATE_SUB(CURDATE(), INTERVAL 25 DAY), 1.0),
    (2, DATE_SUB(CURDATE(), INTERVAL 18 DAY), 1.0),
    (2, DATE_SUB(CURDATE(), INTERVAL 12 DAY), 1.0),
    (2, DATE_SUB(CURDATE(), INTERVAL  6 DAY), 1.0),
    (2, DATE_SUB(CURDATE(), INTERVAL  2 DAY), 1.0),
    -- Бумага (product_id=6): ~3.5/день
    (6, DATE_SUB(CURDATE(), INTERVAL 29 DAY), 4.0),
    (6, DATE_SUB(CURDATE(), INTERVAL 28 DAY), 3.0),
    (6, DATE_SUB(CURDATE(), INTERVAL 27 DAY), 4.0),
    (6, DATE_SUB(CURDATE(), INTERVAL 26 DAY), 3.0),
    (6, DATE_SUB(CURDATE(), INTERVAL 14 DAY), 5.0),
    (6, DATE_SUB(CURDATE(), INTERVAL  7 DAY), 3.0),
    (6, DATE_SUB(CURDATE(), INTERVAL  3 DAY), 4.0),
    (6, DATE_SUB(CURDATE(), INTERVAL  1 DAY), 2.0),
    -- Тонер (product_id=8): ~0.5/день — почти закончился (4 осталось, мин. 5)
    (8, DATE_SUB(CURDATE(), INTERVAL 20 DAY), 1.0),
    (8, DATE_SUB(CURDATE(), INTERVAL 10 DAY), 1.0),
    (8, DATE_SUB(CURDATE(), INTERVAL  5 DAY), 1.0)
ON DUPLICATE KEY UPDATE `total_quantity` = VALUES(`total_quantity`);

-- ── Демо-документ (приходная накладная) ───────────────────────────────────────
INSERT INTO `document` (`document_id`, `document_type`, `document_number`,
                        `document_date`, `warehouse_id_to`, `supplier_id`,
                        `responsible_user_id`, `comment`) VALUES
    (1, 'INCOME', 'INC-20260201-001',
     DATE_SUB(NOW(), INTERVAL 30 DAY),
     1, 1, 3, 'Первоначальное поступление товаров')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SELECT 'Инициализация тестовых данных завершена успешно.' AS message;
