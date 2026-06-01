package uz.barakat.market.service;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.sms.EskizSmsService;
import uz.barakat.market.telegram.CustomerBotProperties;
import uz.barakat.market.telegram.TelegramBotApi;

/**
 * Routes a customer-facing message to the best available channel:
 * the linked customer Telegram bot first (free, instant), otherwise an
 * SMS to the customer's phone. Returns which channel was used so callers
 * can report it back to the cashier. Delivery is best-effort and never
 * throws — it must not break the sale or ledger flow that triggers it.
 */
@Service
public class CustomerNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CustomerNotificationService.class);

    /** Which channel a message went out on. */
    public enum Channel { TELEGRAM, SMS, NONE }

    private final CustomerBotProperties botProperties;
    private final TelegramBotApi botApi;
    private final EskizSmsService sms;

    public CustomerNotificationService(CustomerBotProperties botProperties,
                                       TelegramBotApi botApi,
                                       EskizSmsService sms) {
        this.botProperties = botProperties;
        this.botApi = botApi;
        this.sms = sms;
    }

    /** True when at least one channel is configured (Telegram bot or SMS). */
    public boolean anyChannelAvailable() {
        return botProperties.isUsable() || sms.isUsable();
    }

    /**
     * Sends {@code text} to the customer over the best available channel.
     * Telegram (if the customer linked the bot) is preferred; otherwise SMS
     * (if the customer has a phone and the gateway is configured).
     */
    public Channel notify(Customer customer, String text) {
        if (customer == null || text == null || text.isBlank()) {
            return Channel.NONE;
        }
        if (botProperties.isUsable() && customer.getTelegramChatId() != null) {
            long chatId = customer.getTelegramChatId();
            CompletableFuture.runAsync(() -> botApi.sendMessage(chatId, text));
            return Channel.TELEGRAM;
        }
        if (sms.isUsable() && customer.getPhone() != null && !customer.getPhone().isBlank()) {
            // Run the (network) send off the request thread; the cashier only
            // needs to know SMS was the chosen channel, not await delivery.
            String phone = customer.getPhone();
            CompletableFuture.runAsync(() -> sms.send(phone, text));
            return Channel.SMS;
        }
        log.info("No channel for customer {} (telegram bot usable={}, sms usable={}, "
                        + "chatId={}, phone={})",
                customer.getId(), botProperties.isUsable(), sms.isUsable(),
                customer.getTelegramChatId(), customer.getPhone());
        return Channel.NONE;
    }
}
