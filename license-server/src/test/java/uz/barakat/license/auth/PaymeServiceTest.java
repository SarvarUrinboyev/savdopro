package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.license.domain.Payment;
import uz.barakat.license.domain.PaymentStatus;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.repository.PaymentRepository;

/**
 * The Payme transaction state machine: validation on Check/Create, a Perform
 * that confirms the payment (extends the subscription) exactly once, and
 * idempotent re-delivery on every leg. Amounts are in tiyin (so'm × 100), so
 * a 99 000 so'm plan must arrive as 9 900 000.
 */
@ExtendWith(MockitoExtension.class)
class PaymeServiceTest {

    private static final long PRICE_UZS = 99_000L;
    private static final long PRICE_TIYIN = PRICE_UZS * 100L;

    @Mock private PaymentRepository payments;
    @Mock private BillingService billing;
    private PaymeService service;

    @BeforeEach
    void setUp() {
        service = new PaymeService(payments, billing, "order_id");
    }

    @Test
    void checkPerformAllowsValidPendingOrder() {
        when(payments.findById(42L)).thenReturn(Optional.of(pending(42L)));

        Map<String, Object> r = service.checkPerformTransaction(check(42L, PRICE_TIYIN));

        assertThat(r.get("allow")).isEqualTo(true);
    }

    @Test
    void checkPerformRejectsWrongAmount() {
        when(payments.findById(42L)).thenReturn(Optional.of(pending(42L)));

        assertThatThrownBy(() -> service.checkPerformTransaction(check(42L, 5_000_000L)))
                .isInstanceOfSatisfying(PaymeException.class,
                        e -> assertThat(e.code()).isEqualTo(PaymeService.ERR_INVALID_AMOUNT));
    }

    @Test
    void checkPerformRejectsUnknownOrder() {
        when(payments.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkPerformTransaction(check(99L, PRICE_TIYIN)))
                .isInstanceOfSatisfying(PaymeException.class,
                        e -> assertThat(e.code()).isEqualTo(PaymeService.ERR_ORDER_NOT_FOUND));
    }

    @Test
    void createTransactionStampsTheOrderAndReturnsCreatedState() {
        Payment p = pending(42L);
        when(payments.findById(42L)).thenReturn(Optional.of(p));

        Map<String, Object> r = service.createTransaction(create("T1", 42L, PRICE_TIYIN, 1700L));

        assertThat(p.getPaymeTxId()).isEqualTo("T1");
        assertThat(p.getPaymeState()).isEqualTo(PaymeService.STATE_CREATED);
        assertThat(p.getPaymeCreateTime()).isEqualTo(1700L);
        assertThat(r.get("state")).isEqualTo(PaymeService.STATE_CREATED);
        assertThat(r.get("transaction")).isEqualTo("42");
        verify(payments).save(p);
    }

    @Test
    void createTransactionIsIdempotentForTheSameTxId() {
        Payment p = pending(42L);
        p.setPaymeTxId("T1");
        p.setPaymeState(PaymeService.STATE_CREATED);
        p.setPaymeCreateTime(1700L);
        when(payments.findById(42L)).thenReturn(Optional.of(p));

        Map<String, Object> r = service.createTransaction(create("T1", 42L, PRICE_TIYIN, 9999L));

        assertThat(r.get("state")).isEqualTo(PaymeService.STATE_CREATED);
        verify(payments, never()).save(p); // no re-stamp
    }

    @Test
    void createTransactionRejectsASecondTransactionForTheOrder() {
        Payment p = pending(42L);
        p.setPaymeTxId("T1");
        p.setPaymeState(PaymeService.STATE_CREATED);
        when(payments.findById(42L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.createTransaction(create("T2", 42L, PRICE_TIYIN, 1L)))
                .isInstanceOfSatisfying(PaymeException.class,
                        e -> assertThat(e.code()).isEqualTo(PaymeService.ERR_ORDER_STATE));
    }

    @Test
    void performTransactionConfirmsPaymentOnce() {
        Payment p = pending(42L);
        p.setPaymeTxId("T1");
        p.setPaymeState(PaymeService.STATE_CREATED);
        when(payments.findByPaymeTxId("T1")).thenReturn(Optional.of(p));

        Map<String, Object> r = service.performTransaction(Map.of("id", "T1"));

        assertThat(p.getPaymeState()).isEqualTo(PaymeService.STATE_PERFORMED);
        assertThat(r.get("state")).isEqualTo(PaymeService.STATE_PERFORMED);
        verify(billing).confirmPayment(42L, "payme:T1");
    }

    @Test
    void performTransactionIsIdempotentWhenAlreadyPerformed() {
        Payment p = pending(42L);
        p.setPaymeTxId("T1");
        p.setPaymeState(PaymeService.STATE_PERFORMED);
        p.setPaymePerformTime(123L);
        when(payments.findByPaymeTxId("T1")).thenReturn(Optional.of(p));

        Map<String, Object> r = service.performTransaction(Map.of("id", "T1"));

        assertThat(r.get("state")).isEqualTo(PaymeService.STATE_PERFORMED);
        verify(billing, never()).confirmPayment(anyLong(), anyString());
    }

    @Test
    void performTransactionRejectsUnknownTx() {
        when(payments.findByPaymeTxId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.performTransaction(Map.of("id", "nope")))
                .isInstanceOfSatisfying(PaymeException.class,
                        e -> assertThat(e.code()).isEqualTo(PaymeService.ERR_TX_NOT_FOUND));
    }

    @Test
    void cancelBeforePerformMovesToCancelledAndFailsPayment() {
        Payment p = pending(42L);
        p.setPaymeTxId("T1");
        p.setPaymeState(PaymeService.STATE_CREATED);
        when(payments.findByPaymeTxId("T1")).thenReturn(Optional.of(p));

        Map<String, Object> r = service.cancelTransaction(Map.of("id", "T1", "reason", 3));

        assertThat(p.getPaymeState()).isEqualTo(PaymeService.STATE_CANCELLED);
        assertThat(r.get("state")).isEqualTo(PaymeService.STATE_CANCELLED);
        verify(billing).markFailed(42L);
    }

    @Test
    void cancelAfterPerformMovesToCancelledAfter() {
        Payment p = pending(42L);
        p.setPaymeTxId("T1");
        p.setPaymeState(PaymeService.STATE_PERFORMED);
        when(payments.findByPaymeTxId("T1")).thenReturn(Optional.of(p));

        service.cancelTransaction(Map.of("id", "T1", "reason", 5));

        assertThat(p.getPaymeState()).isEqualTo(PaymeService.STATE_CANCELLED_AFTER);
        verify(billing).markFailed(42L);
    }

    @Test
    void checkTransactionReportsStateAndTimes() {
        Payment p = pending(42L);
        p.setPaymeTxId("T1");
        p.setPaymeState(PaymeService.STATE_PERFORMED);
        p.setPaymeCreateTime(100L);
        p.setPaymePerformTime(200L);
        when(payments.findByPaymeTxId("T1")).thenReturn(Optional.of(p));

        Map<String, Object> r = service.checkTransaction(Map.of("id", "T1"));

        assertThat(r.get("state")).isEqualTo(PaymeService.STATE_PERFORMED);
        assertThat(r.get("create_time")).isEqualTo(100L);
        assertThat(r.get("perform_time")).isEqualTo(200L);
        assertThat(r.get("cancel_time")).isEqualTo(0L);
        assertThat(r.get("transaction")).isEqualTo("42");
    }

    // ------------------------------------------------------------ helpers

    private static Payment pending(long id) {
        Payment p = new Payment();
        p.setId(id);
        p.setAmountUzs(PRICE_UZS);
        p.setStatus(PaymentStatus.PENDING);
        p.setPlan(SubscriptionPlan.BASIC);
        p.setMonths(1);
        return p;
    }

    private static Map<String, Object> check(long orderId, long amountTiyin) {
        return Map.of("amount", amountTiyin, "account", Map.of("order_id", String.valueOf(orderId)));
    }

    private static Map<String, Object> create(String txId, long orderId, long amountTiyin, long time) {
        return Map.of(
                "id", txId,
                "time", time,
                "amount", amountTiyin,
                "account", Map.of("order_id", String.valueOf(orderId)));
    }
}
