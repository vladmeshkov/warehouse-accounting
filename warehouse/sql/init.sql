-- ─────────────────────────────────────────────────────────────────────────────
--  warehouse_accounting — Скрипт генерации базы данных
--  СУБД: MySQL 8.0   Кодировка: utf8mb4
--  Листинг соответствует Приложению В пояснительной записки
-- ─────────────────────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS `warehouse_accounting`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `warehouse_accounting`;

-- ── role ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `role` (
    `role_id`   INT          NOT NULL AUTO_INCREMENT,
    `role_name` VARCHAR(50)  NOT NULL,
    PRIMARY KEY (`role_id`),
    UNIQUE INDEX `uk_role_name` (`role_name` ASC)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── user ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `user` (
    `user_id`       INT           NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(50)   NOT NULL,
    `password_hash` CHAR(64)      NOT NULL,
    `role_id`       INT           NOT NULL,
    `full_name`     VARCHAR(100)  NULL DEFAULT NULL,
    `phone`         VARCHAR(20)   NULL DEFAULT NULL,
    `email`         VARCHAR(100)  NULL DEFAULT NULL,
    `created_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `is_active`     TINYINT(1)    NOT NULL DEFAULT '1',
    PRIMARY KEY (`user_id`),
    UNIQUE INDEX `uk_user_username` (`username` ASC),
    INDEX `idx_user_role` (`role_id` ASC),
    CONSTRAINT `fk_user_role`
        FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── warehouse ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `warehouse` (
    `warehouse_id`       INT          NOT NULL AUTO_INCREMENT,
    `name`               VARCHAR(100) NOT NULL,
    `address`            VARCHAR(255) NULL DEFAULT NULL,
    `responsible_user_id` INT         NULL DEFAULT NULL,
    PRIMARY KEY (`warehouse_id`),
    INDEX `idx_warehouse_responsible` (`responsible_user_id` ASC),
    CONSTRAINT `fk_warehouse_user`
        FOREIGN KEY (`responsible_user_id`) REFERENCES `user` (`user_id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── product ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `product` (
    `product_id`      INT            NOT NULL AUTO_INCREMENT,
    `article`         VARCHAR(50)    NOT NULL,
    `name`            VARCHAR(150)   NOT NULL,
    `category`        VARCHAR(100)   NULL DEFAULT NULL,
    `unit`            VARCHAR(20)    NOT NULL,
    `purchase_price`  DECIMAL(12,2)  NULL DEFAULT NULL,
    `selling_price`   DECIMAL(12,2)  NULL DEFAULT NULL,
    `min_stock_level` INT            NOT NULL DEFAULT '0',
    `description`     TEXT           NULL DEFAULT NULL,
    PRIMARY KEY (`product_id`),
    UNIQUE INDEX `uk_product_article` (`article` ASC),
    INDEX `idx_product_category` (`category` ASC)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── stock ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `stock` (
    `warehouse_id` INT NOT NULL,
    `product_id`   INT NOT NULL,
    `quantity`     INT NOT NULL DEFAULT '0',
    PRIMARY KEY (`warehouse_id`, `product_id`),
    INDEX `idx_stock_product` (`product_id` ASC),
    CONSTRAINT `fk_stock_warehouse`
        FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`warehouse_id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_stock_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── supplier ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `supplier` (
    `supplier_id`    INT          NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(150) NOT NULL,
    `contact_person` VARCHAR(100) NULL DEFAULT NULL,
    `phone`          VARCHAR(20)  NULL DEFAULT NULL,
    `email`          VARCHAR(100) NULL DEFAULT NULL,
    `address`        VARCHAR(255) NULL DEFAULT NULL,
    `inn`            VARCHAR(20)  NULL DEFAULT NULL,
    PRIMARY KEY (`supplier_id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── customer ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `customer` (
    `customer_id`    INT          NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(150) NOT NULL,
    `contact_person` VARCHAR(100) NULL DEFAULT NULL,
    `phone`          VARCHAR(20)  NULL DEFAULT NULL,
    `email`          VARCHAR(100) NULL DEFAULT NULL,
    `address`        VARCHAR(255) NULL DEFAULT NULL,
    PRIMARY KEY (`customer_id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── product_supplier ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `product_supplier` (
    `product_id`   INT           NOT NULL,
    `supplier_id`  INT           NOT NULL,
    `supply_price` DECIMAL(12,2) NULL DEFAULT NULL,
    `delivery_days` INT          NULL DEFAULT NULL,
    PRIMARY KEY (`product_id`, `supplier_id`),
    INDEX `idx_ps_supplier` (`supplier_id` ASC),
    CONSTRAINT `fk_ps_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_ps_supplier`
        FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`supplier_id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── document ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `document` (
    `document_id`         INT                                              NOT NULL AUTO_INCREMENT,
    `document_type`       ENUM('INCOME','OUTCOME','TRANSFER','INVENTORY') NOT NULL,
    `document_number`     VARCHAR(50)                                      NOT NULL,
    `document_date`       DATETIME                                         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `warehouse_id_from`   INT                                              NULL DEFAULT NULL,
    `warehouse_id_to`     INT                                              NULL DEFAULT NULL,
    `supplier_id`         INT                                              NULL DEFAULT NULL,
    `customer_id`         INT                                              NULL DEFAULT NULL,
    `responsible_user_id` INT                                              NOT NULL,
    `comment`             TEXT                                             NULL DEFAULT NULL,
    PRIMARY KEY (`document_id`),
    UNIQUE INDEX `uk_document_number` (`document_number` ASC),
    INDEX `idx_document_date`          (`document_date` ASC),
    INDEX `idx_document_type`          (`document_type` ASC),
    INDEX `idx_document_warehouse_from`(`warehouse_id_from` ASC),
    INDEX `idx_document_warehouse_to`  (`warehouse_id_to` ASC),
    INDEX `idx_document_supplier`      (`supplier_id` ASC),
    INDEX `idx_document_customer`      (`customer_id` ASC),
    INDEX `idx_document_responsible`   (`responsible_user_id` ASC),
    CONSTRAINT `fk_document_user`
        FOREIGN KEY (`responsible_user_id`) REFERENCES `user` (`user_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_document_warehouse_from`
        FOREIGN KEY (`warehouse_id_from`) REFERENCES `warehouse` (`warehouse_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_document_warehouse_to`
        FOREIGN KEY (`warehouse_id_to`) REFERENCES `warehouse` (`warehouse_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_document_supplier`
        FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`supplier_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_document_customer`
        FOREIGN KEY (`customer_id`) REFERENCES `customer` (`customer_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── document_item ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `document_item` (
    `item_id`     INT            NOT NULL AUTO_INCREMENT,
    `document_id` INT            NOT NULL,
    `product_id`  INT            NOT NULL,
    `quantity`    DECIMAL(12,3)  NOT NULL,
    `price`       DECIMAL(12,2)  NULL DEFAULT NULL,
    PRIMARY KEY (`item_id`),
    INDEX `idx_document_item_document` (`document_id` ASC),
    INDEX `idx_document_item_product`  (`product_id` ASC),
    CONSTRAINT `fk_document_item_document`
        FOREIGN KEY (`document_id`) REFERENCES `document` (`document_id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_document_item_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── consumption_history ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `consumption_history` (
    `history_id`       INT           NOT NULL AUTO_INCREMENT,
    `product_id`       INT           NOT NULL,
    `consumption_date` DATE          NOT NULL,
    `total_quantity`   DECIMAL(12,3) NOT NULL,
    PRIMARY KEY (`history_id`),
    UNIQUE INDEX `uk_consumption_product_date` (`product_id` ASC, `consumption_date` ASC),
    CONSTRAINT `fk_consumption_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── deficit_forecast ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `deficit_forecast` (
    `forecast_id`           INT           NOT NULL AUTO_INCREMENT,
    `warehouse_id`          INT           NOT NULL,
    `product_id`            INT           NOT NULL,
    `forecast_date`         DATE          NOT NULL,
    `avg_daily_consumption` DECIMAL(12,3) NULL DEFAULT NULL,
    `days_until_deficit`    INT           NULL DEFAULT NULL,
    `estimated_deficit_date` DATE         NULL DEFAULT NULL,
    `last_calculated`       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`forecast_id`),
    UNIQUE INDEX `uk_forecast_wh_prod_date` (`warehouse_id` ASC, `product_id` ASC, `forecast_date` ASC),
    INDEX `idx_forecast_deficit_date` (`estimated_deficit_date` ASC),
    CONSTRAINT `fk_forecast_warehouse`
        FOREIGN KEY (`warehouse_id`) REFERENCES `warehouse` (`warehouse_id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_forecast_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
