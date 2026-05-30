package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.CustomerDebt;
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.DebtPayment;
import uz.barakat.market.domain.Debtor;
import uz.barakat.market.domain.Expense;
import uz.barakat.market.domain.HomeExpense;
import uz.barakat.market.domain.ManagementCost;
import uz.barakat.market.domain.Order;
import uz.barakat.market.domain.Payment;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Shift;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.dto.CustomerDebtResponse;
import uz.barakat.market.dto.CustomerResponse;
import uz.barakat.market.dto.CustomerTransactionResponse;
import uz.barakat.market.dto.DebtPaymentResponse;
import uz.barakat.market.dto.DebtorResponse;
import uz.barakat.market.dto.ExpenseResponse;
import uz.barakat.market.dto.HomeExpenseResponse;
import uz.barakat.market.dto.ManagementCostResponse;
import uz.barakat.market.dto.OrderResponse;
import uz.barakat.market.dto.PaymentResponse;
import uz.barakat.market.dto.ProductResponse;
import uz.barakat.market.dto.ShiftResponse;
import uz.barakat.market.dto.StockMovementResponse;
import uz.barakat.market.dto.TerminalResponse;

/** Entity -> DTO conversions and the small money computations they need. */
public final class Mappers {

    private Mappers() {
    }

    public static ExpenseResponse expense(Expense e) {
        return new ExpenseResponse(e.getId(), e.getDate(), e.getName(), e.getAmount(),
                e.getPaymentType(), e.getCashAmount(), e.getNaqdAmount(), e.getCardAmount(),
                e.getCurrency(), e.getNote(), e.getCreatedAt());
    }

    public static HomeExpenseResponse homeExpense(HomeExpense e) {
        return new HomeExpenseResponse(e.getId(), e.getDate(), e.getName(), e.getAmount(),
                e.getPaymentType(), e.getCashAmount(), e.getNaqdAmount(), e.getCardAmount(),
                e.getCurrency(), e.getNote(), e.getCreatedAt());
    }

    public static OrderResponse order(Order o, LocalDate today) {
        return new OrderResponse(o.getId(), o.getOrderDate(), o.getDeliveryDate(), o.getName(),
                o.getSupplier(), o.getAmount(), o.isCompleted(), o.getCompletedAt(), o.getNote(),
                orderStatus(o, today));
    }

    /** Derived bucket for an order: COMPLETED / TODAY / OVERDUE / UPCOMING. */
    public static String orderStatus(Order o, LocalDate today) {
        if (o.isCompleted()) {
            return "COMPLETED";
        }
        LocalDate due = o.getDeliveryDate();
        if (due == null) {
            // No delivery date set yet — not due and not overdue (guards an NPE).
            return "UPCOMING";
        }
        if (due.isEqual(today)) {
            return "TODAY";
        }
        return due.isBefore(today) ? "OVERDUE" : "UPCOMING";
    }

    public static DebtorResponse debtor(Debtor d) {
        return new DebtorResponse(d.getId(), d.getDate(), d.getName(), d.getProductName(),
                d.getOriginalAmount(), d.getPaidAmount(),
                remaining(d.getOriginalAmount(), d.getPaidAmount()),
                percent(d.getPaidAmount(), d.getOriginalAmount()), d.isPaid(), d.getNote(),
                d.getCreatedAt());
    }

    public static CustomerDebtResponse customerDebt(CustomerDebt d) {
        return new CustomerDebtResponse(d.getId(), d.getDate(), d.getCustomerName(),
                d.getProductName(), d.getOriginalAmount(), d.getPaidAmount(),
                remaining(d.getOriginalAmount(), d.getPaidAmount()),
                percent(d.getPaidAmount(), d.getOriginalAmount()), d.isPaid(), d.getNote(),
                d.getCreatedAt());
    }

    public static DebtPaymentResponse debtPayment(DebtPayment p) {
        return new DebtPaymentResponse(p.getId(), p.getPaymentDate(), p.getAmount(),
                p.getEntryType(), p.getNote(), p.getCreatedAt());
    }

    public static ShiftResponse shift(Shift s, BigDecimal startingCash) {
        Long minutes = s.getClosedAt() == null
                ? null
                : ChronoUnit.MINUTES.between(s.getOpenedAt(), s.getClosedAt());
        return new ShiftResponse(s.getId(), s.getOpenedAt(), s.getClosedAt(), s.getOpenedBy(),
                s.getStatus(), minutes, startingCash);
    }

    public static TerminalResponse terminal(LocalDate date, BigDecimal humo, BigDecimal uzcard) {
        return new TerminalResponse(date, humo, uzcard, humo.add(uzcard));
    }

    public static ProductResponse product(Product p, String categoryName) {
        BigDecimal margin = p.getSalePrice().subtract(p.getPurchasePrice());
        BigDecimal stockValue = p.getPurchasePrice()
                .multiply(BigDecimal.valueOf(p.getQuantity()));
        return new ProductResponse(p.getId(), p.getName(), p.getBarcode(), p.getImei1(),
                p.getImei2(), p.getPurchasePrice(), p.getSalePrice(), p.getQuantity(),
                p.getCategoryId(), categoryName, p.getDescription(), p.getLowStockThreshold(),
                margin, stockValue, stockStatus(p),
                p.getMxikCode(), p.getVatRate(), p.getUnit(), p.getCreatedAt());
    }

    /** Derived stock bucket: OUT (0) / LOW (at or below threshold) / IN_STOCK. */
    public static String stockStatus(Product p) {
        if (p.getQuantity() <= 0) {
            return "OUT";
        }
        if (p.getLowStockThreshold() > 0 && p.getQuantity() <= p.getLowStockThreshold()) {
            return "LOW";
        }
        return "IN_STOCK";
    }

    public static StockMovementResponse stockMovement(StockMovement m) {
        return new StockMovementResponse(m.getId(), m.getDelta(), m.getResultingQuantity(),
                m.getReason(), m.getNote(), m.getCreatedAt());
    }

    /**
     * API view of a customer. {@code goodsTotal} / {@code paidTotal} are the
     * ledger sums; the balance is their difference (positive => owes us).
     */
    public static CustomerResponse customer(Customer c, BigDecimal goodsTotal,
                                            BigDecimal paidTotal, int transactionCount) {
        return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getAddress(),
                c.getNote(), goodsTotal, paidTotal, goodsTotal.subtract(paidTotal),
                transactionCount, c.getCreatedAt(),
                c.getPointsBalance(), c.getPointsTotalEarned());
    }

    public static CustomerTransactionResponse customerTransaction(CustomerTransaction t) {
        return new CustomerTransactionResponse(t.getId(), t.getDate(), t.getType(),
                t.getDescription(), t.getAmount(), t.getNote(), t.getCreatedAt());
    }

    public static ManagementCostResponse managementCost(ManagementCost c) {
        return new ManagementCostResponse(c.getId(), c.getDate(), c.getType(), c.getName(),
                c.getAmount(), c.getCurrency(), c.getNote(), c.getCreatedAt());
    }

    public static PaymentResponse payment(Payment p) {
        return new PaymentResponse(p.getId(), p.getDate(), p.getDirection(), p.getCategory(),
                p.getParty(), p.getAmount(), p.getMethod(), p.getCurrency(), p.getNote(),
                p.getCreatedAt(), "PAYMENT",
                p.getDiscountAmount(), p.getDiscountPercent(), p.getCustomerId());
    }

    /** Outstanding part of a debt, never negative. */
    public static BigDecimal remaining(BigDecimal original, BigDecimal paid) {
        BigDecimal left = original.subtract(paid);
        return left.signum() < 0 ? BigDecimal.ZERO : left;
    }

    /** Paid percentage, clamped to 0..100. */
    public static int percent(BigDecimal paid, BigDecimal original) {
        if (original == null || original.signum() <= 0) {
            return 0;
        }
        int value = paid.multiply(BigDecimal.valueOf(100))
                .divide(original, 0, RoundingMode.HALF_UP)
                .intValue();
        return Math.max(0, Math.min(100, value));
    }
}
