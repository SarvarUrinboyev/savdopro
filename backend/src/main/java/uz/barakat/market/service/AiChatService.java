package uz.barakat.market.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.service.ai.AiProvider;
import uz.barakat.market.service.ai.GeminiProvider;
import uz.barakat.market.service.ai.OpenAiCompatProvider;

/**
 * Conversational analytics over the shop's data, backed by a configurable
 * provider chain (failover-on-error).
 *
 * <p>Default chain (configured in application-local.properties):
 *   <ol>
 *     <li><b>Gemini Flash</b> — Google, free tier, fast.</li>
 *     <li><b>NVIDIA DeepSeek V4 Flash</b> — free, OpenAI-compatible.</li>
 *     <li><b>NVIDIA Kimi K2.6</b> — free, larger MoE.</li>
 *     <li><b>OpenRouter (Claude Haiku)</b> — paid fallback.</li>
 *   </ol>
 *
 * <p>The router walks the chain in order. A provider is tried only if
 * its API key is configured; on any failure (timeout, 429 rate-limit,
 * 5xx) we silently fall through to the next. Every failure is logged at
 * {@code WARN} so an operator can spot a misconfigured key.
 *
 * <h2>Tool calling (agentic)</h2>
 * Instead of feeding the model one fixed snapshot, we let it pull the
 * exact data it needs. The system prompt advertises a small whitelist of
 * read-only, tenant-scoped tools (see {@link AiToolService}); to use one
 * the model replies with a single line {@code TOOL <name> {json-args}}.
 * We execute it, append the result, and loop — up to {@link #MAX_TOOL_STEPS}
 * times — until the model produces a final answer. This is a
 * provider-agnostic emulation of function-calling: it works uniformly
 * across every provider in the chain and the backend validates every call.
 *
 * <p>When every provider fails (or none are configured) we return the
 * raw KPI snapshot — useful as a degraded fallback so the UI never has
 * a fully blank screen.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** Hard cap on tool-call round-trips so a confused model can't loop forever. */
    private static final int MAX_TOOL_STEPS = 4;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Matches a tool request: {@code TOOL <name> {..json..}} (json optional)
     * at the START OF A LINE. Not anchored to the whole reply — some models
     * append a hallucinated answer after the TOOL line; we take the first
     * TOOL call and ignore the trailing text. {@code [^{}]*} keeps the args
     * to one flat JSON object (all our tool args are flat).
     */
    private static final Pattern TOOL_RE =
            Pattern.compile("(?im)^\\s*TOOL\\s+([a-zA-Z]+)\\s*(\\{[^{}]*})?");

    private static final String SYSTEM_PROMPT =
            "Sen — SavdoPRO POS tizimining yordamchisisan. Sen shu do'konning BARCHA "
            + "ma'lumotini bilasan: savdo, foyda, xarajat, kassa, mijozlar va ularning "
            + "qarzi, yetkazib beruvchilar, bizning qarzlarimiz, ombor qoldig'i, "
            + "yaroqlilik muddati va buyurtmalar. Bu ma'lumotlarni quyidagi ASBOBLAR "
            + "orqali olasan — savolga mos asbobni chaqir, keyin aniq raqamli javob ber. "
            + "Hech qachon raqam to'qima; faqat asbob qaytargan ma'lumotga asoslan. "
            + "Ma'lumot bo'lmasa to'g'ridan-to'g'ri ayt. Javob qisqa va faqat o'zbek tilida bo'lsin.";

    private final ReportService reports;
    private final AnalyticsService analytics;
    private final SaleRepository sales;
    private final ProductRepository products;
    private final AiToolService tools;
    private final List<AiProvider> chain;

    public AiChatService(
            ReportService reports,
            AnalyticsService analytics,
            SaleRepository sales,
            ProductRepository products,
            AiToolService tools,
            // chain order — change to reorder failover priority
            @Value("${ai.providers:gemini,nvidia-deepseek,nvidia-kimi,openrouter}") String chainOrder,
            // Per-provider keys + models. All optional; unset = skipped.
            @Value("${ai.gemini.key:${GEMINI_API_KEY:}}") String geminiKey,
            @Value("${ai.gemini.model:gemini-2.0-flash-exp}") String geminiModel,
            @Value("${ai.nvidia.deepseek.key:${NVIDIA_DEEPSEEK_KEY:}}") String nvDeepseekKey,
            @Value("${ai.nvidia.deepseek.model:deepseek-ai/deepseek-v4-flash}") String nvDeepseekModel,
            @Value("${ai.nvidia.kimi.key:${NVIDIA_KIMI_KEY:}}") String nvKimiKey,
            @Value("${ai.nvidia.kimi.model:moonshotai/kimi-k2.6}") String nvKimiModel,
            @Value("${ai.openrouter.key:${openrouter.api-key:${OPENROUTER_API_KEY:}}}") String openrouterKey,
            @Value("${ai.openrouter.model:${openrouter.model:anthropic/claude-3.5-haiku}}") String openrouterModel) {
        this.reports = reports;
        this.analytics = analytics;
        this.sales = sales;
        this.products = products;
        this.tools = tools;
        this.chain = buildChain(
                chainOrder,
                geminiKey, geminiModel,
                nvDeepseekKey, nvDeepseekModel,
                nvKimiKey, nvKimiModel,
                openrouterKey, openrouterModel);
        log.info("AI chain ({} ready of {} configured): {}",
                chain.stream().filter(AiProvider::isConfigured).count(),
                chain.size(),
                chain.stream().map(p -> p.name() + (p.isConfigured() ? "" : "[off]"))
                        .toList());
    }

    private static List<AiProvider> buildChain(
            String order,
            String geminiKey, String geminiModel,
            String nvDeepseekKey, String nvDeepseekModel,
            String nvKimiKey, String nvKimiModel,
            String openrouterKey, String openrouterModel) {

        String nvBase = "https://integrate.api.nvidia.com/v1";
        String openrouterBase = "https://openrouter.ai/api/v1";
        Map<String, java.util.function.Supplier<AiProvider>> registry = Map.of(
                "gemini", () -> new GeminiProvider(geminiKey, geminiModel),
                "nvidia-deepseek", () -> new OpenAiCompatProvider(
                        "nvidia-deepseek", nvBase, nvDeepseekKey, nvDeepseekModel),
                "nvidia-kimi", () -> new OpenAiCompatProvider(
                        "nvidia-kimi", nvBase, nvKimiKey, nvKimiModel),
                "openrouter", () -> new OpenAiCompatProvider(
                        "openrouter", openrouterBase, openrouterKey, openrouterModel,
                        0.2, 800,
                        Map.of("HTTP-Referer", "https://savdopro.uz",
                                "X-Title", "SavdoPRO AI Assistant"))
        );
        List<AiProvider> out = new ArrayList<>();
        for (String name : order.split(",")) {
            String key = name.trim().toLowerCase();
            var supplier = registry.get(key);
            if (supplier == null) {
                log.warn("AI chain: noma'lum provider nomi '{}', tashlandi", key);
                continue;
            }
            out.add(supplier.get());
        }
        return List.copyOf(out);
    }

    /** One prior exchange, sent by the client so follow-up questions keep context. */
    public record Turn(String question, String answer) { }

    public record ChatRequest(String question, List<Turn> history) { }

    public record ChatResponse(String answer, String snapshot, String provider) { }

    public ChatResponse ask(ChatRequest req) {
        String question = (req == null || req.question() == null) ? "" : req.question();
        List<Turn> history = (req == null || req.history() == null) ? List.of() : req.history();
        String snapshot = buildSnapshot();

        String system = SYSTEM_PROMPT
                + "\n\nBUGUNGI SANA: " + LocalDate.now() + "\n\n"
                + tools.catalog()
                + "\nQO'SHIMCHA ma'lumot kerak bo'lsa, FAQAT bitta qatorda shunday yoz:\n"
                + "TOOL <nom> {\"arg\":\"qiymat\"}\n"
                + "Boshqa hech narsa yozma. Ma'lumot yetarli bo'lsa — to'g'ridan-to'g'ri "
                + "yakuniy javobni yoz (TOOL'siz). Sanalar YYYY-MM-DD ko'rinishida.";

        StringBuilder ctx = new StringBuilder();
        if (!snapshot.isEmpty()) {
            ctx.append("KONTEKST (asosiy KPI):\n").append(snapshot).append('\n');
        }
        if (!history.isEmpty()) {
            ctx.append("OLDINGI SUHBAT:\n");
            for (Turn t : history) {
                if (t == null) continue;
                ctx.append("Savol: ").append(nz(t.question())).append('\n')
                   .append("Javob: ").append(nz(t.answer())).append('\n');
            }
            ctx.append('\n');
        }
        ctx.append("SAVOL: ").append(question);

        StringBuilder errs = new StringBuilder();
        String lastProvider = "none";

        for (int step = 0; step < MAX_TOOL_STEPS; step++) {
            // On the final allowed step, forbid further tool calls so the
            // model is forced to answer with whatever it has gathered.
            String sys = (step == MAX_TOOL_STEPS - 1)
                    ? system + "\n\nMUHIM: endi boshqa TOOL chaqirma — yakuniy javob ber."
                    : system;

            LlmResult r = callLlm(sys, ctx.toString(), errs);
            if (r == null) break;             // every provider failed
            lastProvider = r.provider();

            ToolCall tc = parseToolCall(r.text());
            if (tc == null) {
                return new ChatResponse(r.text().trim(), snapshot, r.provider());
            }

            // Execute the requested tool (tenant-scoped, read-only) and feed
            // the result back into the running context for the next turn.
            String result = tools.call(tc.name(), tc.args());
            log.debug("AI tool call: {} {} -> {} chars", tc.name(), tc.args(), result.length());
            ctx.append("\n\n[siz so'radingiz] TOOL ").append(tc.name())
               .append("\n[natija]\n").append(result)
               .append("\n\nShu ma'lumot asosida davom et.");
        }

        // Tool budget exhausted or no provider answered: degrade gracefully
        // to the raw snapshot so the UI still shows real numbers.
        String diag = errs.length() == 0
                ? "AI hozir javob bera olmadi."
                : "Barcha AI providerlar javob bermadi: " + errs;
        return new ChatResponse(
                diag + (snapshot.isEmpty() ? "" : "\n\nKalit ma'lumot:\n" + snapshot),
                snapshot, lastProvider);
    }

    // --------------------------------------------------------------- llm + tools

    private record LlmResult(String text, String provider) { }

    /** Walk the provider chain once; first success wins, failures accumulate in {@code errs}. */
    private LlmResult callLlm(String system, String user, StringBuilder errs) {
        for (AiProvider p : chain) {
            if (!p.isConfigured()) continue;
            try {
                String answer = p.complete(system, user);
                if (answer != null && !answer.isBlank()) {
                    return new LlmResult(answer, p.name());
                }
            } catch (Exception ex) {
                log.warn("AI provider {} failed: {}", p.name(), ex.getMessage());
                errs.append(p.name()).append("=").append(ex.getMessage()).append("; ");
            }
        }
        return null;
    }

    private record ToolCall(String name, Map<String, Object> args) { }

    /**
     * Parse a {@code TOOL <name> {json}} line out of the model's reply.
     * Returns {@code null} when the reply is a normal (final) answer.
     * Tolerant of markdown fences the model sometimes adds.
     */
    private ToolCall parseToolCall(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replaceAll("(?s)```[a-zA-Z]*", "").trim();
        }
        Matcher m = TOOL_RE.matcher(t);
        if (!m.find()) return null;
        String name = m.group(1);
        Map<String, Object> args = Map.of();
        String json = m.group(2);
        if (json != null && !json.isBlank()) {
            try {
                args = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() { });
            } catch (Exception ignore) {
                // Malformed args — run the tool with no args; it defaults sensibly.
            }
        }
        return new ToolCall(name, args);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // --------------------------------------------------------------- snapshot

    private String buildSnapshot() {
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        sb.append("HOZIRGI VAQT: ").append(LocalDateTime.now()).append("\n\n");

        // -------- POS sotuv jami (kun-by-kun + hafta + oy) --------
        // Sale jadvalidan, fakat haqiqiy POS sotuvlari (Payment jurnaliga
        // tegmaydi). AI eng ko'p so'raydigan savollar shu ma'lumotni
        // talab qiladi — "kecha qancha sotdik?" / "shu hafta qancha?"
        sb.append("POS SOTUVLAR:\n");
        appendSalesLine(sb, "  Bugun     ", today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        appendSalesLine(sb, "  Kecha     ", yesterday.atStartOfDay(), today.atStartOfDay());
        appendSalesLine(sb, "  Bu hafta  ", today.minusDays(7).atStartOfDay(), today.plusDays(1).atStartOfDay());
        appendSalesLine(sb, "  Bu oy     ", today.minusDays(30).atStartOfDay(), today.plusDays(1).atStartOfDay());
        sb.append('\n');

        // -------- Today's full financial snapshot --------
        try {
            var rep = reports.forDate(today);
            sb.append("BUGUNGI MOLIYA:\n");
            sb.append("  Do'kon xarajati: ").append(money(rep.marketTotal())).append(" USD\n");
            sb.append("  Uy xarajati:     ").append(money(rep.homeTotal())).append(" USD\n");
            sb.append("  Kassa qoldiq:    ").append(money(rep.estimatedCash())).append(" USD\n");
            sb.append("  Bizning qarz:    ").append(money(rep.myDebtTotal())).append(" USD\n");
            sb.append("  Mijozdan qarz:   ").append(money(rep.customerDebtTotal())).append(" USD\n\n");
        } catch (Exception ignore) { /* report not available */ }

        // -------- Top profitable products (30 days) --------
        try {
            var topProducts = analytics.profitByProduct(today.minusDays(30), today);
            if (!topProducts.isEmpty()) {
                sb.append("OXIRGI 30 KUNDA ENG FOYDALI MAHSULOTLAR:\n");
                topProducts.stream().limit(5).forEach(p ->
                        sb.append("  - ").append(p.name())
                                .append(": ").append(p.soldQty()).append(" dona, foyda ")
                                .append(money(p.profitUsd())).append(" USD\n"));
                sb.append('\n');
            }
        } catch (Exception ignore) { /* */ }

        // -------- Low stock items --------
        try {
            var low = products.findLowStockProducts();
            if (!low.isEmpty()) {
                long zero = low.stream().filter(p -> p.getQuantity() == 0).count();
                sb.append("PAST STOK: ").append(low.size())
                  .append(" ta mahsulot tugayapti (shu jumladan ").append(zero).append(" ta tugagan)\n");
                low.stream().limit(5).forEach(p ->
                        sb.append("  - ").append(p.getName())
                                .append(": qoldiq ").append(p.getQuantity()).append("\n"));
                sb.append('\n');
            }
        } catch (Exception ignore) { /* */ }

        return sb.toString();
    }

    /** Helper — appends "label: N ta savdo, X USD jami" line. */
    private void appendSalesLine(StringBuilder sb, String label,
                                 LocalDateTime from, LocalDateTime to) {
        try {
            Object[] row = sales.summaryBetween(from, to);
            // Hibernate sometimes nests the result inside an extra array.
            if (row != null && row.length == 1 && row[0] instanceof Object[] inner) {
                row = inner;
            }
            long count = row != null && row.length > 0 ? ((Number) row[0]).longValue() : 0L;
            Object totalObj = row != null && row.length > 1 ? row[1] : BigDecimal.ZERO;
            Object refundObj = row != null && row.length > 2 ? row[2] : BigDecimal.ZERO;
            BigDecimal total = (totalObj instanceof BigDecimal bd) ? bd : new BigDecimal(String.valueOf(totalObj));
            BigDecimal refunded = (refundObj instanceof BigDecimal br) ? br : new BigDecimal(String.valueOf(refundObj));
            BigDecimal net = total.subtract(refunded);
            sb.append(label).append(": ")
              .append(count).append(" ta savdo, ")
              .append(money(net)).append(" USD");
            if (refunded.signum() > 0) {
                sb.append(" (qaytarilgan ").append(money(refunded)).append(")");
            }
            sb.append('\n');
        } catch (Exception ex) {
            sb.append(label).append(": (ma'lumot yo'q)\n");
        }
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0";
        return v.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
