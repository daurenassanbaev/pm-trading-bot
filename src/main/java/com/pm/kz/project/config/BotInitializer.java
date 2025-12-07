package com.pm.kz.project.config;

import com.pm.kz.project.bot.TelegramTradingBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotInitializer {
    private final TelegramTradingBot bot;
    
    @EventListener(ContextRefreshedEvent.class)
    public void init() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            botsApi.registerBot(bot);
            log.info("✅ Telegram Bot успешно запущен!");
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка регистрации бота", e);
            throw e;
        }
    }
}