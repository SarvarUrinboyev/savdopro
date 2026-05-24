package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.CustomerTxType;
import uz.barakat.market.domain.Expense;
import uz.barakat.market.domain.HomeExpense;
import uz.barakat.market.domain.Payment;
import uz.barakat.market.domain.PaymentCategory;
import uz.barakat.market.domain.PaymentDirection;
import uz.barakat.market.domain.PaymentType;
import uz.barakat.market.dto.PaymentRequest;
import uz.barakat.market.dto.PaymentResponse;
import uz.barakat.market.dto.PaymentSummary;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.CustomerTransactionRepository;
import uz.barakat.market.repository.ExpenseRepository;
import uz.barakat.market.repository.HomeExpenseRepository;
import uz.barakat.market.repository.PaymentRepository;

/**
 * The payment journal ("To'lovlar jurnali"): every money movement.
 *
 * <p>The list is a UNION of three sources, presented as a single timeline:
 * <ul>
 *   <li>Direct {@link Payment} rows (source = {@code PAYMENT}).</li>
 *   <li>{@link Expense} / {@link HomeExpense} rows projected as outgoing
 *       payments (source = {@code EXPENSE} / {@code HOME_EXPENSE}).</li>
 *   <li>{@link CustomerTransaction} PAYMENT rows projected as incoming
 *       payments (source = {@code CUSTOMER}).</li>
 * </ul>
 * Only {@code PAYMENT} rows are editable / deletable through this endpoint;
 * the others are read-only mirrors of their owning page.
 */
@Service
@Transactional
public class PaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final HomeExpenseRepository homeExpenses;
    private final CustomerTransactionRepository customerTransactions;
    private final CustomerRepository customers;
    private final MoneyConverter converter;

    public PaymentService(PaymentRepository payments,
                          ExpenseRepository expenses,
                          HomeExpenseRepository homeExpenses,
                          CustomerTransactionRepository customerTransactions,
                          CustomerRepository customers,
                          MoneyConverter converter) {
        this.payments = payments;
        this.expenses = expenses;
        this.homeExpenses = homeExpenses;
        this.customerTransactions = customerTransactions;
        this.customers = customers;
        this.converter = converter;
    }

    @Transactional(readOnly = true)
    public PaymentSummary summary(LocalDate from, LocalDate to) {
        List<PaymentResponse> rows = new ArrayList<>();

        // 1) native payment rows
        payments.findByDateBetweenOrderByDateDescIdDesc(from, to)
                .forEach(p -> rows.add(Mappers.payment(p)));

        // 2) market expenses -> outgoing
        expenses.findByDateBetweenOrderByDateDescIdDesc(from, to)
                .forEach(e -> rows.add(fromExpense(e)));

        // 3) home / shop expenses -> outgoing
        homeExpenses.findByDateBetweenOrderByDateDescIdDesc(from, to)
                .forEach(e -> rows.add(fromHomeExpense(e)));

        // 4) customer payment-ledger lines -> incoming
        List<CustomerTransaction> custPayments = customerTransactions
                .findByTypeAndDateBetweenOrderByDateDescIdDesc(
                        CustomerTxType.PAYMENT, from, to);
        if (!custPayments.isEmpty()) {
            Map<Long, String> custNames = customers.findAll().stream()
                    .collect(Collectors.toMap(Customer::getId, Customer::getName));
            custPayments.forEach(tx -> rows.add(fromCustomerPayment(tx, custNames)));
        }

        // Re-sort the union by date desc, then by id desc within same date.
        rows.sort(Comparator
                .comparing(PaymentResponse::date).reversed()
                .thenComparing((PaymentResponse r) -> r.id() == null ? 0L : r.id())
                .reversed());

        // Aggregate totals in USD.
        BigDecimal incoming = ZERO;
        BigDecimal outgoing = ZERO;
        for (PaymentResponse r : rows) {
            BigDecimal usd = converter.toUsd(r.amount(), r.currency());
            if (r.direction() == PaymentDirection.INCOMING) {
                incoming = incoming.add(usd);
            } else {
                outgoing = outgoing.add(usd);
            }
        }
        return new PaymentSummary(from, to, incoming, outgoing,
                incoming.subtract(outgoing), rows);
    }

    public PaymentResponse create(PaymentRequest request) {
        Payment payment = new Payment();
        apply(payment, request);
        Payment saved = payments.save(payment);
        // Phase 4.4: award loyalty points on every INCOMING payment that
        // is attributed to a customer. Points formula = 1 per
        // POINTS_PER_UZS so the cashier never has to do mental math.
        // Walk-in sales (customerId == null) skip this branch entirely.
        if (saved.getDirection() == PaymentDirection.INCOMING
                && saved.getCustomerId() != null) {
            awardLoyaltyPoints(saved);
        }
        return Mappers.payment(saved);
    }

    public PaymentResponse update(Long id, PaymentRequest request) {
        Payment payment = find(id);
        apply(payment, request);
        return Mappers.payment(payments.save(payment));
    }

    /** 1 point per 1,000 UZS (or 100 UZS-equivalent for USD sales). */
    private static final long POINTS_PER_UZS  = 1_000L;
    private static final long POINTS_PER_USD  = 10L;     // ~12 500 UZS ~ 12 points

    private void awardLoyaltyPoints(Payment payment) {
        long earned = computePoints(payment.getAmount(), payment.getCurrency());
        if (earned <= 0) return;
        Customer customer = customers.findById(payment.getCustomerId()).orElse(null);
        if (customer == null) return;
        customer.setPointsBalance(customer.getPointsBalance() + earned);
        customer.setPointsTotalEarned(customer.getPointsTotalEarned() + earned);
        customers.save(customer);

        CustomerTransaction row = new CustomerTransaction();
        row.setCustomerId(customer.getId());
        row.setDate(payment.getDate());
        row.setType(CustomerTxType.PAYMENT);
        row.setDescription("Loyalty: " + earned + " ball");
        row.setAmount(BigDecimal.ZERO);   // pure points row, no cash movement
        row.setPointsDelta(earned);
        customerTransactions.save(row);
    }

    private static long computePoints(BigDecimal amount, Currency currency) {
        if (amount == null || amount.signum() <= 0) return 0L;
        long divisor = currency == Currency.USD ? POINTS_PER_USD : POINTS_PER_UZS;
        return amount.divide(BigDecimal.valueOf(divisor),
                0, java.math.RoundingMode.FLOOR).longValueExact();
    }

    public void delete(Long id) {
        payments.delete(find(id));
    }

    /**
     * Distinct party names previously used for the given category. Powers
     * the supplier / worker autocomplete in the payment modal — same UX
     * pattern as the customer list, but sourced from past entries.
     */
    @Transactional(readOnly = true)
    public List<String> distinctParties(PaymentCategory category) {
        return payments.findDistinctParties(category);
    }

    // --------------------------------------------------------------- mappers

    private static PaymentResponse fromExpense(Expense e) {
        // Virtual rows have no discount / customer attribution.
        return new PaymentResponse(
                e.getId(), e.getDate(), PaymentDirection.OUTGOING,
                PaymentCategory.OTHER, e.getName(),
                e.getAmount(), e.getPaymentType(),
                e.getCurrency() != null ? e.getCurrency() : Currency.UZS,
                null, e.getCreatedAt(), "EXPENSE",
                null, null, null);
    }

    private static PaymentResponse fromHomeExpense(HomeExpense e) {
        return new PaymentResponse(
                e.getId(), e.getDate(), PaymentDirection.OUTGOING,
                PaymentCategory.OTHER, e.getName(),
                e.getAmount(), e.getPaymentType(),
                e.getCurrency() != null ? e.getCurrency() : Currency.UZS,
                null, e.getCreatedAt(), "HOME_EXPENSE",
                null, null, null);
    }

    private static PaymentResponse fromCustomerPayment(
            CustomerTransaction tx, Map<Long, String> custNames) {
        // Customer ledger is tracked in USD. Method defaults to NAQD because the
        // ledger doesn't yet capture how the money arrived.
        String party = custNames.getOrDefault(tx.getCustomerId(), "Mijoz");
        return new PaymentResponse(
                tx.getId(), tx.getDate(), PaymentDirection.INCOMING,
                PaymentCategory.CUSTOMER, party,
                tx.getAmount(), PaymentType.NAQD, Currency.USD,
                tx.getNote(), tx.getCreatedAt(), "CUSTOMER",
                null, null, tx.getCustomerId());
    }

    // --------------------------------------------------------------- helpers

    private static void apply(Payment payment, PaymentRequest request) {
        payment.setDate(request.date() != null ? request.date() : LocalDate.now());
        payment.setDirection(request.direction());
        payment.setCategory(request.category());
        payment.setParty(blankToNull(request.party()));
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        payment.setCurrency(request.currency() != null ? request.currency() : Currency.UZS);
        payment.setNote(blankToNull(request.note()));
        // Discount + loyalty (Phase 4.4) — null-safe so old clients that
        // don't send these fields keep working.
        payment.setDiscountAmount(request.discountAmount() != null
                ? request.discountAmount() : BigDecimal.ZERO);
        payment.setDiscountPercent(request.discountPercent() != null
                ? request.discountPercent() : BigDecimal.ZERO);
        payment.setCustomerId(request.customerId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Payment find(Long id) {
        return payments.findById(id).orElseThrow(() -> NotFoundException.of("To'lov", id));
    }
}
