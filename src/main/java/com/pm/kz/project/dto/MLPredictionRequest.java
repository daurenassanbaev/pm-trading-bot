package com.pm.kz.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionRequest {
    private String symbol;
    private BigDecimal price;
    private Map<String, Double> features;
}