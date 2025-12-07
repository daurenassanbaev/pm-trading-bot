package com.pm.kz.project.agents;

import com.pm.kz.project.dto.MLPredictionRequest;
import com.pm.kz.project.dto.MLPredictionResponse;
import com.pm.kz.project.dto.MarketUpdate;
import com.pm.kz.project.dto.TradeDecision;
import com.pm.kz.project.entity.Portfolio;
import com.pm.kz.project.entity.TradeAction;
import com.pm.kz.project.entity.User;
import com.pm.kz.project.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class DecisionAgent {

    private final RestTemplate restTemplate;
    private final PortfolioRepository portfolioRepository;
    private final Random random = new Random();

    // ✅ История цен по каждому тикеру (последние 10 значений)
    private static final int MAX_HISTORY = 10;
    private final ConcurrentMap<String, Deque<BigDecimal>> priceHistory = new ConcurrentHashMap<>();

    // Статистика решений (потокобезопасная)
    private final AtomicInteger buyCount = new AtomicInteger(0);
    private final AtomicInteger sellCount = new AtomicInteger(0);
    private final AtomicInteger holdCount = new AtomicInteger(0);

    @Value("${ml.api.url}")
    private String mlApiUrl;

    // Порог для переопределения HOLD (0.0 = выключено)
    @Value("${ml.hold.override.threshold:0.55}")
    private double holdOverrideThreshold;

    public TradeDecision makeDecision(MarketUpdate marketUpdate, User user) {
        log.info("🤖 DecisionAgent: Анализ {} для принятия решения", marketUpdate.getSymbol());

        try {
            // Вызов реального ML API
            TradeDecision decision = callMLModel(marketUpdate, user);

            log.info("📊 Решение: {} с уверенностью {}%",
                    decision.getAction(),
                    decision.getConfidence());
            log.info("💡 Причина: {}", decision.getReason());

            // Статистика
            updateStatistics(decision.getAction());

            return decision;

        } catch (Exception e) {
            log.error("❌ Ошибка ML API: {}. Используем запасное решение", e.getMessage());
            return getFallbackDecision(marketUpdate, user);
        }
    }

    private TradeDecision callMLModel(MarketUpdate marketUpdate, User user) {
        try {
            // Подготовка данных для ML модели
            MLPredictionRequest request = preparePredictionRequest(marketUpdate);

            String url = mlApiUrl + "/predict";
            log.debug("📡 Отправка запроса к ML API: {}", url);

            // Логируем отправляемые данные
            log.info("📤 Отправляем в ML: symbol={}, price={}, features={}",
                    request.getSymbol(),
                    request.getPrice(),
                    request.getFeatures());

            // Вызов ML API
            MLPredictionResponse response = restTemplate.postForObject(
                    url,
                    request,
                    MLPredictionResponse.class
            );

            if (response == null) {
                throw new RuntimeException("Пустой ответ от ML API");
            }

            // Детальное логирование ответа
            log.info("📥 ML ответ: action={}, confidence_up={}, confidence_down={}, reason='{}'",
                    response.getAction(),
                    response.getConfidenceUp(),
                    response.getConfidenceDown(),
                    response.getReason());

            // Преобразование ответа в TradeDecision
            return convertToTradeDecision(response, marketUpdate, user);

        } catch (Exception e) {
            log.error("Ошибка вызова ML API: {}", e.getMessage(), e);
            throw e;
        }
    }

    private MLPredictionRequest preparePredictionRequest(MarketUpdate marketUpdate) {
        BigDecimal price = marketUpdate.getCurrentPrice();
        String symbol = marketUpdate.getSymbol();

        // ✅ 1) Обновляем историю цен для тикера
        Deque<BigDecimal> history = updatePriceHistory(symbol, price);

        // ✅ 2) Вычисляем индикаторы НА ОСНОВЕ РЕАЛЬНОЙ ИСТОРИИ
        double return1d = calculateReturn1d(history);
        double sma5 = calculateSMA(history, 5);
        double sma10 = calculateSMA(history, 10);

        // Защита от деления на ноль
        double priceOverSma5 = sma5 > 0 ? price.doubleValue() / sma5 : 1.0;

        Map<String, Double> features = new HashMap<>();
        features.put("return_1d", return1d);
        features.put("SMA_5", sma5);
        features.put("SMA_10", sma10);
        features.put("price_over_sma5", priceOverSma5);

        log.info("📊 Реальные индикаторы для {} (история: {} точек): return_1d={}, SMA5={}, SMA10={}, price/SMA5={}",
                symbol, history.size(),
                String.format("%.4f", return1d),
                String.format("%.2f", sma5),
                String.format("%.2f", sma10),
                String.format("%.4f", priceOverSma5));

        return new MLPredictionRequest(symbol, price, features);
    }

    /**
     * ✅ Обновляет историю цен для тикера (последние MAX_HISTORY значений)
     */
    private Deque<BigDecimal> updatePriceHistory(String symbol, BigDecimal currentPrice) {
        Deque<BigDecimal> history = priceHistory.computeIfAbsent(symbol, s -> new ArrayDeque<>());

        history.addLast(currentPrice);

        // Ограничиваем размер истории
        if (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }

        return history;
    }

    // ============ ТЕХНИЧЕСКИЕ ИНДИКАТОРЫ НА ОСНОВЕ РЕАЛЬНОЙ ИСТОРИИ ============

    /**
     * ✅ Расчет доходности за 1 период (return_1d)
     * Formula: (last - previous) / previous
     */
    private double calculateReturn1d(Deque<BigDecimal> history) {
        if (history.size() < 2) {
            return 0.0; // пока нет достаточно данных
        }

        BigDecimal[] arr = history.toArray(new BigDecimal[0]);
        BigDecimal prev = arr[arr.length - 2];
        BigDecimal last = arr[arr.length - 1];

        if (prev.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }

        return last.subtract(prev)
                .divide(prev, 8, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * ✅ Расчет Simple Moving Average (SMA)
     * Formula: (P1 + P2 + ... + PN) / N
     */
    private double calculateSMA(Deque<BigDecimal> history, int window) {
        if (history.isEmpty()) {
            return 0.0;
        }

        int size = history.size();
        int startIndex = Math.max(0, size - window);

        BigDecimal[] arr = history.toArray(new BigDecimal[0]);
        double sum = 0.0;
        int count = 0;

        for (int i = startIndex; i < size; i++) {
            sum += arr[i].doubleValue();
            count++;
        }

        return count > 0 ? sum / count : 0.0;
    }

    private TradeDecision convertToTradeDecision(
            MLPredictionResponse response,
            MarketUpdate marketUpdate,
            User user) {

        // Преобразуем action из строки в TradeAction enum
        TradeAction action;
        try {
            action = TradeAction.valueOf(response.getAction().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Неизвестное действие: {}, используем HOLD", response.getAction());
            action = TradeAction.HOLD;
        }

        double confidenceUp = response.getConfidenceUp() != null ? response.getConfidenceUp() : 0.0;
        double confidenceDown = response.getConfidenceDown() != null ? response.getConfidenceDown() : 0.0;

        // Логика переопределения HOLD
        if (action == TradeAction.HOLD && holdOverrideThreshold > 0) {
            double diff = Math.abs(confidenceUp - confidenceDown);
            log.debug("🔍 HOLD анализ: up={}, down={}, diff={}, threshold={}",
                    confidenceUp, confidenceDown, diff, holdOverrideThreshold);

            if (confidenceUp > holdOverrideThreshold && confidenceUp > confidenceDown) {
                action = TradeAction.BUY;
                log.info("🔄 Переопределили HOLD → BUY (up={} > threshold={})",
                        confidenceUp, holdOverrideThreshold);
            } else if (confidenceDown > holdOverrideThreshold && confidenceDown > confidenceUp) {
                action = TradeAction.SELL;
                log.info("🔄 Переопределили HOLD → SELL (down={} > threshold={})",
                        confidenceDown, holdOverrideThreshold);
            }
        }

        // Выбираем правильный confidence в зависимости от действия
        double confidenceValue;
        if (action == TradeAction.BUY) {
            confidenceValue = confidenceUp;
        } else if (action == TradeAction.SELL) {
            confidenceValue = confidenceDown;
        } else {
            // Для HOLD показываем уверенность в неопределенности
            confidenceValue = 1.0 - Math.abs(confidenceUp - confidenceDown);
        }

        BigDecimal confidence = BigDecimal.valueOf(confidenceValue * 100)
                .setScale(2, RoundingMode.HALF_UP);

        // Расчет количества с учетом уверенности
        Integer quantity = calculateQuantity(
                action,
                marketUpdate.getCurrentPrice(),
                user,
                marketUpdate.getSymbol(),
                confidenceValue
        );

        return new TradeDecision(
                action,
                confidence,
                response.getReason(),
                quantity
        );
    }

    private Integer calculateQuantity(
            TradeAction action,
            BigDecimal price,
            User user,
            String symbol,
            double confidence) {

        if (action == TradeAction.BUY) {
            // Инвестируем процент в зависимости от уверенности
            // 50% → 5%, 60% → 15%, 70% → 20%, 80% → 25%, 90%+ → 30%
            double investmentPercent = Math.min(0.30, Math.max(0.05, (confidence - 0.5) * 0.5 + 0.10));

            BigDecimal available = user.getCash().multiply(BigDecimal.valueOf(investmentPercent));
            int quantity = available.divide(price, RoundingMode.DOWN).intValue();

            log.info("💰 BUY: уверенность={}, инвестируем {}% = {} акций",
                    String.format("%.1f%%", confidence * 100),
                    String.format("%.1f", investmentPercent * 100),
                    quantity);

            return quantity > 0 ? quantity : (user.getCash().compareTo(price) >= 0 ? 1 : 0);

        } else if (action == TradeAction.SELL) {
            Portfolio portfolio = portfolioRepository
                    .findByUserAndSymbol(user, symbol)
                    .orElse(null);

            if (portfolio == null || portfolio.getQuantity() <= 0) {
                log.warn("⚠️ Нет позиции для продажи {}", symbol);
                return 0;
            }

            // Продаем процент в зависимости от уверенности
            // 50% → 20%, 60% → 30%, 70% → 40%, 80% → 60%, 90%+ → 80%
            double sellPercent = Math.min(0.80, Math.max(0.20, (confidence - 0.5) * 1.2 + 0.20));
            int quantityToSell = (int)(portfolio.getQuantity() * sellPercent);
            quantityToSell = Math.max(1, Math.min(quantityToSell, portfolio.getQuantity()));

            log.info("📉 SELL: уверенность={}, продаем {}% = {} из {} акций",
                    String.format("%.1f%%", confidence * 100),
                    String.format("%.1f", sellPercent * 100),
                    quantityToSell,
                    portfolio.getQuantity());

            return quantityToSell;
        }

        return 0;
    }

    private TradeDecision getFallbackDecision(MarketUpdate marketUpdate, User user) {
        log.warn("⚠️ Используем запасную стратегию принятия решений");

        double randomValue = random.nextDouble();
        TradeAction action;
        String reason;
        double confidence;

        if (randomValue < 0.3) {
            action = TradeAction.BUY;
            reason = "Запасная стратегия: технические индикаторы указывают на возможный рост";
            confidence = 0.6 + random.nextDouble() * 0.15;
        } else if (randomValue < 0.6) {
            action = TradeAction.SELL;
            reason = "Запасная стратегия: фиксация прибыли по техническому сигналу";
            confidence = 0.6 + random.nextDouble() * 0.15;
        } else {
            action = TradeAction.HOLD;
            reason = "Запасная стратегия: ожидание более четкого сигнала";
            confidence = 0.5 + random.nextDouble() * 0.2;
        }

        BigDecimal confidenceBD = BigDecimal.valueOf(confidence * 100)
                .setScale(2, RoundingMode.HALF_UP);

        Integer quantity = calculateQuantity(
                action,
                marketUpdate.getCurrentPrice(),
                user,
                marketUpdate.getSymbol(),
                confidence
        );

        return new TradeDecision(action, confidenceBD, reason, quantity);
    }

    private void updateStatistics(TradeAction action) {
        switch (action) {
            case BUY -> buyCount.incrementAndGet();
            case SELL -> sellCount.incrementAndGet();
            case HOLD -> holdCount.incrementAndGet();
        }

        int buy = buyCount.get();
        int sell = sellCount.get();
        int hold = holdCount.get();
        int total = buy + sell + hold;

        if (total > 0) {
            log.info("📊 Статистика решений: BUY={} ({}%), SELL={} ({}%), HOLD={} ({}%)",
                    buy, (buy * 100 / total),
                    sell, (sell * 100 / total),
                    hold, (hold * 100 / total));
        }
    }
}