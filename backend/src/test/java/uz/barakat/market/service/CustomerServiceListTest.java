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
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.CustomerTxType;
import uz.barakat.market.dto.CustomerResponse;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.CustomerTransactionRepository;
import uz.barakat.market.repository.ShopRepository;

/**
 * The customer list builds each balance from a single GROUP BY aggregate
 * (aggregateLedgerTotals) rather than loading the whole ledger table. These
 * tests pin the arithmetic end-to-end against H2: goods/paid/balance/count
 * must match the per-transaction computation, and a customer with no ledger
 * lines must read as all-zero (the null-aggregate branch).
 */
@SpringBootTest
@ActiveProfiles("test")
class CustomerServiceListTest {

    @Autowired private CustomerService service;
    @Autowired private CustomerRepository customers;
    @Autowired private CustomerTransactionRepository transactions;
    @Autowired private ShopRepository shops;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private Long seedCustomer(String name) {
        Customer c = new Customer();
        c.setName(name);
        return customers.save(c).getId();
    }

    private void seedTx(Long customerId, CustomerTxType type, String amount) {
        CustomerTransaction t = new CustomerTransaction();
        t.setCustomerId(customerId);
        t.setType(type);
        t.setAmount(new BigDecimal(amount));
        t.setDate(LocalDate.now());
        transactions.save(t);
    }

    private CustomerResponse rowFor(Long customerId) {
        return service.list().stream()
                .filter(r -> r.id().equals(customerId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("customer missing from list()"));
    }

    @Test
    void balanceIsGoodsMinusPaymentsFromAggregate() {
        Long shopId = shops.findAll().stream().findFirst().orElseThrow().getId();
        TenantContext.setShopId(shopId);

        Long cid = seedCustomer("Aggregate Ledger " + System.nanoTime());
        seedTx(cid, CustomerTxType.GOODS, "100");
        seedTx(cid, CustomerTxType.GOODS, "50");
        seedTx(cid, CustomerTxType.PAYMENT, "60");

        CustomerResponse r = rowFor(cid);
        assertThat(r.goodsTotal()).isEqualByComparingTo("150");
        assertThat(r.paidTotal()).isEqualByComparingTo("60");
        assertThat(r.balance()).isEqualByComparingTo("90"); // 150 - 60
        assertThat(r.transactionCount()).isEqualTo(3);
    }

    @Test
    void customerWithoutLedgerReadsAsZero() {
        Long shopId = shops.findAll().stream().findFirst().orElseThrow().getId();
        TenantContext.setShopId(shopId);

        Long cid = seedCustomer("No Ledger " + System.nanoTime());

        CustomerResponse r = rowFor(cid);
        assertThat(r.goodsTotal()).isEqualByComparingTo("0");
        assertThat(r.paidTotal()).isEqualByComparingTo("0");
        assertThat(r.balance()).isEqualByComparingTo("0");
        assertThat(r.transactionCount()).isZero();
    }
}
