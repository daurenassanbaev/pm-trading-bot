package com.pm.kz.project.agents;

import com.pm.kz.project.dto.FinnhubResponse;
import com.pm.kz.project.dto.MarketUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class MarketMonitorAgent {

    private final RestTemplate restTemplate;

    @Value("${finnhub.api.key}")
    private String apiKey;

    public MarketUpdate fetchMarketData(String symbol) {
        log.info("🔍 MarketMonitorAgent: Получение данных для {}", symbol);

        try {
            BigDecimal price = fetchFromFinnhub(symbol);

            MarketUpdate update = new MarketUpdate(
                    symbol,
                    price,
                    LocalDateTime.now(),
                    "FINNHUB_API"
            );

            log.info("✅ Текущая цена {}: ${}", symbol, price);
            return update;

        } catch (Exception e) {
            log.error("❌ Ошибка получения данных для {}: {}", symbol, e.getMessage());
            log.warn("⚠️ Используем симулированную цену");
            return getSimulatedPrice(symbol);
        }
    }

    private BigDecimal fetchFromFinnhub(String symbol) {
        try {
            String url = String.format("https://finnhub.io/api/v1/quote?symbol=%s&token=%s", symbol, apiKey);

            FinnhubResponse response = restTemplate.getForObject(url, FinnhubResponse.class);

            if (response == null) {
                throw new RuntimeException("Пустой ответ от Finnhub API");
            }

            // Проверяем наличие цены
            if (response.getCurrentPrice() == null) {
                log.error("Ответ от API: {}", response);
                throw new RuntimeException("Тикер не найден или API вернул ошибку");
            }

            double currentPrice = response.getCurrentPrice();

            // Проверяем что цена валидна
            if (currentPrice <= 0) {
                throw new RuntimeException("Невалидная цена: " + currentPrice);
            }

            log.debug("📊 Данные от Finnhub: текущая={}, открытие={}, макс={}, мин={}",
                    currentPrice,
                    response.getOpenPrice(),
                    response.getHighPrice(),
                    response.getLowPrice());

            return BigDecimal.valueOf(currentPrice)
                    .setScale(2, RoundingMode.HALF_UP);

        } catch (Exception e) {
            log.error("Ошибка Finnhub API для {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Не удалось получить цену для " + symbol, e);
        }
    }

    // Запасной вариант - симуляция (если API недоступен)
    private MarketUpdate getSimulatedPrice(String symbol) {
        log.warn("🎲 Генерируем симулированную цену для {}", symbol);

        BigDecimal simulatedPrice = BigDecimal.valueOf(100 + Math.random() * 400)
                .setScale(2, RoundingMode.HALF_UP);

        return new MarketUpdate(
                symbol,
                simulatedPrice,
                LocalDateTime.now(),
                "SIMULATED_API"
        );
    }
}