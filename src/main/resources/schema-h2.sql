CREATE TABLE IF NOT EXISTS wx_pay_txn (
                                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                          trade_time TIMESTAMP NOT NULL,
                                          trade_date DATE NOT NULL,
                                          trade_hour INT NOT NULL,
                                          weekday INT NOT NULL,
                                          trade_type VARCHAR(64) NOT NULL,
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

CREATE TABLE IF NOT EXISTS import_batch (
                                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                            file_name VARCHAR(256) NOT NULL,
    uploaded_by VARCHAR(128),
    record_count INT NOT NULL DEFAULT 0,
    inserted_count INT NOT NULL DEFAULT 0,
    duplicated_count INT NOT NULL DEFAULT 0,
    period_start TIMESTAMP,
    period_end TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

-- 索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_order_id ON wx_pay_txn(order_id);
CREATE INDEX IF NOT EXISTS idx_trade_time   ON wx_pay_txn(trade_time);
CREATE INDEX IF NOT EXISTS idx_trade_date   ON wx_pay_txn(trade_date);
CREATE INDEX IF NOT EXISTS idx_weekday      ON wx_pay_txn(weekday);
CREATE INDEX IF NOT EXISTS idx_direction    ON wx_pay_txn(direction);
CREATE INDEX IF NOT EXISTS idx_pay_method   ON wx_pay_txn(pay_method);
CREATE INDEX IF NOT EXISTS idx_status       ON wx_pay_txn(status);
CREATE INDEX IF NOT EXISTS idx_import_batch ON wx_pay_txn(import_batch_id);
