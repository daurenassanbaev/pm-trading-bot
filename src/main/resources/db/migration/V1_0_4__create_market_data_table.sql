CREATE TABLE market_data (
                             id BIGSERIAL PRIMARY KEY,
                             symbol VARCHAR(20) NOT NULL,
                             price DECIMAL(10, 2) NOT NULL,
                             recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_market_data_symbol ON market_data(symbol);
CREATE INDEX idx_market_data_recorded_at ON market_data(recorded_at DESC);