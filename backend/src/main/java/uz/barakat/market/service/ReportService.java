package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.CustomerDebt;
import uz.barakat.market.domain.Debtor;
import uz.barakat.market.domain.Expense;
import uz.barakat.market.domain.HomeExpense;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.domain.Shift;
import uz.barakat.market.dto.EndOfDayReport;
import uz.barakat.market.dto.ExpenseResponse;
import uz.barakat.market.dto.HomeExpenseResponse;
import uz.barakat.market.dto.OrderResponse;
import uz.barakat.market.dto.SalesSummary;
import uz.barakat.market.repository.CustomerDebtRepository;
import uz.barakat.market.repository.DebtorRepository;
import uz.barakat.market.repository.ExpenseRepository;
import uz.barakat.market.repository.HomeExpenseRepository;
import uz.barakat.market.repository.OrderRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.ShiftRepository;
import uz.barakat.market.telegram.TelegramService;

/** Builds the end-of-day report, the 80mm receipt text and the Telegram message. */
@Service
@Transactional
public class ReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int RECEIPT_WIDTH = 32;
    private static final String RULE = "-".repeat(RECEIPT_WIDTH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ReportService.class);

    private final ExpenseRepository expenses;
    private final HomeExpenseRepository homeExpenses;
    private final OrderRepository orders;
    private final DebtorRepository debtors;
    private final CustomerDebtRepository customerDebts;
    private final ShiftRepository shifts;
    private final BalanceService balanceService;
    private final TelegramService telegramService;
    private final MoneyConverter converter;
    private final SaleRepository sales;
    private final ProductRepository products;
    private final EmailService emailService;
    private final String reportEmailTo;

    public ReportService(ExpenseRepository expenses, HomeExpenseRepository homeExpenses,
                         OrderRepository orders, DebtorRepository debtors,
                         CustomerDebtRepository customerDebts, ShiftRepository shifts,
                         BalanceService balanceService, TelegramService telegramService,
                         MoneyConverter converter, SaleRepository sales,
                         ProductRepository products, EmailService emailService,
                         @org.springframework.beans.factory.annotation.Value(
                                 "${app.email.report-to:}") String reportEmailTo) {
        this.expenses = expenses;
        this.homeExpenses = homeExpenses;
        this.orders = orders;
        this.debtors = debtors;
        this.customerDebts = customerDebts;
        this.shifts = shifts;
        this.balanceService = balanceService;
        this.telegramService = telegramService;
        this.converter = converter;
        this.sales = sales;
        this.products = products;
        this.emailService = emailService;
        this.reportEmailTo = reportEmailTo == null ? "" : reportEmailTo.trim();
    }

    /** Full end-of-day report for a date, with the rendered receipt text. */
    @Transactional(readOnly = true)
    public EndOfDayReport forDate(LocalDate date) {
        List<Expense> marketList = expenses.findByDateOrderByIdDesc(date);
        List<HomeExpense> homeList = homeExpenses.findByDateOrderByIdDesc(date);

        BigDecimal marketKassa = sumUsd(marketList, Expense::getCashAmount, Expense::getCurrency);
        BigDecimal marketNaqd = sumUsd(marketList, Expense::getNaqdAmount, Expense::getCurrency);
        BigDecimal marketKarta = sumUsd(marketList, Expense::getCardAmount, Expense::getCurrency);
        BigDecimal marketTotal = sumUsd(marketList, Expense::getAmount, Expense::getCurrency);

        BigDecimal homeKassa = sumUsd(homeList, HomeExpense::getCashAmount, HomeExpense::getCurrency);
        BigDecimal homeNaqd = sumUsd(homeList, HomeExpense::getNaqdAmount, HomeExpense::getCurrency);
        BigDecimal homeKarta = sumUsd(homeList, HomeExpense::getCardAmount, HomeExpense::getCurrency);
        BigDecimal homeTotal = sumUsd(homeList, HomeExpense::getAmount, HomeExpense::getCurrency);

        BigDecimal startingCash = balanceService.startingCash(date);
        BigDecimal cashOut = marketNaqd.add(homeNaqd);
        BigDecimal estimatedCash = startingCash.subtract(cashOut);

        BigDecimal myDebtTotal = remainingTotal(
                debtors.findByPaidFalse(), Debtor::getOriginalAmount, Debtor::getPaidAmount);
        BigDecimal customerDebtTotal = remainingTotal(
                customerDebts.findByPaidFalse(), CustomerDebt::getOriginalAmount,
                CustomerDebt::getPaidAmount);

        List<OrderResponse> tomorrowOrders =
                orders.findByCompletedFalseAndDeliveryDateOrderByIdDesc(date.plusDays(1))
                        .stream().map(o -> Mappers.order(o, date)).toList();
        BigDecimal tomorrowTotal = tomorrowOrders.stream()
                .map(OrderResponse::amount).reduce(ZERO, BigDecimal::add);
        List<OrderResponse> overdueOrders =
                orders.findByCompletedFalseAndDeliveryDateLessThanOrderByDeliveryDateAsc(date)
                        .stream().map(o -> Mappers.order(o, date)).toList();

        List<ExpenseResponse> expenseDtos = marketList.stream().map(Mappers::expense).toList();
        List<HomeExpenseResponse> homeDtos = homeList.stream().map(Mappers::homeExpense).toList();

        SalesSummary salesSummary = salesFor(date);

        EndOfDayReport report = new EndOfDayReport(date, salesSummary,
                marketKassa, marketNaqd, marketKarta, marketTotal,
                homeKassa, homeNaqd, homeKarta, homeTotal,
                startingCash, cashOut, estimatedCash,
                myDebtTotal, customerDebtTotal,
                tomorrowOrders, tomorrowTotal, overdueOrders,
                expenseDtos, homeDtos, "");
        return new EndOfDayReport(date, salesSummary,
                marketKassa, marketNaqd, marketKarta, marketTotal,
                homeKassa, homeNaqd, homeKarta, homeTotal,
                startingCash, cashOut, estimatedCash,
                myDebtTotal, customerDebtTotal,
                tomorrowOrders, tomorrowTotal, overdueOrders,
                expenseDtos, homeDtos, renderReceipt(report));
    }

    /**
     * Builds today/that-date's report and pushes it to Telegram, plus an
     * email copy when OWNER_REPORT_EMAIL + SMTP are configured — the owner
     * still gets the daily numbers if Telegram is blocked on their network.
     */
    public EndOfDayReport sendToTelegram(LocalDate date) {
        EndOfDayReport report = forDate(date);
        String text = renderTelegram(report);
        telegramService.sendMessage(text);
        if (!reportEmailTo.isBlank() && emailService.isUsable()) {
            try {
                emailService.send(reportEmailTo,
                        "SavdoPRO kunlik hisobot — " + date.format(DATE), text);
            } catch (RuntimeException ex) {
                // Email nusxasi best-effort: Telegram yo'li asosiy kanal.
                log.warn("Daily report email failed: {}", ex.toString());
            }
        }
        return report;
    }

    // ---------------------------------------------------------- receipt (80mm)

    private String renderReceipt(EndOfDayReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append(centered("BARAKAT SUPERMARKET")).append('\n');
        sb.append(centered("* * SMENA HISOBOTI * *")).append('\n');
        sb.append(centered(r.date().format(DATE))).append('\n');
        shiftFor(r.date()).ifPresent(shift -> sb.append(centered(shiftRange(shift))).append('\n'));
        sb.append(RULE).append('\n');

        SalesSummary s = r.sales() == null ? SalesSummary.EMPTY : r.sales();
        sb.append("SAVDO\n");
        sb.append("  Cheklar: ").append(s.count()).append(" ta\n");
        sb.append(amountRow("Sof savdo", s.net()));
        sb.append(amountRow("Tannarx", s.cogs()));
        sb.append(amountRow("Sof foyda", s.profit()));
        sb.append(RULE).append('\n');

        sb.append("XARAJATLAR\n");
        sb.append(amountRow("Kassa", r.marketKassa()));
        sb.append(amountRow("Naqd", r.marketNaqd()));
        sb.append(amountRow("Karta", r.marketKarta()));
        sb.append(amountRow("Jami", r.marketTotal()));
        if (r.homeTotal().signum() > 0) {
            sb.append(RULE).append('\n').append("DO'KON XARAJATLARI\n");
            sb.append(amountRow("Kassa", r.homeKassa()));
            sb.append(amountRow("Naqd", r.homeNaqd()));
            sb.append(amountRow("Karta", r.homeKarta()));
            sb.append(amountRow("Jami", r.homeTotal()));
        }
        sb.append(RULE).append('\n').append("KASSA HOLATI\n");
        sb.append(amountRow("Ertalabgi", r.startingCash()));
        sb.append(amountRow("Chiqdi", r.cashOut()));
        sb.append(amountRow("Qoldiq", r.estimatedCash()));

        if (r.myDebtTotal().signum() > 0 || r.customerDebtTotal().signum() > 0) {
            sb.append(RULE).append('\n').append("QARZLAR\n");
            sb.append(amountRow("Mening", r.myDebtTotal()));
            sb.append(amountRow("Bizdan", r.customerDebtTotal()));
        }
        if (!r.tomorrowOrders().isEmpty()) {
            sb.append(RULE).append('\n').append("ERTAGA KELADI\n");
            for (OrderResponse o : r.tomorrowOrders()) {
                sb.append("  - ").append(o.name()).append('\n');
            }
            sb.append(amountRow("Jami", r.tomorrowOrdersTotal()));
        }
        if (!r.overdueOrders().isEmpty()) {
            sb.append(RULE).append('\n').append("KELMAGAN\n");
            for (OrderResponse o : r.overdueOrders()) {
                sb.append("  - ").append(o.name())
                        .append(" (").append(o.deliveryDate().format(DATE)).append(")\n");
            }
        }
        sb.append(RULE).append('\n');
        sb.append(centered("Smena muvaffaqiyatli yopildi")).append('\n');
        return sb.toString();
    }

    // -------------------------------------------------------- telegram message

    private String renderTelegram(EndOfDayReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("BARAKAT SUPERMARKET\n");
        sb.append("Smena hisoboti - ").append(r.date().format(DATE)).append("\n");
        shiftFor(r.date()).ifPresent(shift -> sb.append(shiftRange(shift)).append('\n'));

        SalesSummary s = r.sales() == null ? SalesSummary.EMPTY : r.sales();
        sb.append("\nSAVDO (BUGUN)\n");
        sb.append("- Cheklar: ").append(s.count()).append(" ta\n");
        sb.append("- Sof savdo: ").append(MoneyFormat.usd(s.net())).append('\n');
        if (s.refunded().signum() > 0) {
            sb.append("- Qaytarilgan: ").append(MoneyFormat.usd(s.refunded())).append('\n');
        }
        sb.append("- Tannarx (taxminiy): ").append(MoneyFormat.usd(s.cogs())).append('\n');
        sb.append("- Sof foyda (taxminiy): ").append(MoneyFormat.usd(s.profit())).append('\n');

        sb.append("\nSUPERMARKET XARAJATI\n");
        sb.append("- Kassadan: ").append(MoneyFormat.usd(r.marketKassa())).append('\n');
        sb.append("- Naqddan: ").append(MoneyFormat.usd(r.marketNaqd())).append('\n');
        sb.append("- Kartadan: ").append(MoneyFormat.usd(r.marketKarta())).append('\n');
        sb.append("- Jami: ").append(MoneyFormat.usd(r.marketTotal())).append('\n');

        if (r.homeTotal().signum() > 0) {
            sb.append("\nDO'KON XARAJATLARI\n");
            sb.append("- Naqddan: ").append(MoneyFormat.usd(r.homeNaqd())).append('\n');
            sb.append("- Kassadan: ").append(MoneyFormat.usd(r.homeKassa())).append('\n');
            sb.append("- Jami: ").append(MoneyFormat.usd(r.homeTotal())).append('\n');
        }

        sb.append("\nKASSA HOLATI\n");
        sb.append("- Ertalabgi balans: ").append(MoneyFormat.usd(r.startingCash())).append('\n');
        sb.append("- Chiqdi: ").append(MoneyFormat.usd(r.cashOut())).append('\n');
        sb.append("- Taxminiy qoldiq: ").append(MoneyFormat.usd(r.estimatedCash())).append('\n');

        sb.append("\nQARZLAR\n");
        sb.append("- Mening qarzlarim: ").append(MoneyFormat.usd(r.myDebtTotal())).append('\n');
        sb.append("- Bizdan qarzlar: ").append(MoneyFormat.usd(r.customerDebtTotal())).append('\n');

        if (!r.tomorrowOrders().isEmpty()) {
            sb.append("\nERTAGA KELADI\n");
            for (OrderResponse o : r.tomorrowOrders()) {
                sb.append("- ").append(o.name())
                        .append(" - ").append(MoneyFormat.usd(o.amount())).append('\n');
            }
            sb.append("- Jami: ").append(MoneyFormat.usd(r.tomorrowOrdersTotal())).append('\n');
        }
        if (!r.overdueOrders().isEmpty()) {
            sb.append("\nKELMAGAN BUYURTMALAR\n");
            for (OrderResponse o : r.overdueOrders()) {
                sb.append("- ").append(o.name())
                        .append(" (").append(o.deliveryDate().format(DATE)).append(")\n");
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------- sales

    /**
     * Aggregates one day's sales: transaction count, gross takings, refunds
     * and an estimated profit. Profit = net takings - cost of goods sold,
     * where COGS uses each product's current purchase price against the
     * quantity actually sold (sold = quantity - refundedQty). Deleted
     * products contribute zero cost, so profit is an upper-bound estimate.
     *
     * <p>Public so the interactive owner bot ({@code /bugun}) can reuse it.
     */
    public SalesSummary salesFor(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        Object[] row = sales.summaryBetween(from, to);
        if (row != null && row.length == 1 && row[0] instanceof Object[] inner) {
            row = inner;
        }
        long count = row != null && row.length > 0 && row[0] != null
                ? ((Number) row[0]).longValue() : 0L;
        BigDecimal gross = toBig(row != null && row.length > 1 ? row[1] : null);
        BigDecimal refunded = toBig(row != null && row.length > 2 ? row[2] : null);
        BigDecimal net = gross.subtract(refunded);

        List<Sale> daySales = sales.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
        Map<Long, BigDecimal> cost = costByProduct(daySales);

        BigDecimal cogs = ZERO;
        for (Sale s : daySales) {
            for (SaleItem i : s.getItems()) {
                int sold = i.getQuantity() - i.getRefundedQty();
                if (sold <= 0) {
                    continue;
                }
                // Prefer the cost frozen on the line at sale time (V37); fall back
                // to the product's current cost for legacy lines without a snapshot.
                BigDecimal unitCost = i.getCostAtSaleUzs() != null
                        ? i.getCostAtSaleUzs()
                        : cost.getOrDefault(i.getProductId(), ZERO);
                cogs = cogs.add(unitCost.multiply(BigDecimal.valueOf(sold)));
            }
        }
        BigDecimal profit = net.subtract(cogs);
        return new SalesSummary(count, gross, refunded, net, cogs, profit);
    }

    /** Maps productId -> current purchase price for every product sold that day. */
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

    private static BigDecimal toBig(Object o) {
        if (o == null) {
            return ZERO;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(String.valueOf(o)).setScale(2, RoundingMode.HALF_UP);
    }

    // ----------------------------------------------------------------- helpers

    private java.util.Optional<Shift> shiftFor(LocalDate date) {
        return shifts.findAllByOrderByOpenedAtDesc().stream()
                .filter(s -> s.getOpenedAt().toLocalDate().equals(date))
                .findFirst();
    }

    private static String shiftRange(Shift shift) {
        String open = shift.getOpenedAt().format(TIME);
        String close = shift.getClosedAt() == null ? "..." : shift.getClosedAt().format(TIME);
        return open + " - " + close;
    }

    private static String amountRow(String label, BigDecimal value) {
        String left = "  " + label;
        String right = MoneyFormat.usd(value);
        int gap = Math.max(1, RECEIPT_WIDTH - left.length() - right.length());
        return left + " ".repeat(gap) + right + "\n";
    }

    private static String centered(String text) {
        int gap = Math.max(0, (RECEIPT_WIDTH - text.length()) / 2);
        return " ".repeat(gap) + text;
    }

    /** Sums a money field across records, converting each entry to USD. */
    private <T> BigDecimal sumUsd(List<T> list, Function<T, BigDecimal> field,
                                  Function<T, Currency> currency) {
        return list.stream()
                .map(t -> converter.toUsd(field.apply(t), currency.apply(t)))
                .reduce(ZERO, BigDecimal::add);
    }

    private static <T> BigDecimal remainingTotal(
            List<T> list, Function<T, BigDecimal> original, Function<T, BigDecimal> paid) {
        return list.stream()
                .map(t -> Mappers.remaining(original.apply(t), paid.apply(t)))
                .reduce(ZERO, BigDecimal::add);
    }
}
