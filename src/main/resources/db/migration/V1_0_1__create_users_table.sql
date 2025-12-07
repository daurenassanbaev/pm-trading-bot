CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       telegram_id VARCHAR(255) UNIQUE NOT NULL,
                       username VARCHAR(255),
                       cash DECIMAL(15, 2) DEFAULT 10000.00
);

CREATE INDEX idx_users_telegram_id ON users(telegram_id);
