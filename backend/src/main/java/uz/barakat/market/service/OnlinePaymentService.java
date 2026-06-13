package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.CustomerTxType;
import uz.barakat.market.domain.OnlinePayment;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.payment.PaymeException;
import uz.barakat.market.payment.PaymentProperties;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.CustomerTransactionRepository;
import uz.barakat.market.repository.OnlinePaymentRepository;

/**
 * Online debt repayment via Click and Payme. Two responsibilities:
 *
 * <ol>
 *   <li><b>Link generation</b> (authenticated): builds a hosted-checkout URL
 *       for a customer's debt. The ledger is in USD; the gateways are in
 *       UZS, so the debt is converted at the current rate.</li>
 *   <li><b>Provider callbacks</b> (unauthenticated webhooks): the Payme
 *       JSON-RPC state machine and the Click prepare/complete flow. Every
 *       attempt is persisted so callbacks are idempotent — a repeated
 *       perform returns the same result instead of crediting twice.</li>
 * </ol>
 *
 * <p>The credited USD value is frozen on the {@link OnlinePayment} when the
 * transaction is created, so a later FX-rate change cannot move it.</p>
 *
 * <p>This class lives in the {@code service} package on purpose: the tenant
 * filter aspect advises it, so authenticated link generation is shop-scoped
 * while the webhooks (no tenant context) resolve customers by absolute id.</p>
 */
@Service
@Transactional
public class OnlinePaymentService {

    private static final Logger log = LoggerFactory.getLogger(OnlinePaymentService.class);
    private static final String PAYME = "PAYME";
    private static final String CLICK = "CLICK";

    private final PaymentProperties properties;
    private final OnlinePaymentRepository payments;
    private final CustomerRepository customers;
    private final CustomerTransactionRepository ledger;
    private final MoneyConverter converter;

    public OnlinePaymentService(PaymentProperties properties,
                                OnlinePaymentRepository payments,
                                CustomerRepository customers,
                                CustomerTransactionRepository ledger,
                                MoneyConverter converter) {
        this.properties = properties;
        this.payments = payments;
        this.customers = customers;
        this.ledger = ledger;
        this.converter = converter;
    }

    // ============================================================ link gen

    public boolean paymeEnabled() {
        return properties.payme().isUsable();
    }

    public boolean clickEnabled() {
        return properties.click().isUsable();
    }

    /** Current outstanding debt (USD) for a customer; 0 if none. */
    @Transactional(readOnly = true)
    public BigDecimal debtUsd(Long customerId) {
        BigDecimal balance = BigDecimal.ZERO;
        for (CustomerTransaction tx : ledger.findByCustomerIdOrderByDateDescIdDesc(customerId)) {
            balance = tx.getType() == CustomerTxType.GOODS
                    ? balance.add(tx.getAmount())
                    : balance.subtract(tx.getAmount());
        }
        return balance.max(BigDecimal.ZERO);
    }

    /** Suggested charge in UZS so'm for a customer's debt (for the link UI). */
    @Transactional(readOnly = true)
    public long suggestedSom(Long customerId) {
        return usdToSom(debtUsd(customerId));
    }

    /**
     * Builds a hosted-checkout link. {@code provider} is "payme" or "click";
     * {@code amountSom} is the charge in UZS so'm (must be &gt; 0). The
     * customer lookup is tenant-scoped (authenticated caller).
     */
    @Transactional(readOnly = true)
    public String generateLink(Long customerId, String provider, long amountSom) {
        Customer c = customers.findById(customerId)
                .orElseThrow(() -> NotFoundException.of("Mijoz", customerId));
        if (amountSom <= 0) {
            throw new BadRequestException("Summa 0 dan katta bo'lishi kerak");
        }
        return switch (provider == null ? "" : provider.toLowerCase()) {
            case "payme" -> paymeLink(c.getId(), amountSom);
            case "click" -> clickLink(c.getId(), amountSom);
            default -> throw new BadRequestException("Noma'lum provayder: " + provider);
        };
    }

    private String paymeLink(Long customerId, long amountSom) {
        PaymentProperties.Payme p = properties.payme();
        if (!p.isUsable()) {
            throw new BadRequestException("Payme sozlanmagan");
        }
        long tiyin = amountSom * 100L;
        String raw = "m=" + p.merchantId()
                + ";ac." + p.account() + "=" + customerId
                + ";a=" + tiyin;
        String token = Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return p.checkoutUrl().replaceAll("/+$", "") + "/" + token;
    }

    private String clickLink(Long customerId, long amountSom) {
        PaymentProperties.Click c = properties.click();
        if (!c.isUsable()) {
            throw new BadRequestException("Click sozlanmagan");
        }
        return c.checkoutUrl().replaceAll("/+$", "")
                + "?service_id=" + c.serviceId()
                + "&merchant_id=" + c.merchantId()
                + "&amount=" + amountSom
                + "&transaction_param=" + customerId;
    }

    // ====================================================== money helpers

    /** USD ledger value -> whole UZS so'm at the current rate. */
    public long usdToSom(BigDecimal usd) {
        if (usd == null || usd.signum() <= 0) {
            return 0L;
        }
        return usd.multiply(converter.usdToUzs()).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** UZS so'm -> USD ledger value at the current rate. */
    public BigDecimal somToUsd(long som) {
        BigDecimal rate = converter.usdToUzs();
        if (rate.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(som).divide(rate, 2, RoundingMode.HALF_UP);
    }

    // ============================================================== Payme

    /** CheckPerformTransaction: validate the account + amount are payable. */
    @Transactional(readOnly = true)
    public void paymeCheckPerform(String account, long amountTiyin) {
        requirePaymeUsable();
        resolveCustomer(account); // throws CUSTOMER_NOT_FOUND if missing
        if (amountTiyin <= 0) {
            throw new PaymeException(PaymeException.INVALID_AMOUNT, "Noto'g'ri summa");
        }
    }

    /** CreateTransaction: persist (idempotently) a created transaction. */
    public OnlinePayment paymeCreate(String paymeTxnId, String account,
                                     long amountTiyin, long timeMs) {
        requirePaymeUsable();
        OnlinePayment existing =
                payments.findByProviderAndProviderTxnId(PAYME, paymeTxnId).orElse(null);
        if (existing != null) {
            if (existing.getState() != OnlinePayment.STATE_CREATED) {
                throw new PaymeException(PaymeException.CANNOT_PERFORM,
                        "Tranzaksiya holati noto'g'ri");
            }
            return existing;
        }
        if (amountTiyin <= 0) {
            throw new PaymeException(PaymeException.INVALID_AMOUNT, "Noto'g'ri summa");
        }
        Customer c = resolveCustomer(account);
        OnlinePayment op = new OnlinePayment();
        op.setProvider(PAYME);
        op.setProviderTxnId(paymeTxnId);
        op.setCustomerId(c.getId());
        op.setShopId(c.getShopId());
        op.setAmount(somToUsd(amountTiyin / 100L)); // freeze USD value now
        op.setState(OnlinePayment.STATE_CREATED);
        op.setCreateTimeMs(timeMs);
        return payments.save(op);
    }

    /** PerformTransaction: credit the debt once, idempotently. */
    public OnlinePayment paymePerform(String paymeTxnId) {
        OnlinePayment op = payments.findByProviderAndProviderTxnId(PAYME, paymeTxnId)
                .orElseThrow(() -> new PaymeException(
                        PaymeException.TXN_NOT_FOUND, "Tranzaksiya topilmadi"));
        if (op.getState() == OnlinePayment.STATE_PERFORMED) {
            return op; // already performed — idempotent
        }
        if (op.getState() != OnlinePayment.STATE_CREATED) {
            throw new PaymeException(PaymeException.CANNOT_PERFORM, "Holati noto'g'ri");
        }
        creditDebt(op, "Payme");
        op.setState(OnlinePayment.STATE_PERFORMED);
        op.setPerformTimeMs(System.currentTimeMillis());
        return payments.save(op);
    }

    /** CancelTransaction: move to a cancelled state. */
    public OnlinePayment paymeCancel(String paymeTxnId, int reason) {
        OnlinePayment op = payments.findByProviderAndProviderTxnId(PAYME, paymeTxnId)
                .orElseThrow(() -> new PaymeException(
                        PaymeException.TXN_NOT_FOUND, "Tranzaksiya topilmadi"));
        if (op.getState() == OnlinePayment.STATE_PERFORMED) {
            op.setState(OnlinePayment.STATE_CANCELLED_AFTER_PERFORM);
        } else if (op.getState() == OnlinePayment.STATE_CREATED) {
            op.setState(OnlinePayment.STATE_CANCELLED);
        }
        op.setReason(reason);
        op.setCancelTimeMs(System.currentTimeMillis());
        return payments.save(op);
    }

    @Transactional(readOnly = true)
    public OnlinePayment paymeFind(String paymeTxnId) {
        return payments.findByProviderAndProviderTxnId(PAYME, paymeTxnId)
                .orElseThrow(() -> new PaymeException(
                        PaymeException.TXN_NOT_FOUND, "Tranzaksiya topilmadi"));
    }

    private Customer resolveCustomer(String account) {
        Long id = parseLong(account);
        Customer c = id == null ? null : customers.findById(id).orElse(null);
        if (c == null) {
            throw new PaymeException(PaymeException.CUSTOMER_NOT_FOUND, "Mijoz topilmadi");
        }
        return c;
    }

    private void requirePaymeUsable() {
        if (!properties.payme().isUsable()) {
            throw new PaymeException(PaymeException.CUSTOMER_NOT_FOUND, "Payme sozlanmagan");
        }
    }

    // ============================================================== Click

    /** Click Prepare: validate + persist a created transaction; returns it. */
    public OnlinePayment clickPrepare(String account, long amountSom, String clickTransId) {
        if (!properties.click().isUsable()) {
            throw new BadRequestException("Click sozlanmagan");
        }
        OnlinePayment existing =
                payments.findByProviderAndProviderTxnId(CLICK, clickTransId).orElse(null);
        if (existing != null) {
            return existing;
        }
        Long id = parseLong(account);
        Customer c = id == null ? null : customers.findById(id).orElse(null);
        if (c == null) {
            throw new BadRequestException("Mijoz topilmadi");
        }
        if (amountSom <= 0) {
            throw new BadRequestException("Noto'g'ri summa");
        }
        OnlinePayment op = new OnlinePayment();
        op.setProvider(CLICK);
        op.setProviderTxnId(clickTransId);
        op.setCustomerId(c.getId());
        op.setShopId(c.getShopId());
        op.setAmount(somToUsd(amountSom));
        op.setState(OnlinePayment.STATE_CREATED);
        op.setCreateTimeMs(System.currentTimeMillis());
        return payments.save(op);
    }

    /** Click Complete: credit (confirm) or cancel the prepared transaction. */
    public OnlinePayment clickComplete(String clickTransId, boolean confirm) {
        OnlinePayment op = payments.findByProviderAndProviderTxnId(CLICK, clickTransId)
                .orElseThrow(() -> new BadRequestException("Tranzaksiya topilmadi"));
        if (!confirm) {
            if (op.getState() == OnlinePayment.STATE_CREATED) {
                op.setState(OnlinePayment.STATE_CANCELLED);
                op.setCancelTimeMs(System.currentTimeMillis());
            }
            return payments.save(op);
        }
        if (op.getState() == OnlinePayment.STATE_PERFORMED) {
            return op; // idempotent
        }
        creditDebt(op, "Click");
        op.setState(OnlinePayment.STATE_PERFORMED);
        op.setPerformTimeMs(System.currentTimeMillis());
        return payments.save(op);
    }

    public Optional<OnlinePayment> findClick(String clickTransId) {
        return payments.findByProviderAndProviderTxnId(CLICK, clickTransId);
    }

    // ========================================================= reconciliation

    /**
     * Repairs a paid-but-not-credited online payment: writes the missing
     * ledger PAYMENT row so the customer's debt finally reflects the money
     * received. Shop-guarded (online_payments is not tenant-filtered) and
     * idempotent — a no-op unless the txn is PERFORMED with no ledger link.
     * Returns true if a credit was written.
     */
    public boolean creditUnreconciled(Long onlinePaymentId) {
        OnlinePayment op = payments.findById(onlinePaymentId)
                .orElseThrow(() -> NotFoundException.of("Onlayn to'lov", onlinePaymentId));
        Long shop = TenantContext.currentShopId();
        if (shop != null && !shop.equals(op.getShopId())) {
            throw NotFoundException.of("Onlayn to'lov", onlinePaymentId);
        }
        if (op.getState() != OnlinePayment.STATE_PERFORMED || op.getLedgerTxId() != null) {
            return false;
        }
        creditDebt(op, op.getProvider());
        payments.save(op);
        return true;
    }

    // ============================================================ crediting

    /**
     * Writes one PAYMENT row into the customer ledger for this online
     * payment, exactly once. The shop id is set explicitly because the
     * webhook has no tenant context for {@code @PrePersist} to read.
     */
    private void creditDebt(OnlinePayment op, String providerLabel) {
        if (op.getLedgerTxId() != null) {
            return; // already credited
        }
        Customer c = customers.findById(op.getCustomerId()).orElse(null);
        if (c == null) {
            log.warn("Online payment {} has no customer {}", op.getId(), op.getCustomerId());
            return;
        }
        CustomerTransaction tx = new CustomerTransaction();
        tx.setCustomerId(c.getId());
        tx.setShopId(c.getShopId());
        tx.setDate(LocalDate.now());
        tx.setType(CustomerTxType.PAYMENT);
        tx.setAmount(op.getAmount());
        tx.setDescription("Onlayn to'lov (" + providerLabel + ")");
        CustomerTransaction saved = ledger.save(tx);
        op.setLedgerTxId(saved.getId());
        log.info("Online payment {} credited {} to customer {}",
                op.getId(), op.getAmount(), c.getId());
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
