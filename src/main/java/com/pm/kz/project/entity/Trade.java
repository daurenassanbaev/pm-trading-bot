package com.pm.kz.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String symbol;
    
    @Enumerated(EnumType.STRING)
    private TradeAction action;
    
    private Integer quantity;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(precision = 15, scale = 2)
    private BigDecimal total;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;
    
    private String reason;
    
    private LocalDateTime executedAt = LocalDateTime.now();
}