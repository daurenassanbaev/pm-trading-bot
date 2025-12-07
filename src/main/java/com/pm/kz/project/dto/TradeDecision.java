package com.pm.kz.project.dto;

import com.pm.kz.project.entity.TradeAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeDecision {
    private TradeAction action;
    private BigDecimal confidence;
    private String reason;
    private Integer suggestedQuantity;
}