package com.pm.kz.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketUpdate {
    private String symbol;
    private BigDecimal currentPrice;
    private LocalDateTime timestamp;
    private String source;
}