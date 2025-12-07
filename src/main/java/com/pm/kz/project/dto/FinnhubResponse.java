package com.pm.kz.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FinnhubResponse {
    @JsonProperty("c")  // current price
    private Double currentPrice;
    
    @JsonProperty("h")  // high price of the day
    private Double highPrice;
    
    @JsonProperty("l")  // low price of the day
    private Double lowPrice;
    
    @JsonProperty("o")  // open price of the day
    private Double openPrice;
    
    @JsonProperty("pc") // previous close price
    private Double previousClose;
    
    @JsonProperty("t")  // timestamp
    private Long timestamp;
}