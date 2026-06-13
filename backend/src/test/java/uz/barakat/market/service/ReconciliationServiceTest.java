package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.OnlinePayment;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.OnlinePaymentRepository;
import uz.barakat.market.repository.ShopRepository;

/**
 * Online (Click/Payme) payments must reconcile to the debt ledger: a performed
 * payment with a ledger link reads as MATCHED, one without as UNRECONCILED, and
 * the repair action then credits it so it becomes MATCHED.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconciliationServiceTest {

    @Autowired ReconciliationService recon;
    @Autowired OnlinePaymentService onlinePayments;
    @Autowired OnlinePaymentRepository opRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired ShopRepository shops;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void onlinePaymentsReconcileAndUnreconciledCanBeCredited() {
        Long shopId = shops.findAll().stream().findFirst().orElseThrow().getId();
        TenantContext.setShopId(shopId);

        Customer c = new Customer();
        c.setName("Recon test mijoz");
        c = customerRepo.save(c);

        opRepo.save(online(shopId, c.getId(), "10", OnlinePayment.STATE_PERFORMED, 999L, "m1"));
        OnlinePayment unrec =
                opRepo.save(online(shopId, c.getId(), "5", OnlinePayment.STATE_PERFORMED, null, "u1"));

        LocalDate today = LocalDate.now();
        var r = recon.reconcile(today, today);
        assertThat(r.onlineSummary().matched()).isEqualTo(1);
        assertThat(r.onlineSummary().unreconciled()).isEqualTo(1);
        assertThat(r.onlineSummary().matchedUsd()).isEqualByComparingTo("10");

        // Repair the paid-but-not-credited one.
        assertThat(onlinePayments.creditUnreconciled(unrec.getId())).isTrue();

        var r2 = recon.reconcile(today, today);
        assertThat(r2.onlineSummary().unreconciled()).isEqualTo(0);
        assertThat(r2.onlineSummary().matched()).isEqualTo(2);

        // Re-crediting is a no-op (idempotent).
        assertThat(onlinePayments.creditUnreconciled(unrec.getId())).isFalse();
    }

    private static OnlinePayment online(Long shopId, Long customerId, String amount,
                                        int state, Long ledgerTxId, String txn) {
        OnlinePayment o = new OnlinePayment();
        o.setProvider("PAYME");
        o.setProviderTxnId(txn);
        o.setCustomerId(customerId);
        o.setShopId(shopId);
        o.setAmount(new BigDecimal(amount));
        o.setState(state);
        o.setCreateTimeMs(System.currentTimeMillis());
        o.setPerformTimeMs(System.currentTimeMillis());
        o.setLedgerTxId(ledgerTxId);
        return o;
    }
}
