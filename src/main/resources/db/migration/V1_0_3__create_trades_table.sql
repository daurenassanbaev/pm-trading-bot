CREATE TABLE trades (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        symbol VARCHAR(20) NOT NULL,
                        action VARCHAR(10) NOT NULL,
                        quantity INTEGER,
                        price DECIMAL(10, 2),
                        total DECIMAL(15, 2),
                        confidence DECIMAL(5, 2),
                        reason TEXT,
                        executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_trades_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        CONSTRAINT chk_action CHECK (action IN ('BUY', 'SELL', 'HOLD'))
);

CREATE INDEX idx_trades_user_id ON trades(user_id);
CREATE INDEX idx_trades_symbol ON trades(symbol);
CREATE INDEX idx_trades_executed_at ON trades(executed_at DESC);