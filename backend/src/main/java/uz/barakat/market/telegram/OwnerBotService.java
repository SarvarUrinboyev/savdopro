package uz.barakat.market.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import uz.barakat.market.dto.DashboardResponse;
import uz.barakat.market.dto.ProductResponse;
import uz.barakat.market.dto.SalesSummary;
import uz.barakat.market.service.DashboardService;
import uz.barakat.market.service.GlobalScope;
import uz.barakat.market.service.MoneyFormat;
import uz.barakat.market.service.ProductService;
import uz.barakat.market.service.ReportService;

/**
 * Interactive owner bot — lets the owner pull live numbers from Telegram on
 * the SAME bot that already pushes the daily reports. Long-polls the owner
 * bot token and answers a few commands:
 * <ul>
 *   <li>{@code /bugun} — today's sales, profit, cash, debt</li>
 *   <li>{@code /kam_qoldiq} — products at/below their low-stock threshold</li>
 *   <li>{@code /qarzdorlar} — total customer debt</li>
 *   <li>{@code /help} — the command list</li>
 * </ul>
 *
 * <p><b>Security:</b> replies ONLY to chat ids already whitelisted in
 * {@code telegram.chat-ids} (the owner). Any other chat is ignored, so the
 * shop's figures can't leak to a stranger who finds the bot.
 *
 * <p>A no-op when the owner bot isn't configured. Uses long-polling (no
 * webhook) on the owner token; the daily-report sender ({@link TelegramService})
 * only ever <em>sends</em>, so there's no getUpdates conflict.
 */
@Service
public class OwnerBotService {

    private static final Logger log = LoggerFactory.getLogger(OwnerBotService.class);
    private static final String API = "https://api.telegram.org/bot";
    private static final int POLL_TIMEOUT = 50;

    private final TelegramProperties properties;
    private final DashboardService dashboard;
    private final ReportService reports;
    private final ProductService products;
    private final GlobalScope globalScope;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private volatile boolean running = true;
    private volatile long offset = 0;
    private Thread poller;
    private Set<String> ownerChats = Set.of();

    public OwnerBotService(TelegramProperties properties, DashboardService dashboard,
                           ReportService reports, ProductService products,
                           GlobalScope globalScope, ObjectMapper mapper) {
        this.properties = properties;
        this.dashboard = dashboard;
        this.reports = reports;
        this.products = products;
        this.globalScope = globalScope;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isUsable()) {
            log.info("Owner bot commands disabled (Telegram bot not configured)");
            return;
        }
        ownerChats = properties.chatIds().stream()
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        poller = new Thread(this::pollLoop, "owner-bot-poll");
        poller.setDaemon(true);
        poller.start();
        log.info("Owner bot command listener started for {} chat(s)", ownerChats.size());
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (poller != null) {
            poller.interrupt();
        }
    }

    private void pollLoop() {
        int fails = 0;
        while (running) {
            try {
                JsonNode updates = getUpdates(offset);
                if (updates != null && updates.isArray()) {
                    for (JsonNode u : updates) {
                        offset = Math.max(offset, u.path("update_id").asLong() + 1);
                        handle(u);
                    }
                }
                fails = 0;
            } catch (Exception ex) {
                fails++;
                if (fails <= 3) {
                    log.warn("Owner bot poll error ({}x): {}", fails, ex.toString());
                } else if (fails % 20 == 0) {
                    log.warn("Owner bot still failing ({} consecutive)", fails);
                }
                sleepBackoff(fails);
            }
        }
    }

    private void handle(JsonNode update) {
        try {
            JsonNode msg = update.path("message");
            if (msg.isMissingNode()) {
                return;
            }
            String chatId = msg.path("chat").path("id").asText("");
            // Only the owner's whitelisted chats may read the shop's numbers.
            if (chatId.isEmpty() || !ownerChats.contains(chatId)) {
                return;
            }
            String text = msg.path("text").asText("").trim();
            if (text.isEmpty()) {
                return;
            }
            String cmd = text.split("\\s+")[0].toLowerCase();
            int at = cmd.indexOf('@'); // strip /bugun@MyBot
            if (at > 0) {
                cmd = cmd.substring(0, at);
            }
            final String command = cmd;
            // Run inside an all-shops tenant scope so native-query reports
            // (salesFor) aren't empty off-request — see GlobalScope.
            String reply = globalScope.call(() -> switch (command) {
                case "/start", "/help", "/yordam" -> helpText();
                case "/bugun", "/today" -> todayText();
                case "/kam_qoldiq", "/kam" -> lowStockText();
                case "/qarzdorlar", "/qarz" -> debtorsText();
                default -> null;
            });
            if (reply != null) {
                send(chatId, reply);
            }
        } catch (Exception ex) {
            log.warn("Owner bot handler error: {}", ex.toString());
        }
    }

    private String helpText() {
        return "🤖 SavdoPRO — Egasi boti\n\n"
                + "/bugun — bugungi savdo, foyda, naqd, qarz\n"
                + "/kam_qoldiq — tugayotgan mahsulotlar\n"
                + "/qarzdorlar — umumiy mijozlar qarzi\n"
                + "/help — buyruqlar ro'yxati";
    }

    private String todayText() {
        DashboardResponse d = dashboard.today();
        SalesSummary s = reports.salesFor(LocalDate.now());
        return "📊 BUGUN — " + LocalDate.now() + "\n\n"
                + "🛒 Savdo: " + MoneyFormat.usd(s.net()) + "\n"
                + "📈 Foyda (taxminiy): " + MoneyFormat.usd(s.profit()) + "\n"
                + "🧾 Cheklar: " + s.count() + "\n\n"
                + "💵 Naqd: " + MoneyFormat.usd(d.todayNaqd()) + "\n"
                + "💳 Karta: " + MoneyFormat.usd(d.todayKarta()) + "\n"
                + "💸 Xarajat: " + MoneyFormat.usd(d.todayExpenseTotal()) + "\n"
                + "📒 Umumiy qarz: " + MoneyFormat.usd(d.totalDebt());
    }

    private String lowStockText() {
        List<ProductResponse> low = products.lowStock();
        if (low.isEmpty()) {
            return "✅ Kam qolgan mahsulot yo'q.";
        }
        StringBuilder sb = new StringBuilder("⚠️ Kam qoldi (" + low.size() + "):\n\n");
        low.stream().limit(30).forEach(p ->
                sb.append("• ").append(p.name()).append(" — ").append(p.quantity()).append(" dona\n"));
        if (low.size() > 30) {
            sb.append("… va yana ").append(low.size() - 30).append(" ta");
        }
        return sb.toString();
    }

    private String debtorsText() {
        DashboardResponse d = dashboard.today();
        return "📒 Umumiy mijozlar qarzi: " + MoneyFormat.usd(d.totalDebt())
                + "\n\nTo'liq ro'yxat — ilovadagi \"Mijozlar\" bo'limida.";
    }

    // ----------------------------------------------------- Telegram I/O (owner token)

    private JsonNode getUpdates(long off) throws Exception {
        String url = API + properties.botToken() + "/getUpdates?offset=" + off
                + "&timeout=" + POLL_TIMEOUT;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(POLL_TIMEOUT + 15L)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            return null;
        }
        return mapper.readTree(resp.body()).path("result");
    }

    private void send(String chatId, String text) {
        try {
            String url = API + properties.botToken() + "/sendMessage";
            String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            log.warn("Owner bot send failed: {}", ex.toString());
        }
    }

    private static void sleepBackoff(int fails) {
        long ms = Math.min(60_000L, 3_000L * (1L << Math.min(fails - 1, 5)));
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
