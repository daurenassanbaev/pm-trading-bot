package com.pm.kz.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionResponse {
    private String symbol;
    private String action;
    
    @JsonProperty("confidence_up")
    private Double confidenceUp;
    
    @JsonProperty("confidence_down")
    private Double confidenceDown;
    
    private String reason;
    private LocalDateTime timestamp;
}
