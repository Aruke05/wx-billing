-- 交易表
CREATE TABLE IF NOT EXISTS wx_pay_txn (
                                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                          trade_time TIMESTAMP NOT NULL,
                                          trade_date DATE NOT NULL,
                                          trade_hour INT NOT NULL,
                                          weekday INT NOT NULL,
                                          trade_type VARCHAR(64) NOT NULL,
    channel_type VARCHAR(64),
    counterparty VARCHAR(128),
    product VARCHAR(256),
    direction VARCHAR(16) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    pay_method VARCHAR(64),
    status VARCHAR(64),
    order_id VARCHAR(64) NOT NULL,
    merchant_order_id VARCHAR(64),
    remark VARCHAR(512),
    import_batch_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 导入批次表
CREATE TABLE IF NOT EXISTS import_batch (
                                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                            file_name VARCHAR(256) NOT NULL,
    uploaded_by VARCHAR(128),
    record_count INT NOT NULL DEFAULT 0,
    inserted_count INT NOT NULL DEFAULT 0,
    duplicated_count INT NOT NULL DEFAULT 0,
    period_start TIMESTAMP NULL,
    period_end TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 索引
CREATE UNIQUE INDEX uk_order_id    ON wx_pay_txn(order_id);
CREATE INDEX idx_trade_time        ON wx_pay_txn(trade_time);
CREATE INDEX idx_trade_date        ON wx_pay_txn(trade_date);
CREATE INDEX idx_weekday           ON wx_pay_txn(weekday);
CREATE INDEX idx_direction         ON wx_pay_txn(direction);
CREATE INDEX idx_pay_method        ON wx_pay_txn(pay_method);
CREATE INDEX idx_status            ON wx_pay_txn(status);
CREATE INDEX idx_import_batch      ON wx_pay_txn(import_batch_id);
