package com.pm.kz.project.service;

import com.pm.kz.project.agents.DecisionAgent;
import com.pm.kz.project.agents.ExecutionAgent;
import com.pm.kz.project.agents.MarketMonitorAgent;
import com.pm.kz.project.dto.ExecutionResult;
import com.pm.kz.project.dto.MarketUpdate;
import com.pm.kz.project.dto.TradeDecision;
import com.pm.kz.project.entity.User;
import com.pm.kz.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingCoordinator {
    private final MarketMonitorAgent marketMonitorAgent;
    private final DecisionAgent decisionAgent;
    private final ExecutionAgent executionAgent;
    private final UserRepository userRepository;
    
    public ExecutionResult runCycle(String telegramId, String symbol) {
        log.info("🚀 TradingCoordinator: Начало торгового цикла для {} ({})", 
                 telegramId, symbol);
        
        try {
            // Получаем пользователя
            User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
            
            // Шаг 1: Мониторинг рынка
            MarketUpdate marketUpdate = marketMonitorAgent.fetchMarketData(symbol);
            
            // Шаг 2: Принятие решения
            TradeDecision decision = decisionAgent.makeDecision(marketUpdate, user);
            
            // Шаг 3: Исполнение сделки
            ExecutionResult result = executionAgent.executeTrade(user, marketUpdate, decision);
            
            log.info("🏁 Торговый цикл завершен: {}", result.getMessage());
            return result;
            
        } catch (Exception e) {
            log.error("❌ Ошибка в торговом цикле: {}", e.getMessage(), e);
            return new ExecutionResult(
                false,
                "❌ Ошибка: " + e.getMessage(),
                null,
                null,
                null
            );
        }
    }
}
