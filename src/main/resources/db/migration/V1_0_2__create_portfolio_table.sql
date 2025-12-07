CREATE TABLE portfolio (
                           id BIGSERIAL PRIMARY KEY,
                           user_id BIGINT NOT NULL,
                           symbol VARCHAR(20) NOT NULL,
                           quantity INTEGER NOT NULL DEFAULT 0,
                           avg_price DECIMAL(10, 2) DEFAULT 0.00,
                           CONSTRAINT fk_portfolio_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                           CONSTRAINT uk_user_symbol UNIQUE (user_id, symbol)
);

CREATE INDEX idx_portfolio_user_id ON portfolio(user_id);
CREATE INDEX idx_portfolio_symbol ON portfolio(symbol);