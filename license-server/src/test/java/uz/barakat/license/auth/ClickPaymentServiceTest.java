package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

/**
 * The Click Prepare/Complete protocol: signature gate first, then order
 * validation, then (on a clean Complete) confirmation that extends the
 * subscription. Callbacks are signed with the same secret the service uses,
 * so a wrong signature, amount, or already-paid order is genuinely rejected.
 */
@ExtendWith(MockitoExtension.class)
class ClickPaymentServiceTest {

    private static final String SECRET = "click-secret";
    private static final String SVC = "svc";

    @Mock private uz.barakat.license.repository.PaymentRepository payments;
    @Mock private BillingService billing;
    private ClickPaymentService service;

    @BeforeEach
    void setUp() {
        service = new ClickPaymentService(payments, billing, SECRET);
    }

    @Test
    void prepareReturnsOkAndPrepareIdForValidPendingOrder() {
        when(payments.findById(42L)).thenReturn(Optional.of(payment(42L, 99_000L, PaymentStatus.PENDING)));

        Map<String, Object> r = service.prepare(signedPrepare("tx-1", "42", "99000"));

        assertThat(r.get("error")).isEqualTo(ClickPaymentService.OK);
        assertThat(r.get("merchant_prepare_id")).isEqualTo(42L);
        assertThat(r.get("click_trans_id")).isEqualTo("tx-1");
    }

    @Test
    void prepareRejectsBadSignatureBeforeTouchingState() {
        ClickCallback bad = new ClickCallback("tx-1", SVC, "pd", "42", null,
                "99000", "0", "0", "", "t", "WRONG-SIGN");

        Map<String, Object> r = service.prepare(bad);

        assertThat(r.get("error")).isEqualTo(ClickPaymentService.SIGN_FAILED);
        verifyNoInteractions(billing);
    }

    @Test
    void prepareRejectsAmountMismatch() {
        when(payments.findById(42L)).thenReturn(Optional.of(payment(42L, 99_000L, PaymentStatus.PENDING)));

        Map<String, Object> r = service.prepare(signedPrepare("tx-1", "42", "50000"));

        assertThat(r.get("error")).isEqualTo(ClickPaymentService.BAD_AMOUNT);
    }

    @Test
    void prepareRejectsAlreadyPaidOrder() {
        when(payments.findById(42L)).thenReturn(Optional.of(payment(42L, 99_000L, PaymentStatus.PAID)));

        Map<String, Object> r = service.prepare(signedPrepare("tx-1", "42", "99000"));

        assertThat(r.get("error")).isEqualTo(ClickPaymentService.ALREADY_PAID);
    }

    @Test
    void prepareRejectsUnknownOrder() {
        when(payments.findById(99L)).thenReturn(Optional.empty());

        Map<String, Object> r = service.prepare(signedPrepare("tx-1", "99", "99000"));

        assertThat(r.get("error")).isEqualTo(ClickPaymentService.ORDER_NOT_FOUND);
    }

    @Test
    void completeConfirmsPaymentAndExtendsSubscription() {
        when(payments.findById(42L)).thenReturn(Optional.of(payment(42L, 99_000L, PaymentStatus.PENDING)));

        Map<String, Object> r = service.complete(signedComplete("tx-1", "42", "42", "99000", "0"));

        assertThat(r.get("error")).isEqualTo(ClickPaymentService.OK);
        assertThat(r.get("merchant_confirm_id")).isEqualTo(42L);
        verify(billing).confirmPayment(42L, "tx-1");
    }

    @Test
    void completeIsIdempotentForAnAlreadyPaidOrder() {
        when(payments.findById(42L)).thenReturn(Optional.of(payment(42L, 99_000L, PaymentStatus.PAID)));

        Map<String, Object> r = service.complete(signedComplete("tx-1", "42", "42", "99000", "0"));

        assertThat(r.get("error")).isEqualTo(ClickPaymentService.OK);
        verify(billing, never()).confirmPayment(anyLong(), anyString());
    }

    @Test
    void completeMarksFailedWhenClickReportsCancellation() {
        when(payments.findById(42L)).thenReturn(Optional.of(payment(42L, 99_000L, PaymentStatus.PENDING)));

        Map<String, Object> r = service.complete(signedComplete("tx-1", "42", "42", "99000", "-9"));

        assertThat(r.get("error")).isEqualTo(-9);
        verify(billing).markFailed(42L);
        verify(billing, never()).confirmPayment(anyLong(), anyString());
    }

    // ------------------------------------------------------------ helpers

    private static Payment payment(long id, long amount, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setAmountUzs(amount);
        p.setStatus(status);
        p.setPlan(SubscriptionPlan.BASIC);
        p.setMonths(1);
        return p;
    }

    private static ClickCallback signedPrepare(String txId, String orderId, String amount) {
        ClickCallback unsigned = new ClickCallback(txId, SVC, "pd", orderId, null,
                amount, "0", "0", "", "2026-01-01 10:00:00", null);
        return withSign(unsigned, ClickSignature.expectedPrepare(unsigned, SECRET));
    }

    private static ClickCallback signedComplete(String txId, String orderId, String prepareId,
                                                String amount, String clickError) {
        ClickCallback unsigned = new ClickCallback(txId, SVC, "pd", orderId, prepareId,
                amount, "1", clickError, "", "2026-01-01 10:05:00", null);
        return withSign(unsigned, ClickSignature.expectedComplete(unsigned, SECRET));
    }

    private static ClickCallback withSign(ClickCallback c, String sign) {
        return new ClickCallback(c.clickTransId(), c.serviceId(), c.clickPaydocId(),
                c.merchantTransId(), c.merchantPrepareId(), c.amount(), c.action(),
                c.error(), c.errorNote(), c.signTime(), sign);
    }
}
