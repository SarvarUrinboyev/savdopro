package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;

/**
 * Read-only, tenant-scoped "tools" the AI assistant can call to answer
 * questions that the fixed KPI snapshot can't cover (a specific product,
 * an arbitrary date range, the busiest hour, the finance of a given day).
 *
 * <h2>Why a tool layer</h2>
 * The chat used to feed the LLM one static snapshot, so it could only
 * answer ~4 canned questions. This service exposes a small, whitelisted
 * set of query functions. The {@link AiChatService} agent loop lets the
 * model request one by replying {@code TOOL <name> {json-args}}; we run
 * it here and hand the result back. The model never touches the database
 * directly — it can only invoke these vetted, parameter-checked methods.
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li><b>Read-only.</b> Every method only SELECTs. Mutations (promos,
 *       reorders, sending reports) are deliberately NOT here — those are
 *       a later, confirmation-gated phase.</li>
 *   <li><b>Tenant-scoped.</b> These run on the {@code /api/ai/ask} request
 *       thread, where {@code TenantFilter} has already set the account /
 *       shop context, so the same Hibernate filters that protect every
 *       controller apply here too. A merchant can only ever read its own
 *       rows.</li>
 *   <li><b>Bounded.</b> Limits are clamped; unknown tools / bad args
 *       return a short error string the model can recover from.</li>
 * </ul>
 */
@Service
public class AiToolService {

    private static final Logger log = LoggerFactory.getLogger(AiToolService.class);

    private final SaleRepository sales;
    private final AnalyticsService analytics;
    private final ReportService reports;
    private final ProductRepository products;

    public AiToolService(SaleRepository sales, AnalyticsService analytics,
                         ReportService reports, ProductRepository products) {
        this.sales = sales;
        this.analytics = analytics;
        this.reports = reports;
        this.products = products;
    }

    /**
     * Human-readable catalogue of the available tools, injected verbatim
     * into the system prompt so the model knows what it can call and with
     * which arguments. Dates are ISO {@code YYYY-MM-DD}; the words
     * {@code bugun} / {@code kecha} are also accepted.
     */
    public String catalog() {
        return """
            MAVJUD ASBOBLAR (kerak bo'lsa birini chaqir):
            - salesInRange {from, to}        -> oraliqdagi sotuvlar soni va sof tushum (UZS)
            - productSales {name, from, to}  -> bitta mahsulot nechta sotilgan + foyda
            - topProducts {from, to, limit}  -> eng foydali mahsulotlar ro'yxati
            - hourlySales {from, to}         -> qaysi soatlarda ko'p sotilgan (eng gavjum soat)
            - financeOnDate {date}           -> o'sha kungi xarajat, kassa qoldig'i, qarzlar
            - lowStock {}                    -> tugayotgan / tugagan mahsulotlar
            """;
    }

    /**
     * Dispatch a tool by name. Returns a compact, plain-text result that
     * is fed straight back to the model. Never throws — any problem is
     * returned as a short {@code XATO: ...} string so the agent loop can
     * continue (and the model can apologise gracefully).
     */
    public String call(String name, Map<String, Object> args) {
        if (name == null) return "XATO: asbob nomi yo'q";
        try {
            return switch (name.trim()) {
                case "salesInRange" -> salesInRange(date(args, "from"), date(args, "to"));
                case "productSales" -> productSales(str(args, "name"),
                        date(args, "from"), date(args, "to"));
                case "topProducts"  -> topProducts(date(args, "from"), date(args, "to"),
                        intArg(args, "limit", 5));
                case "hourlySales"  -> hourlySales(date(args, "from"), date(args, "to"));
                case "financeOnDate" -> financeOnDate(date(args, "date"));
                case "lowStock"     -> lowStock();
                default -> "XATO: noma'lum asbob '" + name + "'";
            };
        } catch (Exception ex) {
            log.warn("AI tool '{}' failed: {}", name, ex.toString());
            return "XATO: '" + name + "' bajarilmadi (" + ex.getMessage() + ")";
        }
    }

    // ------------------------------------------------------------- tools

    private String salesInRange(LocalDate from, LocalDate to) {
        long[] cnt = {0};
        BigDecimal[] money = totals(from, to, cnt);
        return String.format(Locale.ROOT,
                "%s..%s: %d ta sotuv, sof tushum %s UZS (qaytarilgan %s)",
                from, to, cnt[0], money(money[0]), money(money[1]));
    }

    private String productSales(String name, LocalDate from, LocalDate to) {
        if (name == null || name.isBlank()) return "XATO: mahsulot nomi kerak";
        String needle = name.toLowerCase(Locale.ROOT);
        var rows = analytics.profitByProduct(from, to).stream()
                .filter(r -> r.name() != null
                        && r.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        if (rows.isEmpty()) {
            return String.format("%s..%s: '%s' bo'yicha sotuv topilmadi", from, to, name);
        }
        StringBuilder sb = new StringBuilder(
                String.format("%s..%s, '%s' bo'yicha:\n", from, to, name));
        rows.forEach(r -> sb.append("  - ").append(r.name())
                .append(": ").append(r.soldQty()).append(" dona, foyda ")
                .append(money(r.profitUsd())).append(" USD\n"));
        return sb.toString();
    }

    private String topProducts(LocalDate from, LocalDate to, int limit) {
        int lim = Math.min(Math.max(limit, 1), 20);
        var rows = analytics.profitByProduct(from, to);
        if (rows.isEmpty()) return String.format("%s..%s: sotuv yo'q", from, to);
        StringBuilder sb = new StringBuilder(
                String.format("%s..%s eng foydali %d mahsulot:\n", from, to, lim));
        rows.stream().limit(lim).forEach(r -> sb.append("  - ").append(r.name())
                .append(": ").append(r.soldQty()).append(" dona, foyda ")
                .append(money(r.profitUsd())).append(" USD\n"));
        return sb.toString();
    }

    private String hourlySales(LocalDate from, LocalDate to) {
        var buckets = analytics.hourlySales(from, to);
        if (buckets.isEmpty()) return String.format("%s..%s: sotuv yo'q", from, to);
        var peak = buckets.stream()
                .max((a, b) -> Long.compare(a.count(), b.count())).orElse(null);
        StringBuilder sb = new StringBuilder(String.format("%s..%s soatlik sotuv:\n", from, to));
        buckets.forEach(b -> sb.append(String.format("  %02d:00 -> %d ta\n", b.hour(), b.count())));
        if (peak != null) {
            sb.append(String.format("Eng gavjum soat: %02d:00 (%d ta)", peak.hour(), peak.count()));
        }
        return sb.toString();
    }

    private String financeOnDate(LocalDate date) {
        var rep = reports.forDate(date);
        return String.format(Locale.ROOT,
                "%s moliya: do'kon xarajati %s, uy xarajati %s, kassa qoldig'i %s, "
                + "bizning qarz %s, mijoz qarzi %s (UZS)",
                date, money(rep.marketTotal()), money(rep.homeTotal()),
                money(rep.estimatedCash()), money(rep.myDebtTotal()),
                money(rep.customerDebtTotal()));
    }

    private String lowStock() {
        var low = products.findLowStockProducts();
        if (low.isEmpty()) return "Hamma mahsulot yetarli — past stok yo'q";
        long zero = low.stream().filter(p -> p.getQuantity() == 0).count();
        StringBuilder sb = new StringBuilder(
                String.format("%d ta mahsulot tugayapti (%d tasi tugagan):\n", low.size(), zero));
        low.stream().limit(20).forEach(p -> sb.append("  - ").append(p.getName())
                .append(": qoldiq ").append(p.getQuantity()).append("\n"));
        return sb.toString();
    }

    // ------------------------------------------------------------- helpers

    /** Pull count + net + refunded for a date range via the existing summary query. */
    private BigDecimal[] totals(LocalDate from, LocalDate to, long[] countOut) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay(); // inclusive of 'to'
        Object[] row = sales.summaryBetween(fromTs, toTs);
        // Hibernate sometimes nests the projection inside an extra array.
        if (row != null && row.length == 1 && row[0] instanceof Object[] inner) {
            row = inner;
        }
        long count = row != null && row.length > 0 ? ((Number) row[0]).longValue() : 0L;
        BigDecimal total = bd(row != null && row.length > 1 ? row[1] : null);
        BigDecimal refunded = bd(row != null && row.length > 2 ? row[2] : null);
        countOut[0] = count;
        return new BigDecimal[]{ total.subtract(refunded), refunded };
    }

    private static BigDecimal bd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(String.valueOf(o));
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0";
        return v.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static int intArg(Map<String, Object> args, String key, int dflt) {
        Object v = args == null ? null : args.get(key);
        if (v instanceof Number n) return n.intValue();
        try { return v == null ? dflt : Integer.parseInt(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return dflt; }
    }

    /**
     * Resolve a date argument. Accepts ISO {@code YYYY-MM-DD} plus the
     * Uzbek keywords {@code bugun} (today) and {@code kecha} (yesterday).
     * Falls back to today if missing/unparseable so a sloppy model call
     * still returns something useful rather than erroring out.
     */
    private static LocalDate date(Map<String, Object> args, String key) {
        String raw = str(args, key);
        if (raw == null || raw.isBlank()) return LocalDate.now();
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "bugun", "today" -> LocalDate.now();
            case "kecha", "yesterday" -> LocalDate.now().minusDays(1);
            default -> {
                try { yield LocalDate.parse(raw.trim()); }
                catch (Exception e) { yield LocalDate.now(); }
            }
        };
    }
}
