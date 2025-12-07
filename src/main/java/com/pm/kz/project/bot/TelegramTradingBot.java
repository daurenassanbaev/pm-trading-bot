package com.pm.kz.project.bot;

import com.pm.kz.project.dto.ExecutionResult;
import com.pm.kz.project.entity.Portfolio;
import com.pm.kz.project.entity.Trade;
import com.pm.kz.project.entity.User;
import com.pm.kz.project.repository.PortfolioRepository;
import com.pm.kz.project.repository.TradeRepository;
import com.pm.kz.project.repository.UserRepository;
import com.pm.kz.project.service.TradingCoordinator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class TelegramTradingBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;
    private final TradingCoordinator tradingCoordinator;

    // Список поддерживаемых компаний
    private static final List<String> SUPPORTED_SYMBOLS = Arrays.asList(
            "AAPL", "MSFT", "GOOGL", "TSLA", "NVDA"
    );

    public TelegramTradingBot(
            UserRepository userRepository,
            PortfolioRepository portfolioRepository,
            TradeRepository tradeRepository,
            TradingCoordinator tradingCoordinator) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.tradingCoordinator = tradingCoordinator;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            String chatId = update.getMessage().getChatId().toString();
            String telegramId = update.getMessage().getFrom().getId().toString();
            String username = update.getMessage().getFrom().getUserName();

            log.info("Получено сообщение: {} от {}", messageText, telegramId);

            if (messageText.startsWith("/start") || messageText.equals("🏠 Старт")) {
                handleStart(chatId, telegramId, username);
            } else if (messageText.startsWith("/portfolio") || messageText.equals("📊 Портфель")) {
                handlePortfolio(chatId, telegramId);
            } else if (messageText.startsWith("/cash") || messageText.equals("💰 Баланс")) {
                handleCash(chatId, telegramId);
            } else if (messageText.startsWith("/run all") || messageText.equals("🔥 Торговать всеми")) {
                handleRunAll(chatId, telegramId);
            } else if (messageText.startsWith("/run") || messageText.equals("🚀 Торговать")) {
                handleRunPrompt(chatId, telegramId);
            } else if (messageText.startsWith("/history") || messageText.equals("📜 История")) {
                handleHistory(chatId, telegramId);
            } else if (messageText.startsWith("/stats") || messageText.equals("📈 Статистика")) {
                handleStats(chatId, telegramId);
            } else if (messageText.startsWith("/commands") || messageText.equals("ℹ️ Команды")) {
                handleCommands(chatId, telegramId);
            } else if (messageText.matches("^[A-Z]{1,5}$")) {
                // Если пользователь ввел тикер (1-5 заглавных букв)
                handleRun(chatId, telegramId, messageText);
            } else {
                sendMessageWithKeyboard(chatId, "❓ Неизвестная команда. Используйте кнопки ниже или команды.");
            }
        }
    }

    private void handleStart(String chatId, String telegramId, String username) {
        User user = userRepository.findByTelegramId(telegramId).orElse(null);

        if (user == null) {
            user = new User();
            user.setTelegramId(telegramId);
            user.setUsername(username);
            user.setCash(BigDecimal.valueOf(10000.00));
            userRepository.save(user);

            sendMessageWithKeyboard(chatId,
                    "🎉 Добро пожаловать в AI Trading Bot!\n\n" +
                            "💰 Начальный баланс: $10,000.00\n\n" +
                            "🤖 Поддерживаемые компании:\n" +
                            "• AAPL (Apple)\n" +
                            "• MSFT (Microsoft)\n" +
                            "• GOOGL (Google)\n" +
                            "• TSLA (Tesla)\n" +
                            "• NVDA (NVIDIA)\n\n" +
                            "Используйте кнопки для управления или команды:\n" +
                            "/portfolio - показать портфель\n" +
                            "/cash - показать баланс\n" +
                            "/run TICKER - торговать одной\n" +
                            "/run all - торговать всеми!\n" +
                            "/history - история сделок\n" +
                            "/stats - статистика");
        } else {
            sendMessageWithKeyboard(chatId, "👋 С возвращением! Ваш баланс: $" + user.getCash());
        }
    }

    private void handlePortfolio(String chatId, String telegramId) {
        User user = userRepository.findByTelegramId(telegramId).orElse(null);

        if (user == null) {
            sendMessageWithKeyboard(chatId, "❌ Сначала используйте /start");
            return;
        }

        List<Portfolio> positions = portfolioRepository.findByUser(user);

        if (positions.isEmpty()) {
            sendMessageWithKeyboard(chatId, "📊 Ваш портфель пуст\n\nИспользуйте /run TICKER для начала торговли");
            return;
        }

        StringBuilder response = new StringBuilder("📊 Ваш портфель:\n\n");
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Portfolio p : positions) {
            if (p.getQuantity() > 0) {
                BigDecimal positionValue = p.getAvgPrice().multiply(BigDecimal.valueOf(p.getQuantity()));
                totalValue = totalValue.add(positionValue);

                response.append(String.format(
                        "🔹 %s: %d акций\n" +
                                "   Средняя цена: $%s\n" +
                                "   Стоимость: $%s\n\n",
                        p.getSymbol(),
                        p.getQuantity(),
                        p.getAvgPrice(),
                        positionValue
                ));
            }
        }

        response.append(String.format("💼 Общая стоимость портфеля: $%s\n", totalValue));
        response.append(String.format("💰 Наличные: $%s\n", user.getCash()));
        response.append(String.format("📊 Всего активов: $%s", totalValue.add(user.getCash())));

        sendMessageWithKeyboard(chatId, response.toString());
    }

    private void handleCash(String chatId, String telegramId) {
        User user = userRepository.findByTelegramId(telegramId).orElse(null);

        if (user == null) {
            sendMessageWithKeyboard(chatId, "❌ Сначала используйте /start");
            return;
        }

        sendMessageWithKeyboard(chatId, "💰 Ваш баланс: $" + user.getCash());
    }

    private void handleRunPrompt(String chatId, String telegramId) {
        sendMessageWithKeyboard(chatId,
                "🚀 Введите тикер акции для торговли\n\n" +
                        "Поддерживаемые тикеры:\n" +
                        "• AAPL (Apple)\n" +
                        "• MSFT (Microsoft)\n" +
                        "• GOOGL (Google)\n" +
                        "• TSLA (Tesla)\n" +
                        "• NVDA (NVIDIA)\n\n" +
                        "Просто введите тикер (например: AAPL)\n" +
                        "Или используйте /run all для торговли всеми компаниями сразу!");
    }

    private void handleRun(String chatId, String telegramId, String symbol) {
        symbol = symbol.toUpperCase();

        if (!SUPPORTED_SYMBOLS.contains(symbol)) {
            sendMessageWithKeyboard(chatId,
                    "⚠️ Тикер " + symbol + " не поддерживается.\n\n" +
                            "Доступные тикеры: " + String.join(", ", SUPPORTED_SYMBOLS));
            return;
        }

        sendMessageWithKeyboard(chatId, "🚀 Запуск торгового цикла для " + symbol + "...\n");

        try {
            ExecutionResult result = tradingCoordinator.runCycle(telegramId, symbol);

            StringBuilder response = new StringBuilder();
            response.append("━━━━━━━━━━━━━━━━━━━━\n");
            response.append("🔮 Результат для ").append(symbol).append("\n\n");
            response.append(result.getMessage()).append("\n\n");

            if (result.isSuccess()) {
                response.append("💵 Новый баланс: $").append(result.getNewCashBalance()).append("\n");
                if (result.getPortfolioUpdate() != null) {
                    response.append("📈 ").append(result.getPortfolioUpdate()).append("\n");
                }
            }

            response.append("━━━━━━━━━━━━━━━━━━━━\n");

            sendMessageWithKeyboard(chatId, response.toString());

        } catch (Exception e) {
            sendMessageWithKeyboard(chatId, "❌ Ошибка для " + symbol + ": " + e.getMessage());
            log.error("Ошибка при выполнении торгового цикла для {}", symbol, e);
        }
    }

    private void handleRunAll(String chatId, String telegramId) {
        sendMessageWithKeyboard(chatId,
                "🔥 Запуск торговли по всем 5 компаниям!\n" +
                        "Подождите, это займет несколько секунд...\n");

        StringBuilder summaryResponse = new StringBuilder();
        summaryResponse.append("━━━━━━━━━━━━━━━━━━━━\n");
        summaryResponse.append("🔥 РЕЗУЛЬТАТЫ ТОРГОВЛИ ПО ВСЕМ КОМПАНИЯМ\n");
        summaryResponse.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        int successCount = 0;
        int failCount = 0;

        for (String symbol : SUPPORTED_SYMBOLS) {
            try {
                log.info("Торгуем {}", symbol);
                ExecutionResult result = tradingCoordinator.runCycle(telegramId, symbol);

                String actionEmoji = getActionEmoji(result.getMessage());
                summaryResponse.append(actionEmoji).append(" ").append(symbol).append(": ");

                if (result.isSuccess()) {
                    // Извлекаем действие из сообщения
                    String action = extractAction(result.getMessage());
                    summaryResponse.append(action).append("\n");
                    successCount++;
                } else {
                    summaryResponse.append("ОШИБКА\n");
                    failCount++;
                }

                // Небольшая задержка между запросами
                Thread.sleep(500);

            } catch (Exception e) {
                summaryResponse.append("❌ ").append(symbol).append(": ОШИБКА\n");
                failCount++;
                log.error("Ошибка при торговле {}", symbol, e);
            }
        }

        summaryResponse.append("\n━━━━━━━━━━━━━━━━━━━━\n");
        summaryResponse.append(String.format("✅ Успешно: %d | ❌ Ошибок: %d\n", successCount, failCount));

        // Получаем обновленный баланс
        User user = userRepository.findByTelegramId(telegramId).orElse(null);
        if (user != null) {
            summaryResponse.append(String.format("💰 Баланс: $%s\n", user.getCash()));
        }

        summaryResponse.append("\nИспользуйте /portfolio для просмотра портфеля");

        sendMessageWithKeyboard(chatId, summaryResponse.toString());
    }

    private String extractAction(String message) {
        if (message.contains("HOLD") || message.contains("💤")) {
            return "HOLD";
        } else if (message.contains("Куплено") || message.contains("BUY")) {
            return "BUY";
        } else if (message.contains("Продано") || message.contains("SELL")) {
            return "SELL";
        }
        return "UNKNOWN";
    }

    private String getActionEmoji(String message) {
        if (message.contains("HOLD") || message.contains("💤")) {
            return "⚪";
        } else if (message.contains("Куплено") || message.contains("BUY")) {
            return "🟢";
        } else if (message.contains("Продано") || message.contains("SELL")) {
            return "🔴";
        }
        return "⚫";
    }

    private void handleHistory(String chatId, String telegramId) {
        User user = userRepository.findByTelegramId(telegramId).orElse(null);

        if (user == null) {
            sendMessageWithKeyboard(chatId, "❌ Сначала используйте /start");
            return;
        }

        List<Trade> trades = tradeRepository.findByUserOrderByExecutedAtDesc(user);

        if (trades.isEmpty()) {
            sendMessageWithKeyboard(chatId, "📜 История сделок пуста");
            return;
        }

        StringBuilder response = new StringBuilder("📜 История сделок (последние 10):\n\n");

        trades.stream().limit(10).forEach(trade -> {
            String emoji = switch (trade.getAction()) {
                case BUY -> "🟢";
                case SELL -> "🔴";
                case HOLD -> "⚪";
            };

            response.append(String.format(
                    "%s %s %s\n" +
                            "   Количество: %d, Цена: $%s\n" +
                            "   Уверенность: %s%%\n" +
                            "   %s\n\n",
                    emoji,
                    trade.getAction(),
                    trade.getSymbol(),
                    trade.getQuantity(),
                    trade.getPrice(),
                    trade.getConfidence(),
                    trade.getExecutedAt().toString()
            ));
        });

        sendMessageWithKeyboard(chatId, response.toString());
    }

    private void handleStats(String chatId, String telegramId) {
        User user = userRepository.findByTelegramId(telegramId).orElse(null);

        if (user == null) {
            sendMessageWithKeyboard(chatId, "❌ Сначала используйте /start");
            return;
        }

        List<Trade> trades = tradeRepository.findByUserOrderByExecutedAtDesc(user);

        if (trades.isEmpty()) {
            sendMessageWithKeyboard(chatId, "📈 Пока нет статистики. Начните торговать!");
            return;
        }

        long buyCount = trades.stream().filter(t -> t.getAction() == com.pm.kz.project.entity.TradeAction.BUY).count();
        long sellCount = trades.stream().filter(t -> t.getAction() == com.pm.kz.project.entity.TradeAction.SELL).count();
        long holdCount = trades.stream().filter(t -> t.getAction() == com.pm.kz.project.entity.TradeAction.HOLD).count();
        long total = buyCount + sellCount + holdCount;

        StringBuilder response = new StringBuilder("📈 Ваша статистика:\n\n");
        response.append(String.format("Всего решений: %d\n", total));
        response.append(String.format("🟢 BUY: %d (%.1f%%)\n", buyCount, (buyCount * 100.0 / total)));
        response.append(String.format("🔴 SELL: %d (%.1f%%)\n", sellCount, (sellCount * 100.0 / total)));
        response.append(String.format("⚪ HOLD: %d (%.1f%%)\n\n", holdCount, (holdCount * 100.0 / total)));

        // Статистика по компаниям
        response.append("📊 По компаниям:\n");
        for (String symbol : SUPPORTED_SYMBOLS) {
            long symbolTrades = trades.stream().filter(t -> t.getSymbol().equals(symbol)).count();
            if (symbolTrades > 0) {
                response.append(String.format("  %s: %d сделок\n", symbol, symbolTrades));
            }
        }

        sendMessageWithKeyboard(chatId, response.toString());
    }

    private void handleCommands(String chatId, String telegramId) {
        sendMessageWithKeyboard(chatId,
                "ℹ️ Доступные команды:\n\n" +
                        "📊 /portfolio - показать портфель\n" +
                        "💰 /cash - показать баланс\n" +
                        "🚀 /run TICKER - торговать одной компанией\n" +
                        "🔥 /run all - торговать всеми компаниями!\n" +
                        "📜 /history - последние 10 сделок\n" +
                        "📈 /stats - статистика по сделкам\n\n" +
                        "Поддерживаемые тикеры:\n" +
                        "AAPL, MSFT, GOOGL, TSLA, NVDA");
    }

    private void sendMessageWithKeyboard(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(createMainKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения", e);
        }
    }

    private ReplyKeyboardMarkup createMainKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        // Первая строка
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📊 Портфель"));
        row1.add(new KeyboardButton("💰 Баланс"));
        keyboard.add(row1);

        // Вторая строка
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🚀 Торговать"));
        row2.add(new KeyboardButton("🔥 Торговать всеми"));
        keyboard.add(row2);

        // Третья строка
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("📜 История"));
        row3.add(new KeyboardButton("📈 Статистика"));
        keyboard.add(row3);

        // Четвертая строка
        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton("ℹ️ Команды"));
        keyboard.add(row4);

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        return keyboardMarkup;
    }
}