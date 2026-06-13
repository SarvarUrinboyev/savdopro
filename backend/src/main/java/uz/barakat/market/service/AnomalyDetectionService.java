package uz.barakat.market.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.config.AnomalyProperties;
import uz.barakat.market.domain.Expense;
import uz.barakat.market.domain.HomeExpense;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.domain.Shift;
import uz.barakat.market.repository.ExpenseRepository;
import uz.barakat.market.repository.HomeExpenseRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.SaleRepository.CashierRefundRow;
import uz.barakat.market.repository.ShiftRepository;

/**
 * Deterministic anomaly engine for the AI anomaly-control feature. Runs five
 * rule-based detectors for ONE business day in whatever single-shop tenant
 * scope is active when called, and returns {@link Candidate}s — it never
 * persists or notifies (that is {@code AnomalyMonitorService}'s job, mirroring
 * the {@code AnomalyService}/{@code AnomalyAlertService} read/write split).
 *
 * <p>All money is compared in the canonical unit (USD): {@code Sale}/{@code
 * Product} amounts already are, while {@code Payment}/{@code Expense} rows are
 * converted via {@link MoneyConverter}. Baselines are the shop's own trailing
 * history, so comparisons are always within one shop. Rules are intentionally
 * loud — owners want false positives over missed losses — and every threshold
 * is tunable via {@link AnomalyProperties}.
 */
@Service
@Transactional(readOnly = true)
public class AnomalyDetectionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String INFO = "info";
    private static final String WARN = "warn";
    private static final String CRITICAL = "critical";

    /** A detected anomaly, not yet persisted. */
    public record Candidate(
            String severity, String code, String dedupeKey,
            LocalDate occurredOn, String message, String detailJson) { }

    private final SaleRepository sales;
    private final ProductRepository products;
    private final ExpenseRepository expenses;
    private final HomeExpenseRepository homeExpenses;
    private final ShiftRepository shifts;
    private final BalanceService balanceService;
    private final MoneyConverter converter;
    private final AnomalyProperties props;
    private final ObjectMapper mapper;

    public AnomalyDetectionService(SaleRepository sales, ProductRepository products,
                                   ExpenseRepository expenses, HomeExpenseRepository homeExpenses,
                                   ShiftRepository shifts, BalanceService balanceService,
                                   MoneyConverter converter, AnomalyProperties props,
                                   ObjectMapper mapper) {
        this.sales = sales;
        this.products = products;
        this.expenses = expenses;
        this.homeExpenses = homeExpenses;
        this.shifts = shifts;
        this.balanceService = balanceService;
        this.converter = converter;
        this.props = props;
        this.mapper = mapper;
    }

    /** Runs all five detectors for {@code day} in the active shop scope. */
    public List<Candidate> detect(LocalDate day) {
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        List<Sale> daySales = sales.findByCreatedAtBetweenOrderByCreatedAtDesc(dayStart, dayEnd);

        List<Candidate> out = new ArrayList<>();
        out.addAll(tillNegative(day, daySales, dayStart, dayEnd)); // 1. Kassa minusga tushishi
        out.addAll(belowCostDaily(day, daySales));           // 2. Tannarxdan past sotuv
        out.addAll(suspiciousRefunds(day, daySales, dayStart, dayEnd)); // 3. G'alati refund
        out.addAll(nightSalesSpike(day, daySales));          // 4. Tungi sotuv spike
        out.addAll(cashierPatterns(day, daySales, dayEnd));  // 5. Xodim bo'yicha pattern
        return out;
    }

    // ============================================================= 1. till < 0

    private List<Candidate> tillNegative(LocalDate day, List<Sale> daySales,
                                         LocalDateTime dayStart, LocalDateTime dayEnd) {
        // Booked cash drawer for the day:
        //   morning cash + cash sales − cash refunds − cash (naqd) expenses.
        // Sale amounts are already in the canonical unit (USD) — like
        // ReportService.salesFor — so they are summed raw; expenses are entered
        // in their own currency, so they go through MoneyConverter (matching
        // ReportService.estimatedCash). The payments journal is intentionally
        // NOT used here: POS books sale/refund Payments with a canonical amount
        // but a defaulted UZS currency, which MoneyConverter would mis-scale.
        BigDecimal start = balanceService.startingCash(day);

        BigDecimal cashIn = ZERO;
        for (Sale s : daySales) {
            if (isCash(s.getPaymentMethod())) {
                cashIn = cashIn.add(s.getTotalUzs());
            }
        }
        BigDecimal cashRefunds = ZERO;
        for (Sale s : sales.findRefundedBetween(dayStart, dayEnd)) {
            if (isCash(s.getPaymentMethod())) {
                cashRefunds = cashRefunds.add(s.getRefundedTotalUzs());
            }
        }
        BigDecimal cashExpenses = ZERO;
        for (Expense e : expenses.findByDateOrderByIdDesc(day)) {
            cashExpenses = cashExpenses.add(converter.toUsd(e.getNaqdAmount(), e.getCurrency()));
        }
        for (HomeExpense e : homeExpenses.findByDateOrderByIdDesc(day)) {
            cashExpenses = cashExpenses.add(converter.toUsd(e.getNaqdAmount(), e.getCurrency()));
        }

        BigDecimal position = start.add(cashIn).subtract(cashRefunds).subtract(cashExpenses);
        if (position.signum() >= 0) {
            return List.of();
        }
        boolean deep = position.abs().compareTo(props.largeRefundMinUsd()) >= 0;
        String sev = deep ? CRITICAL : WARN;
        String msg = "Kassada naqd pul manfiyga tushdi (hisob bo'yicha: -" + MoneyFormat.usd(position.abs())
                + "). Ertalabgi balans, naqd sotuv, qaytarish va chiqimlarni tekshiring.";
        return List.of(new Candidate(sev, "till-negative", "till-negative:" + day, day, msg,
                json("startingCash", start, "cashIn", cashIn, "cashRefunds", cashRefunds,
                        "cashExpenses", cashExpenses, "position", position)));
    }

    /** A sale's payment method that physically takes/returns cash from the drawer. */
    private static boolean isCash(String paymentMethod) {
        return paymentMethod != null && paymentMethod.trim().equalsIgnoreCase("NAQD");
    }

    // ====================================================== 2. below-cost daily

    private List<Candidate> belowCostDaily(LocalDate day, List<Sale> daySales) {
        Map<Long, BigDecimal> cost = costByProduct(daySales);
        BigDecimal totalLoss = ZERO;
        int lineCount = 0;
        for (Sale s : daySales) {
            for (SaleItem it : s.getItems()) {
                if (it.getProductId() == null || it.getQuantity() <= 0) {
                    continue;
                }
                BigDecimal c = cost.get(it.getProductId());
                if (c == null || c.signum() <= 0) {
                    continue; // unknown cost → can't judge
                }
                BigDecimal eff = it.getLineTotalUzs()
                        .divide(BigDecimal.valueOf(it.getQuantity()), 2, RoundingMode.HALF_UP);
                if (eff.compareTo(c) < 0) {
                    totalLoss = totalLoss.add(c.subtract(eff).multiply(BigDecimal.valueOf(it.getQuantity())));
                    lineCount++;
                }
            }
        }
        if (totalLoss.compareTo(props.belowCostMinDailyUsd()) < 0) {
            return List.of();
        }
        String sev = totalLoss.compareTo(props.largeRefundMinUsd()) >= 0 ? CRITICAL : WARN;
        String msg = "Bugun tannarxdan past sotuvlar: jami taxminiy zarar -" + MoneyFormat.usd(totalLoss)
                + " (" + lineCount + " ta chiziq). Narx yoki kassirni tekshiring.";
        return List.of(new Candidate(sev, "below-cost-daily", "below-cost-daily:" + day, day, msg,
                json("lossUsd", totalLoss, "lineCount", lineCount)));
    }

    // ========================================================== 3. refunds

    private List<Candidate> suspiciousRefunds(LocalDate day, List<Sale> daySales,
                                              LocalDateTime dayStart, LocalDateTime dayEnd) {
        List<Candidate> out = new ArrayList<>();

        // 3a. daily refund rate (today's sales, like the live banner rule)
        BigDecimal revenue = ZERO;
        BigDecimal refunds = ZERO;
        for (Sale s : daySales) {
            revenue = revenue.add(s.getTotalUzs());
            refunds = refunds.add(s.getRefundedTotalUzs());
        }
        if (revenue.signum() > 0 && refunds.signum() > 0) {
            BigDecimal rate = refunds.multiply(HUNDRED).divide(revenue, 1, RoundingMode.HALF_UP);
            String sev = null;
            if (rate.compareTo(BigDecimal.valueOf(props.refundRateCriticalPct())) >= 0) {
                sev = CRITICAL;
            } else if (rate.compareTo(BigDecimal.valueOf(props.refundRatePct())) >= 0) {
                sev = WARN;
            }
            if (sev != null) {
                out.add(new Candidate(sev, "refund-rate", "refund-rate:" + day, day,
                        String.format("Bugun qaytarish darajasi %.1f%% — odatdagidan baland. "
                                + "Kassirni tekshiring.", rate.doubleValue()),
                        json("ratePct", rate, "revenue", revenue, "refunds", refunds)));
            }
        }

        // Refunds that HAPPENED today (refundedAt in [dayStart, dayEnd)).
        List<Sale> refundedToday = sales.findRefundedBetween(dayStart, dayEnd);

        // 3b. refund burst — many refunds in a single hour
        Map<Integer, Integer> byHour = new HashMap<>();
        for (Sale s : refundedToday) {
            if (s.getRefundedAt() != null) {
                byHour.merge(s.getRefundedAt().getHour(), 1, Integer::sum);
            }
        }
        for (Map.Entry<Integer, Integer> e : byHour.entrySet()) {
            if (e.getValue() >= props.refundBurstPerHour()) {
                int hour = e.getKey();
                out.add(new Candidate(WARN, "refund-burst", "refund-burst:" + day + ":" + hour, day,
                        String.format("Soat %02d:00–%02d:00 oralig'ida %d ta qaytarish — g'ayrioddiy ko'p.",
                                hour, (hour + 1) % 24, e.getValue()),
                        json("hour", hour, "count", e.getValue())));
            }
        }

        // 3c. large single refund + 3d. stale refund (long after the sale)
        for (Sale s : refundedToday) {
            if (s.getRefundedTotalUzs().compareTo(props.largeRefundMinUsd()) >= 0) {
                out.add(new Candidate(WARN, "refund-large", "refund-large:" + day + ":" + s.getId(), day,
                        "Katta qaytarish — Chek #" + s.getId() + ": " + MoneyFormat.usd(s.getRefundedTotalUzs())
                                + ". Sababi va kassirni tekshiring.",
                        json("saleId", s.getId(), "amount", s.getRefundedTotalUzs())));
            }
            if (s.getCreatedAt() != null && s.getRefundedAt() != null) {
                long days = java.time.Duration.between(s.getCreatedAt(), s.getRefundedAt()).toDays();
                if (days >= props.refundLateDays()) {
                    out.add(new Candidate(WARN, "refund-stale", "refund-stale:" + day + ":" + s.getId(), day,
                            "Kech qaytarish — Chek #" + s.getId() + " " + days
                                    + " kundan keyin qaytarildi. Tekshiring.",
                            json("saleId", s.getId(), "daysAfter", days)));
                }
            }
        }
        return out;
    }

    // ===================================================== 4. night sales spike

    private List<Candidate> nightSalesSpike(LocalDate day, List<Sale> daySales) {
        long todayCount = 0;
        BigDecimal todayRev = ZERO;
        for (Sale s : daySales) {
            if (s.getCreatedAt() != null && isNight(s.getCreatedAt().getHour())) {
                todayCount++;
                todayRev = todayRev.add(s.getTotalUzs());
            }
        }
        if (todayCount < props.nightSpikeMinCount()) {
            return List.of();
        }

        // Baseline: same night window over the trailing window, excluding today.
        LocalDateTime from = day.minusDays(props.baselineDays()).atStartOfDay();
        List<Sale> baseline = sales.findByCreatedAtBetweenOrderByCreatedAtDesc(from, day.atStartOfDay());
        Set<LocalDate> activeDays = new java.util.HashSet<>(); // distinct days with any sale
        Map<LocalDate, Long> nightCountByDay = new HashMap<>();
        Map<LocalDate, BigDecimal> nightRevByDay = new HashMap<>();
        for (Sale s : baseline) {
            if (s.getCreatedAt() == null) {
                continue;
            }
            LocalDate d = s.getCreatedAt().toLocalDate();
            activeDays.add(d);
            if (isNight(s.getCreatedAt().getHour())) {
                nightCountByDay.merge(d, 1L, Long::sum);
                nightRevByDay.merge(d, s.getTotalUzs(), BigDecimal::add);
            }
        }
        int active = activeDays.size();
        if (active < props.minBaselineDays()) {
            return List.of(); // not enough history to call it a spike
        }
        double meanCount = nightCountByDay.values().stream().mapToLong(Long::longValue).sum() / (double) active;
        BigDecimal meanRev = nightRevByDay.values().stream().reduce(ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(active), 2, RoundingMode.HALF_UP);

        double mult = props.nightSpikeMultiplier();
        boolean countSpike = meanCount > 0 && todayCount >= mult * meanCount;
        boolean revSpike = meanRev.signum() > 0
                && todayRev.compareTo(meanRev.multiply(BigDecimal.valueOf(mult))) >= 0;
        if (!countSpike && !revSpike) {
            return List.of();
        }
        String msg = String.format("Tungi savdo keskin oshdi: bugun %d ta sotuv (%s), "
                        + "odatdagi tungi o'rtacha %.1f ta. Tekshiring.",
                todayCount, MoneyFormat.usd(todayRev), meanCount);
        return List.of(new Candidate(WARN, "night-spike", "night-spike:" + day, day, msg,
                json("todayCount", todayCount, "baselineMeanCount", round1(meanCount),
                        "todayRevenue", todayRev, "baselineMeanRevenue", meanRev)));
    }

    // ================================================ 5. per-cashier patterns

    private List<Candidate> cashierPatterns(LocalDate day, List<Sale> daySales, LocalDateTime dayEnd) {
        // Sub-pattern accumulator per cashier (a cashier can trip several).
        Map<String, List<String>> hits = new LinkedHashMap<>();

        // 5a. refund-rate outlier vs the shop's peer median (recent window).
        LocalDateTime recentFrom = day.minusDays(7).atStartOfDay();
        List<CashierRefundRow> rows = sales.cashierRefundStats(recentFrom, dayEnd);
        List<double[]> rateByCashier = new ArrayList<>(); // [index into names], rate
        List<String> names = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        for (CashierRefundRow r : rows) {
            if (isBlank(r.getCashier()) || r.getReceipts() < props.cashierMinReceipts()) {
                continue;
            }
            BigDecimal gross = r.getGross() == null ? ZERO : r.getGross();
            if (gross.signum() <= 0) {
                continue;
            }
            double rate = r.getRefunded() == null ? 0
                    : r.getRefunded().multiply(HUNDRED).divide(gross, 1, RoundingMode.HALF_UP).doubleValue();
            names.add(r.getCashier());
            rates.add(rate);
        }
        if (rates.size() >= 2) {
            double median = median(rates);
            double threshold = props.cashierRefundOutlierMult() * median;
            for (int i = 0; i < names.size(); i++) {
                if (rates.get(i) > 0 && median > 0 && rates.get(i) >= threshold) {
                    hits.computeIfAbsent(names.get(i), k -> new ArrayList<>())
                            .add(String.format("qaytarish darajasi %.1f%% (do'kon o'rtachasi %.1f%%)",
                                    rates.get(i), median));
                }
            }
        }

        // 5b. below-cost loss concentrated on one cashier (today).
        Map<Long, BigDecimal> cost = costByProduct(daySales);
        Map<String, BigDecimal> belowCostByCashier = new HashMap<>();
        BigDecimal totalBelow = ZERO;
        for (Sale s : daySales) {
            if (isBlank(s.getCashier())) {
                continue;
            }
            for (SaleItem it : s.getItems()) {
                if (it.getProductId() == null || it.getQuantity() <= 0) {
                    continue;
                }
                BigDecimal c = cost.get(it.getProductId());
                if (c == null || c.signum() <= 0) {
                    continue;
                }
                BigDecimal eff = it.getLineTotalUzs()
                        .divide(BigDecimal.valueOf(it.getQuantity()), 2, RoundingMode.HALF_UP);
                if (eff.compareTo(c) < 0) {
                    BigDecimal loss = c.subtract(eff).multiply(BigDecimal.valueOf(it.getQuantity()));
                    belowCostByCashier.merge(s.getCashier(), loss, BigDecimal::add);
                    totalBelow = totalBelow.add(loss);
                }
            }
        }
        if (totalBelow.compareTo(props.belowCostMinDailyUsd()) >= 0) {
            dominant(belowCostByCashier, totalBelow).ifPresent(c -> hits
                    .computeIfAbsent(c, k -> new ArrayList<>())
                    .add("zararga sotuvlarning katta qismi (" + MoneyFormat.usd(belowCostByCashier.get(c)) + ")"));
        }

        // 5c. night sales concentrated on one cashier (today).
        Map<String, Long> nightByCashier = new HashMap<>();
        long totalNight = 0;
        for (Sale s : daySales) {
            if (!isBlank(s.getCashier()) && s.getCreatedAt() != null && isNight(s.getCreatedAt().getHour())) {
                nightByCashier.merge(s.getCashier(), 1L, Long::sum);
                totalNight++;
            }
        }
        if (totalNight >= props.nightSpikeMinCount()) {
            final long tn = totalNight;
            dominantCount(nightByCashier, tn).ifPresent(c -> hits
                    .computeIfAbsent(c, k -> new ArrayList<>())
                    .add("tungi sotuvlarning katta qismi (" + nightByCashier.get(c) + " ta)"));
        }

        // 5d. repeated cash shortfalls by the shift opener (trailing window).
        LocalDate baselineFrom = day.minusDays(props.baselineDays());
        Map<String, Integer> shortfalls = new HashMap<>();
        for (Shift sh : shifts.findAllByOrderByOpenedAtDesc()) {
            if (sh.getOpenedAt() == null || isBlank(sh.getOpenedBy())) {
                continue;
            }
            LocalDate d = sh.getOpenedAt().toLocalDate();
            if (d.isBefore(baselineFrom) || d.isAfter(day)) {
                continue;
            }
            BigDecimal exp = sh.getExpectedCash();
            BigDecimal cnt = sh.getCountedCash();
            if (exp != null && cnt != null && exp.subtract(cnt).signum() > 0) {
                shortfalls.merge(sh.getOpenedBy(), 1, Integer::sum);
            }
        }
        shortfalls.forEach((cashier, n) -> {
            if (n >= 2) {
                hits.computeIfAbsent(cashier, k -> new ArrayList<>())
                        .add("oxirgi davrda " + n + " marta kassada kamomad");
            }
        });

        // Emit one alert per cashier; critical when several sub-patterns trip.
        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : hits.entrySet()) {
            String cashier = e.getKey();
            List<String> reasons = e.getValue();
            String sev = reasons.size() >= 2 ? CRITICAL : WARN;
            String msg = "Kassir '" + cashier + "' bo'yicha shubhali pattern: "
                    + String.join("; ", reasons) + ".";
            out.add(new Candidate(sev, "cashier-anomaly",
                    "cashier-anomaly:" + day + ":" + cashier, day, msg,
                    json("cashier", cashier, "patterns", reasons)));
        }
        return out;
    }

    // ----------------------------------------------------------------- helpers

    private boolean isNight(int hour) {
        int start = props.nightStartHour();
        int end = props.nightEndHour();
        return hour >= start || hour < end;
    }

    /** productId -> current purchase price, for every product sold that day. */
    private Map<Long, BigDecimal> costByProduct(List<Sale> daySales) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Sale s : daySales) {
            for (SaleItem i : s.getItems()) {
                if (i.getProductId() != null) {
                    ids.add(i.getProductId());
                }
            }
        }
        Map<Long, BigDecimal> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Product p : products.findAllById(ids)) {
                map.put(p.getId(), p.getPurchasePrice() == null ? ZERO : p.getPurchasePrice());
            }
        }
        return map;
    }

    /** The key holding ≥70% of {@code total} (by amount), if any. */
    private static java.util.Optional<String> dominant(Map<String, BigDecimal> byKey, BigDecimal total) {
        if (total.signum() <= 0) {
            return java.util.Optional.empty();
        }
        return byKey.entrySet().stream()
                .filter(en -> en.getValue().multiply(HUNDRED)
                        .divide(total, 0, RoundingMode.HALF_UP).intValue() >= 70)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** The key holding ≥70% of {@code total} (by count), if any. */
    private static java.util.Optional<String> dominantCount(Map<String, Long> byKey, long total) {
        if (total <= 0) {
            return java.util.Optional.empty();
        }
        return byKey.entrySet().stream()
                .filter(en -> en.getValue() * 100.0 / total >= 70.0)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n == 0) {
            return 0;
        }
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String json(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        try {
            return mapper.writeValueAsString(m);
        } catch (Exception ex) {
            return null;
        }
    }
}
