package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.barakat.market.domain.Product;
import uz.barakat.market.dto.CustomerResponse;
import uz.barakat.market.dto.DebtorResponse;
import uz.barakat.market.dto.OrderResponse;
import uz.barakat.market.dto.OrdersByStatus;
import uz.barakat.market.dto.SupplierResponse;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;

/**
 * Read-only, tenant-scoped "tools" the AI assistant can call to answer
 * ANY question about the shop's own data: sales, profit, customers and
 * their debts, suppliers, our debts, expenses and cash, inventory, expiry
 * and supplier orders. The goal is total awareness — the assistant of a
 * given shop knows 100% of that shop's figures (and only that shop's).
 *
 * <h2>Why a tool layer</h2>
 * The {@link AiChatService} agent loop lets the model request one tool by
 * replying {@code TOOL <name> {json-args}}; we run it here and hand the
 * result back. The model never touches the database directly — it can only
 * invoke these vetted, parameter-checked methods.
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li><b>Read-only.</b> Every method only SELECTs (it reuses the same
 *       read paths as the REST controllers). No mutations live here.</li>
 *   <li><b>Tenant-scoped.</b> These run on the {@code /api/ai/ask} request
 *       thread, where {@code TenantFilter} has set the account / shop
 *       context, so the same Hibernate filters that protect every
 *       controller apply. A shop can only ever read its own rows.</li>
 *   <li><b>Bounded.</b> Limits are clamped; unknown tools / bad args
 *       return a short {@code XATO: ...} string the model can recover from.</li>
 * </ul>
 */
@Service
public class AiToolService {

    private static final Logger log = LoggerFactory.getLogger(AiToolService.class);

    private final SaleRepository sales;
    private final AnalyticsService analytics;
    private final ReportService reports;
    private final ProductRepository products;
    private final CustomerService customers;
    private final SupplierService suppliers;
    private final DebtService debts;
    private final OrderService orders;

    public AiToolService(SaleRepository sales, AnalyticsService analytics,
                         ReportService reports, ProductRepository products,
                         CustomerService customers, SupplierService suppliers,
                         DebtService debts, OrderService orders) {
        this.sales = sales;
        this.analytics = analytics;
        this.reports = reports;
        this.products = products;
        this.customers = customers;
        this.suppliers = suppliers;
        this.debts = debts;
        this.orders = orders;
    }

    /**
     * Human-readable catalogue of the available tools, injected verbatim
     * into the system prompt so the model knows what it can call and with
     * which arguments. Dates are ISO {@code YYYY-MM-DD}; the words
     * {@code bugun} / {@code kecha} are also accepted.
     */
    public String catalog() {
        return """
            MAVJUD ASBOBLAR (kerakli BITTASINI tanlab chaqir — javob shu do'kon ma'lumotidan):
            SAVDO / FOYDA:
            - salesInRange {from, to}        -> oraliqdagi sotuvlar soni va sof tushum (USD)
            - productSales {name, from, to}  -> bitta mahsulot nechta sotilgan + foyda
            - topProducts {from, to, limit}  -> eng foydali mahsulotlar
            - hourlySales {from, to}         -> qaysi soatlarda ko'p sotilgan
            MIJOZLAR / QARZ:
            - customerDebt {}                -> mijozlar bizga qancha qarz (jami + eng katta qarzdorlar)
            - customerInfo {name}            -> bitta mijoz: balans, telefon, ball, daraja
            - customerCount {}               -> mijozlar soni va umumiy balans
            YETKAZIB BERUVCHILAR / BIZNING QARZ:
            - supplierDebt {}                -> yetkazib beruvchilarga jami to'langan / balans
            - supplierInfo {name}            -> bitta yetkazib beruvchi ma'lumoti
            - myDebts {}                     -> bizning qarzlarimiz (kimga, qancha qoldi)
            MOLIYA / KASSA / XARAJAT:
            - financeOnDate {date}           -> o'sha kungi sotuv, foyda, xarajat, kassa, qarzlar
            - debtsOverview {}               -> hozirgi holatda mijoz qarzi + bizning qarz jami
            OMBOR / MAHSULOT:
            - inventoryValue {}              -> ombor qiymati (tannarx) + potensial foyda + tur soni
            - productInfo {name}             -> bitta mahsulot: qoldiq, narx, yaroqlilik muddati
            - lowStock {}                    -> tugayotgan / tugagan mahsulotlar
            - expiringSoon {days}            -> muddati yaqinlashgan mahsulotlar (default 30 kun)
            BUYURTMALAR:
            - orders {}                      -> bugun keladigan / kechikkan / kelgusi buyurtmalar
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
                case "salesInRange"  -> salesInRange(date(args, "from"), date(args, "to"));
                case "productSales"  -> productSales(str(args, "name"),
                        date(args, "from"), date(args, "to"));
                case "topProducts"   -> topProducts(date(args, "from"), date(args, "to"),
                        intArg(args, "limit", 5));
                case "hourlySales"   -> hourlySales(date(args, "from"), date(args, "to"));
                case "customerDebt"  -> customerDebt();
                case "customerInfo"  -> customerInfo(str(args, "name"));
                case "customerCount" -> customerCount();
                case "supplierDebt"  -> supplierDebt();
                case "supplierInfo"  -> supplierInfo(str(args, "name"));
                case "myDebts"       -> myDebts();
                case "debtsOverview" -> debtsOverview();
                case "financeOnDate" -> financeOnDate(date(args, "date"));
                case "inventoryValue" -> inventoryValue();
                case "productInfo"   -> productInfo(str(args, "name"));
                case "lowStock"      -> lowStock();
                case "expiringSoon"  -> expiringSoon(intArg(args, "days", 30));
                case "orders"        -> ordersOverview();
                default -> "XATO: noma'lum asbob '" + name + "'";
            };
        } catch (Exception ex) {
            log.warn("AI tool '{}' failed: {}", name, ex.toString());
            return "XATO: '" + name + "' bajarilmadi (" + ex.getMessage() + ")";
        }
    }

    // -------------------------------------------------------- sales / profit

    private String salesInRange(LocalDate from, LocalDate to) {
        long[] cnt = {0};
        BigDecimal[] money = totals(from, to, cnt);
        return String.format(Locale.ROOT,
                "%s..%s: %d ta sotuv, sof tushum %s USD (qaytarilgan %s)",
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

    // ------------------------------------------------------ customers / debt

    private String customerDebt() {
        List<CustomerResponse> all = customers.list();
        List<CustomerResponse> debtorList = all.stream()
                .filter(c -> c.balance() != null && c.balance().signum() > 0)
                .sorted(Comparator.comparing(CustomerResponse::balance).reversed())
                .toList();
        BigDecimal total = debtorList.stream()
                .map(CustomerResponse::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debtorList.isEmpty()) {
            return "Mijozlarda qarz yo'q (hammasi to'lagan).";
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "Mijozlar bizga jami %s USD qarz (%d ta qarzdor). Eng kattalari:\n",
                money(total), debtorList.size()));
        debtorList.stream().limit(10).forEach(c -> sb.append("  - ").append(c.name())
                .append(": ").append(money(c.balance())).append(" USD\n"));
        return sb.toString();
    }

    private String customerInfo(String name) {
        if (name == null || name.isBlank()) return "XATO: mijoz nomi kerak";
        String needle = name.toLowerCase(Locale.ROOT);
        var hit = customers.list().stream()
                .filter(c -> c.name() != null
                        && c.name().toLowerCase(Locale.ROOT).contains(needle))
                .findFirst().orElse(null);
        if (hit == null) return "'" + name + "' nomli mijoz topilmadi";
        return String.format(Locale.ROOT,
                "Mijoz: %s | tel: %s | balans: %s USD (%s) | berilgan: %s | to'langan: %s | "
                + "ball: %d | daraja: %s",
                hit.name(), nz(hit.phone()), money(hit.balance()),
                hit.balance() != null && hit.balance().signum() > 0 ? "qarzdor"
                        : hit.balance() != null && hit.balance().signum() < 0 ? "haqdor" : "tenglik",
                money(hit.goodsTotal()), money(hit.paidTotal()),
                hit.pointsBalance(), nz(hit.tier()));
    }

    private String customerCount() {
        List<CustomerResponse> all = customers.list();
        BigDecimal net = all.stream().map(CustomerResponse::balance)
                .filter(b -> b != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        return String.format(Locale.ROOT,
                "Jami %d ta mijoz. Umumiy balans (qarz - haq): %s USD",
                all.size(), money(net));
    }

    // ---------------------------------------------------- suppliers / my debt

    private String supplierDebt() {
        List<SupplierResponse> all = suppliers.list();
        if (all.isEmpty()) return "Yetkazib beruvchilar yo'q.";
        BigDecimal paid = all.stream().map(SupplierResponse::paidTotal)
                .filter(b -> b != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "Jami %d ta yetkazib beruvchi, ularga jami to'langan %s USD:\n",
                all.size(), money(paid)));
        all.stream().limit(10).forEach(s -> sb.append("  - ").append(s.name())
                .append(": to'langan ").append(money(s.paidTotal())).append(" USD\n"));
        return sb.toString();
    }

    private String supplierInfo(String name) {
        if (name == null || name.isBlank()) return "XATO: yetkazib beruvchi nomi kerak";
        String needle = name.toLowerCase(Locale.ROOT);
        var hit = suppliers.list().stream()
                .filter(s -> s.name() != null
                        && s.name().toLowerCase(Locale.ROOT).contains(needle))
                .findFirst().orElse(null);
        if (hit == null) return "'" + name + "' nomli yetkazib beruvchi topilmadi";
        return String.format(Locale.ROOT,
                "Yetkazib beruvchi: %s | tel: %s | jami to'langan: %s USD | balans: %s USD",
                hit.name(), nz(hit.phone()), money(hit.paidTotal()), money(hit.balance()));
    }

    private String myDebts() {
        List<DebtorResponse> mine = debts.listMyDebts().stream()
                .filter(d -> !d.paid()).toList();
        if (mine.isEmpty()) return "Bizning qarzimiz yo'q.";
        BigDecimal total = mine.stream().map(DebtorResponse::remainingAmount)
                .filter(b -> b != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "Bizning jami qarzimiz %s USD (%d ta):\n", money(total), mine.size()));
        mine.stream().limit(10).forEach(d -> sb.append("  - ").append(d.name())
                .append(": qoldiq ").append(money(d.remainingAmount())).append(" USD")
                .append(d.productName() == null ? "" : " (" + d.productName() + ")").append("\n"));
        return sb.toString();
    }

    private String debtsOverview() {
        var s = debts.summary();
        return String.format(Locale.ROOT,
                "Hozirgi qarzlar holati: mijozlar bizga %s USD qarz, biz boshqalarga %s USD qarzmiz.",
                money(s.customerDebtTotal()), money(s.myDebtTotal()));
    }

    // ------------------------------------------------------ finance / cash

    private String financeOnDate(LocalDate date) {
        var rep = reports.forDate(date);
        return String.format(Locale.ROOT,
                "%s moliya: sotuv %s, taxminiy foyda %s, do'kon xarajati %s, uy xarajati %s, "
                + "kassa qoldig'i %s, bizning qarz %s, mijoz qarzi %s (USD)",
                date,
                rep.sales() == null ? "0" : money(rep.sales().net()),
                rep.sales() == null ? "0" : money(rep.sales().profit()),
                money(rep.marketTotal()), money(rep.homeTotal()),
                money(rep.estimatedCash()), money(rep.myDebtTotal()),
                money(rep.customerDebtTotal()));
    }

    // --------------------------------------------------------- inventory

    private String inventoryValue() {
        List<Product> all = products.findAllByOrderByNameAsc();
        if (all.isEmpty()) return "Omborda mahsulot yo'q.";
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        long units = 0;
        for (Product p : all) {
            BigDecimal qty = BigDecimal.valueOf(p.getQuantity());
            BigDecimal pp = nzBd(p.getPurchasePrice());
            BigDecimal sp = nzBd(p.getSalePrice());
            cost = cost.add(pp.multiply(qty));
            profit = profit.add(sp.subtract(pp).multiply(qty));
            units += p.getQuantity();
        }
        return String.format(Locale.ROOT,
                "Omborda %d xil mahsulot, jami %d dona. Ombor qiymati (tannarx) %s USD, "
                + "potensial foyda %s USD.",
                all.size(), units, money(cost), money(profit));
    }

    private String productInfo(String name) {
        if (name == null || name.isBlank()) return "XATO: mahsulot nomi kerak";
        String needle = name.toLowerCase(Locale.ROOT);
        var hit = products.findAllByOrderByNameAsc().stream()
                .filter(p -> p.getName() != null
                        && p.getName().toLowerCase(Locale.ROOT).contains(needle))
                .findFirst().orElse(null);
        if (hit == null) return "'" + name + "' nomli mahsulot topilmadi";
        String expiry = hit.getExpiryDate() == null ? "yo'q" : hit.getExpiryDate().toString();
        return String.format(Locale.ROOT,
                "Mahsulot: %s | qoldiq: %d dona | sotuv narxi: %s | tannarx: %s | "
                + "yaroqlilik: %s",
                hit.getName(), hit.getQuantity(), money(hit.getSalePrice()),
                money(hit.getPurchasePrice()), expiry);
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

    private String expiringSoon(int days) {
        int window = Math.min(Math.max(days, 1), 365);
        LocalDate limit = LocalDate.now().plusDays(window);
        var soon = products.findAllByOrderByNameAsc().stream()
                .filter(p -> p.getExpiryDate() != null && !p.getExpiryDate().isAfter(limit))
                .sorted(Comparator.comparing(Product::getExpiryDate))
                .toList();
        if (soon.isEmpty()) {
            return String.format("Keyingi %d kun ichida muddati o'tadigan mahsulot yo'q.", window);
        }
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder(String.format(
                "%d kun ichida muddati o'tadigan %d mahsulot:\n", window, soon.size()));
        soon.stream().limit(20).forEach(p -> {
            boolean expired = p.getExpiryDate().isBefore(today);
            sb.append("  - ").append(p.getName()).append(": ")
              .append(p.getExpiryDate()).append(expired ? " (o'tib ketgan!)" : "").append("\n");
        });
        return sb.toString();
    }

    // ----------------------------------------------------------- orders

    private String ordersOverview() {
        OrdersByStatus g = orders.grouped();
        if (g.today().isEmpty() && g.overdue().isEmpty() && g.upcoming().isEmpty()) {
            return "Ochiq buyurtmalar yo'q.";
        }
        StringBuilder sb = new StringBuilder("Buyurtmalar holati:\n");
        sb.append("  Bugun keladi: ").append(orderLine(g.today())).append('\n');
        sb.append("  Kechikkan: ").append(orderLine(g.overdue())).append('\n');
        sb.append("  Kelgusi: ").append(orderLine(g.upcoming()));
        return sb.toString();
    }

    private static String orderLine(List<OrderResponse> list) {
        if (list == null || list.isEmpty()) return "yo'q";
        BigDecimal sum = list.stream().map(OrderResponse::amount)
                .filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        String names = list.stream().limit(5).map(OrderResponse::name)
                .reduce((a, b) -> a + ", " + b).orElse("");
        return list.size() + " ta (" + money(sum) + " USD): " + names;
    }

    // ------------------------------------------------------------- helpers

    /** Pull count + net + refunded for a date range via the existing summary query. */
    private BigDecimal[] totals(LocalDate from, LocalDate to, long[] countOut) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay(); // inclusive of 'to'
        Object[] row = sales.summaryBetween(fromTs, toTs);
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

    private static BigDecimal nzBd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0";
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
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
     * Falls back to today if missing/unparseable.
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
