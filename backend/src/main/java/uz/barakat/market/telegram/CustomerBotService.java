package uz.barakat.market.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.CustomerTxType;
import uz.barakat.market.dto.ExchangeRateResponse;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.CustomerTransactionRepository;
import uz.barakat.market.service.ExchangeRateService;
import uz.barakat.market.service.MoneyFormat;
import uz.barakat.market.service.ReportPdfRenderer;

/**
 * Customer self-service Telegram bot. Customers send their phone number
 * (only via Telegram's verified "share contact" button, so each person
 * can see only their own data), and the bot replies with their debt and
 * the goods they have taken.
 */
@Service
public class CustomerBotService {

    private static final Logger log = LoggerFactory.getLogger(CustomerBotService.class);
    private static final int POLL_TIMEOUT = 25;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CustomerBotProperties properties;
    private final TelegramBotApi api;
    private final CustomerRepository customers;
    private final CustomerTransactionRepository transactions;
    private final ExchangeRateService exchangeRate;
    private final ReportPdfRenderer pdf;

    private volatile boolean running;
    private Thread poller;
    private long offset;

    public CustomerBotService(CustomerBotProperties properties, TelegramBotApi api,
                              CustomerRepository customers,
                              CustomerTransactionRepository transactions,
                              ExchangeRateService exchangeRate,
                              ReportPdfRenderer pdf) {
        this.properties = properties;
        this.api = api;
        this.customers = customers;
        this.transactions = transactions;
        this.exchangeRate = exchangeRate;
        this.pdf = pdf;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isUsable()) {
            log.info("Customer Telegram bot is not configured - not started");
            return;
        }
        running = true;
        poller = new Thread(this::pollLoop, "customer-bot");
        poller.setDaemon(true);
        poller.start();
        log.info("Customer Telegram bot started");
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (poller != null) {
            poller.interrupt();
        }
    }

    private void pollLoop() {
        int consecutiveFailures = 0;
        while (running) {
            try {
                JsonNode updates = api.getUpdates(offset, POLL_TIMEOUT);
                if (updates != null && updates.isArray()) {
                    for (JsonNode update : updates) {
                        offset = Math.max(offset, update.path("update_id").asLong() + 1);
                        handle(update);
                    }
                }
                consecutiveFailures = 0;
            } catch (Exception ex) {
                consecutiveFailures += 1;
                // Log loudly on the first few errors, then quiet down so the log
                // doesn't get spammed when Telegram is unreachable for hours.
                if (consecutiveFailures <= 3) {
                    log.warn("Customer bot poll error ({}x): {}",
                            consecutiveFailures, ex.toString());
                } else if (consecutiveFailures % 20 == 0) {
                    log.warn("Customer bot still failing ({} consecutive errors)",
                            consecutiveFailures);
                }
                sleepWithBackoff(consecutiveFailures);
            }
        }
    }

    /** Exponential backoff up to ~60s after sustained failures. */
    private static void sleepWithBackoff(int consecutiveFailures) {
        long delayMs = Math.min(60_000L, 3_000L * (1L << Math.min(consecutiveFailures - 1, 5)));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void handle(JsonNode update) {
        try {
            if (update.has("message")) {
                handleMessage(update.get("message"));
            } else if (update.has("callback_query")) {
                handleCallback(update.get("callback_query"));
            }
        } catch (Exception ex) {
            log.warn("Customer bot handler error: {}", ex.toString());
        }
    }

    private void handleMessage(JsonNode message) {
        long chatId = message.path("chat").path("id").asLong();
        if (message.has("contact")) {
            JsonNode contact = message.get("contact");
            long contactUser = contact.path("user_id").asLong();
            long fromUser = message.path("from").path("id").asLong();
            // Only accept the sender's OWN verified contact.
            if (contactUser == 0 || contactUser != fromUser) {
                api.sendMessage(chatId,
                        "Iltimos, faqat O'ZINGIZNING raqamingizni pastdagi tugma orqali yuboring.",
                        contactKeyboard());
                return;
            }
            linkAndShow(chatId, contact.path("phone_number").asText());
            return;
        }
        String text = message.path("text").asText("").trim();
        if (text.startsWith("/start")) {
            api.sendMessage(chatId, welcomeText(), contactKeyboard());
            return;
        }
        if (text.equalsIgnoreCase("/balans") || text.equalsIgnoreCase("/qarz")) {
            Optional<Customer> linked = customers.findByTelegramChatId(chatId);
            if (linked.isEmpty()) {
                api.sendMessage(chatId,
                        "Avval tugma orqali telefon raqamingizni yuboring.",
                        contactKeyboard());
            } else {
                api.sendMessage(chatId, summaryText(linked.get()), rangeKeyboard());
            }
            return;
        }
        if (text.equalsIgnoreCase("/help") || text.equalsIgnoreCase("/yordam")) {
            api.sendMessage(chatId,
                    "BUYRUQLAR:\n" +
                    "/start  — boshlash\n" +
                    "/balans — qarzingiz va sotib olganlaringiz\n" +
                    "/help   — shu yordam\n\n" +
                    "Mahsulot narxlari va savollar uchun do'konga murojaat qiling.");
            return;
        }
        // Unknown text. If the customer is ALREADY linked (e.g. they typed an
        // owner-bot command like /bugun by mistake), just show their summary —
        // never make a linked customer re-share their phone.
        Optional<Customer> known = customers.findByTelegramChatId(chatId);
        if (known.isPresent()) {
            api.sendMessage(chatId,
                    "Bu buyruq mavjud emas. Quyidagidan foydalaning 👇\n\n"
                    + summaryText(known.get()),
                    rangeKeyboard());
        } else {
            api.sendMessage(chatId,
                    "Qarzingizni bilish uchun pastdagi \"📱 Telefon raqamni yuborish\" "
                    + "tugmasini bosing.\nYoki: /balans, /help",
                    contactKeyboard());
        }
    }

    private void handleCallback(JsonNode callback) {
        api.answerCallback(callback.path("id").asText());
        long chatId = callback.path("message").path("chat").path("id").asLong();
        String data = callback.path("data").asText("");
        Optional<Customer> found = customers.findByTelegramChatId(chatId);
        if (found.isEmpty()) {
            api.sendMessage(chatId, welcomeText(), contactKeyboard());
            return;
        }
        Customer customer = found.get();
        // Download a PDF ledger for the chosen period.
        if (data.startsWith("PDF_")) {
            sendLedgerPdf(chatId, customer, periodFrom(data), periodLabel(data));
            return;
        }
        // Otherwise show the period's goods on screen.
        api.sendMessage(chatId,
                goodsBlock(customer.getId(), periodFrom(data),
                        periodLabel(data) + " tovarlaringiz", 30, true),
                rangeKeyboard());
    }

    /** Period start for a RANGE / PDF callback (null = all time). */
    private static LocalDate periodFrom(String data) {
        return switch (data.replace("PDF_", "RANGE_")) {
            case "RANGE_30" -> LocalDate.now().minusDays(30);
            case "RANGE_MONTH" -> LocalDate.now().withDayOfMonth(1);
            case "RANGE_90" -> LocalDate.now().minusDays(90);
            default -> null;
        };
    }

    private static String periodLabel(String data) {
        return switch (data.replace("PDF_", "RANGE_")) {
            case "RANGE_30" -> "Oxirgi 30 kun";
            case "RANGE_MONTH" -> "Bu oy";
            case "RANGE_90" -> "Oxirgi 3 oy";
            default -> "Barcha";
        };
    }

    /**
     * Builds + sends a PDF ledger (purchases + payments + running balance) for
     * the period, so the customer can download/keep their own statement.
     */
    private void sendLedgerPdf(long chatId, Customer customer, LocalDate from, String label) {
        try {
            List<CustomerTransaction> all = transactions
                    .findByCustomerIdOrderByDateDescIdDesc(customer.getId());
            BigDecimal opening = BigDecimal.ZERO;
            for (CustomerTransaction tx : all) {
                if (from != null && tx.getDate().isBefore(from)) {
                    opening = applyTx(opening, tx);
                }
            }
            List<CustomerTransaction> period = all.stream()
                    .filter(tx -> from == null || !tx.getDate().isBefore(from))
                    .sorted(java.util.Comparator.comparing(CustomerTransaction::getDate)
                            .thenComparing(CustomerTransaction::getId))
                    .toList();
            BigDecimal running = opening;
            List<ReportPdfRenderer.LedgerRow> rows = new java.util.ArrayList<>();
            for (CustomerTransaction tx : period) {
                running = applyTx(running, tx);
                rows.add(new ReportPdfRenderer.LedgerRow(
                        tx.getDate(),
                        tx.getType() == CustomerTxType.GOODS ? "Tovar" : "To'lov",
                        tx.getDescription() == null ? "—" : tx.getDescription(),
                        tx.getAmount(), running));
            }
            byte[] bytes = pdf.renderCustomerLedger(
                    customer.getName(), customer.getPhone(), opening, rows, running);
            api.sendDocument(chatId, bytes, "hisobot-" + LocalDate.now() + ".pdf",
                    label + " — " + customer.getName());
        } catch (Exception ex) {
            log.warn("Customer ledger PDF failed for chat {}: {}", chatId, ex.toString());
            api.sendMessage(chatId, "Hisobotni tayyorlashda xatolik. Keyinroq urinib ko'ring.");
        }
    }

    private static BigDecimal applyTx(BigDecimal balance, CustomerTransaction tx) {
        return tx.getType() == CustomerTxType.GOODS
                ? balance.add(tx.getAmount())
                : balance.subtract(tx.getAmount());
    }

    private void linkAndShow(long chatId, String phone) {
        Optional<Customer> found = findByPhone(phone);
        if (found.isEmpty()) {
            api.sendMessage(chatId,
                    "❌ Telefon raqamingiz (" + phone + ") do'kon bazasida topilmadi.\n"
                    + "Iltimos, do'kon bilan bog'laning.");
            return;
        }
        Customer customer = found.get();
        customer.setTelegramChatId(chatId);
        customers.save(customer);
        api.sendMessage(chatId, summaryText(customer), rangeKeyboard());
    }

    // --------------------------------------------------------------- helpers

    private Optional<Customer> findByPhone(String rawPhone) {
        String target = normalizePhone(rawPhone);
        if (target.length() < 7) {
            return Optional.empty();
        }
        return customers.findAll().stream()
                .filter(c -> normalizePhone(c.getPhone()).equals(target))
                .findFirst();
    }

    /** Keeps digits only and compares on the last 9 (the Uzbek national number). */
    private static String normalizePhone(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D", "");
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
    }

    private BigDecimal balanceOf(Long customerId) {
        BigDecimal balance = BigDecimal.ZERO;
        for (CustomerTransaction tx
                : transactions.findByCustomerIdOrderByDateDescIdDesc(customerId)) {
            balance = tx.getType() == CustomerTxType.GOODS
                    ? balance.add(tx.getAmount())
                    : balance.subtract(tx.getAmount());
        }
        return balance;
    }

    private String summaryText(Customer customer) {
        BigDecimal balance = balanceOf(customer.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("Assalomu alaykum, ").append(customer.getName()).append("! 👋\n\n");
        int cmp = balance.compareTo(BigDecimal.ZERO);
        if (cmp > 0) {
            sb.append("💰 Sizning qarzingiz: ").append(MoneyFormat.usd(balance))
              .append(uzsSuffix(balance)).append('\n');
        } else if (cmp < 0) {
            sb.append("✅ Sizda ortiqcha to'lov bor: ")
              .append(MoneyFormat.usd(balance.negate())).append('\n');
        } else {
            sb.append("✅ Sizning qarzingiz yo'q. Rahmat!\n");
        }
        sb.append('\n')
          .append(goodsBlock(customer.getId(), null, "So'nggi tovarlaringiz", 5, false))
          .append("\n\nBatafsil ko'rish uchun pastdagi tugmalardan tanlang:");
        return sb.toString();
    }

    /** Lists the customer's GOODS lines (optionally from a date), newest first. */
    private String goodsBlock(Long customerId, LocalDate from, String title,
                              int limit, boolean withTotal) {
        List<CustomerTransaction> goods = transactions
                .findByCustomerIdOrderByDateDescIdDesc(customerId).stream()
                .filter(tx -> tx.getType() == CustomerTxType.GOODS)
                .filter(tx -> from == null || !tx.getDate().isBefore(from))
                .toList();
        StringBuilder sb = new StringBuilder("📦 ").append(title).append(":\n");
        if (goods.isEmpty()) {
            sb.append("  (bu davrda tovar yo'q)");
            return sb.toString();
        }
        BigDecimal total = BigDecimal.ZERO;
        int shown = 0;
        for (CustomerTransaction tx : goods) {
            total = total.add(tx.getAmount());
            if (shown < limit) {
                sb.append("• ").append(tx.getDate().format(DATE)).append(" — ")
                  .append(tx.getDescription() == null ? "tovar" : tx.getDescription())
                  .append(" — ").append(MoneyFormat.usd(tx.getAmount())).append('\n');
                shown++;
            }
        }
        if (goods.size() > limit) {
            sb.append("... va yana ").append(goods.size() - limit).append(" ta\n");
        }
        if (withTotal) {
            sb.append("Jami: ").append(MoneyFormat.usd(total));
        }
        return sb.toString().stripTrailing();
    }

    private String uzsSuffix(BigDecimal usd) {
        ExchangeRateResponse rate = exchangeRate.current();
        if (rate == null || !rate.available() || rate.rate() == null) {
            return "";
        }
        long uzs = usd.multiply(rate.rate()).longValue();
        return " (≈ " + groupDigits(uzs) + " so'm)";
    }

    private static String groupDigits(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            out.append(digits.charAt(i));
            if (++count % 3 == 0 && i > 0) {
                out.append(' ');
            }
        }
        return out.reverse().toString();
    }

    private static String welcomeText() {
        return "Assalomu alaykum! 🛍\n"
             + "Barakat do'koni — mijozlar xizmati.\n\n"
             + "Qarzingizni va olgan tovarlaringizni bilish uchun pastdagi tugma orqali "
             + "telefon raqamingizni yuboring. 📱\n\n"
             + "🔒 Maxfiylik: faqat o'z raqamingiz orqali, faqat o'z ma'lumotingizni ko'rasiz.";
    }

    private static Object contactKeyboard() {
        return Map.of(
                "keyboard", List.of(List.of(Map.of(
                        "text", "📱 Telefon raqamni yuborish", "request_contact", true))),
                "resize_keyboard", true,
                "one_time_keyboard", true);
    }

    private static Object rangeKeyboard() {
        return Map.of("inline_keyboard", List.of(
                List.of(Map.of("text", "📅 30 kun", "callback_data", "RANGE_30"),
                        Map.of("text", "📅 Bu oy", "callback_data", "RANGE_MONTH")),
                List.of(Map.of("text", "📅 3 oy", "callback_data", "RANGE_90"),
                        Map.of("text", "📋 Hammasi", "callback_data", "RANGE_ALL")),
                List.of(Map.of("text", "📄 PDF: 30 kun", "callback_data", "PDF_30"),
                        Map.of("text", "📄 PDF: hammasi", "callback_data", "PDF_ALL"))));
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
