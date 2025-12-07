package com.pm.kz.project.agents;

import com.pm.kz.project.dto.ExecutionResult;
import com.pm.kz.project.dto.MarketUpdate;
import com.pm.kz.project.dto.TradeDecision;
import com.pm.kz.project.entity.Portfolio;
import com.pm.kz.project.entity.Trade;
import com.pm.kz.project.entity.TradeAction;
import com.pm.kz.project.entity.User;
import com.pm.kz.project.repository.PortfolioRepository;
import com.pm.kz.project.repository.TradeRepository;
import com.pm.kz.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionAgent {
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;

    @Transactional
    public ExecutionResult executeTrade(
            User user,
            MarketUpdate marketUpdate,
            TradeDecision decision) {

        log.info("⚡ ExecutionAgent: Исполнение {} для {}",
                decision.getAction(),
                marketUpdate.getSymbol());

        if (decision.getAction() == TradeAction.HOLD) {
            // Сохраняем HOLD как запись в истории
            saveTrade(user, marketUpdate, decision, 0, BigDecimal.ZERO);

            return new ExecutionResult(
                    true,
                    "💤 Решение: HOLD. Позиция сохранена. " + decision.getReason(),
                    null,
                    user.getCash(),
                    "Портфель не изменен"
            );
        }

        if (decision.getAction() == TradeAction.BUY) {
            return executeBuy(user, marketUpdate, decision);
        } else {
            return executeSell(user, marketUpdate, decision);
        }
    }

    private ExecutionResult executeBuy(User user, MarketUpdate marketUpdate, TradeDecision decision) {
        BigDecimal price = marketUpdate.getCurrentPrice();
        int quantity = decision.getSuggestedQuantity();

        // Проверяем что количество положительное
        if (quantity <= 0) {
            return new ExecutionResult(
                    false,
                    "❌ Невозможно купить: рекомендуемое количество = " + quantity,
                    null,
                    user.getCash(),
                    "Требуется больше средств"
            );
        }

        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(quantity));

        // Проверяем достаточно ли средств
        if (user.getCash().compareTo(totalCost) < 0) {
            return new ExecutionResult(
                    false,
                    "❌ Недостаточно средств для покупки",
                    null,
                    user.getCash(),
                    String.format("Требуется: $%s, доступно: $%s", totalCost, user.getCash())
            );
        }

        // Обновляем баланс
        user.setCash(user.getCash().subtract(totalCost));
        userRepository.save(user);

        // Обновляем или создаем портфель
        Portfolio portfolio = portfolioRepository
                .findByUserAndSymbol(user, marketUpdate.getSymbol())
                .orElse(new Portfolio(null, user, marketUpdate.getSymbol(), 0, BigDecimal.ZERO));

        // Вычисляем новую среднюю цену
        BigDecimal newAvgPrice = calculateNewAvgPrice(
                portfolio.getQuantity(),
                portfolio.getAvgPrice(),
                quantity,
                price
        );

        portfolio.setQuantity(portfolio.getQuantity() + quantity);
        portfolio.setAvgPrice(newAvgPrice);
        portfolioRepository.save(portfolio);

        // Сохраняем сделку
        Trade savedTrade = saveTrade(user, marketUpdate, decision, quantity, totalCost);

        log.info("✅ Покупка выполнена: {} x {} по ${}, общая стоимость: ${}",
                quantity,
                marketUpdate.getSymbol(),
                price,
                totalCost);

        return new ExecutionResult(
                true,
                String.format("✅ Куплено %d x %s по $%s\n💡 %s",
                        quantity,
                        marketUpdate.getSymbol(),
                        price,
                        decision.getReason()),
                savedTrade.getId(),
                user.getCash(),
                String.format("В портфеле: %d акций, средняя цена: $%s",
                        portfolio.getQuantity(),
                        newAvgPrice)
        );
    }

    private ExecutionResult executeSell(User user, MarketUpdate marketUpdate, TradeDecision decision) {
        BigDecimal price = marketUpdate.getCurrentPrice();

        // Получаем портфель
        Portfolio portfolio = portfolioRepository
                .findByUserAndSymbol(user, marketUpdate.getSymbol())
                .orElse(null);

        // Проверяем наличие позиции
        if (portfolio == null || portfolio.getQuantity() <= 0) {
            return new ExecutionResult(
                    false,
                    "❌ Нет позиции для продажи " + marketUpdate.getSymbol(),
                    null,
                    user.getCash(),
                    "Сначала купите акции"
            );
        }

        // Определяем реальное количество для продажи
        int requestedQuantity = decision.getSuggestedQuantity();
        int actualQuantity = Math.min(requestedQuantity, portfolio.getQuantity());

        // Если запрошено больше чем есть, продаем все что есть
        if (actualQuantity <= 0) {
            return new ExecutionResult(
                    false,
                    "❌ Недостаточно акций для продажи",
                    null,
                    user.getCash(),
                    String.format("Доступно: %d акций", portfolio.getQuantity())
            );
        }

        BigDecimal totalRevenue = price.multiply(BigDecimal.valueOf(actualQuantity));

        // Вычисляем прибыль/убыток
        BigDecimal purchaseCost = portfolio.getAvgPrice().multiply(BigDecimal.valueOf(actualQuantity));
        BigDecimal profitLoss = totalRevenue.subtract(purchaseCost);
        String profitInfo = profitLoss.compareTo(BigDecimal.ZERO) >= 0
                ? String.format("📈 Прибыль: $%s", profitLoss.abs())
                : String.format("📉 Убыток: $%s", profitLoss.abs());

        // Обновляем баланс
        user.setCash(user.getCash().add(totalRevenue));
        userRepository.save(user);

        // Обновляем портфель
        portfolio.setQuantity(portfolio.getQuantity() - actualQuantity);
        portfolioRepository.save(portfolio);

        // Сохраняем сделку
        Trade savedTrade = saveTrade(user, marketUpdate, decision, actualQuantity, totalRevenue);

        log.info("✅ Продажа выполнена: {} x {} по ${}, выручка: ${}, {}",
                actualQuantity,
                marketUpdate.getSymbol(),
                price,
                totalRevenue,
                profitInfo);

        String message = String.format("✅ Продано %d x %s по $%s\n%s\n💡 %s",
                actualQuantity,
                marketUpdate.getSymbol(),
                price,
                profitInfo,
                decision.getReason());

        String portfolioUpdate = portfolio.getQuantity() > 0
                ? String.format("Осталось: %d акций", portfolio.getQuantity())
                : "Позиция закрыта полностью";

        return new ExecutionResult(
                true,
                message,
                savedTrade.getId(),
                user.getCash(),
                portfolioUpdate
        );
    }

    private Trade saveTrade(
            User user,
            MarketUpdate marketUpdate,
            TradeDecision decision,
            int quantity,
            BigDecimal total) {

        Trade trade = new Trade();
        trade.setUser(user);
        trade.setSymbol(marketUpdate.getSymbol());
        trade.setAction(decision.getAction());
        trade.setQuantity(quantity);
        trade.setPrice(marketUpdate.getCurrentPrice());
        trade.setTotal(total);
        trade.setConfidence(decision.getConfidence());
        trade.setReason(decision.getReason());

        return tradeRepository.save(trade);
    }

    private BigDecimal calculateNewAvgPrice(
            int oldQty,
            BigDecimal oldAvg,
            int newQty,
            BigDecimal newPrice) {

        // Если старого количества нет, просто возвращаем новую цену
        if (oldQty == 0) {
            return newPrice;
        }

        // Общая стоимость старых акций
        BigDecimal oldTotal = oldAvg.multiply(BigDecimal.valueOf(oldQty));

        // Общая стоимость новых акций
        BigDecimal newTotal = newPrice.multiply(BigDecimal.valueOf(newQty));

        // Средняя цена = (старая стоимость + новая стоимость) / (старое кол-во + новое кол-во)
        return oldTotal.add(newTotal)
                .divide(BigDecimal.valueOf(oldQty + newQty), 2, RoundingMode.HALF_UP);
    }
}