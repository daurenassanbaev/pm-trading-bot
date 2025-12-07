package com.pm.kz.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {
    private boolean success;
    private String message;
    private Long tradeId;
    private BigDecimal newCashBalance;
    private String portfolioUpdate;
}