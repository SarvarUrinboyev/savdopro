package uz.barakat.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uz.barakat.market.domain.CustomerDebt;
import uz.barakat.market.domain.DebtEntryType;
import uz.barakat.market.domain.DebtPayment;
import uz.barakat.market.domain.Debtor;
import uz.barakat.market.dto.CustomerDebtRequest;
import uz.barakat.market.dto.CustomerDebtResponse;
import uz.barakat.market.dto.DebtPaymentRequest;
import uz.barakat.market.dto.DebtPaymentResponse;
import uz.barakat.market.dto.DebtSummary;
import uz.barakat.market.dto.DebtorRequest;
import uz.barakat.market.dto.DebtorResponse;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.CustomerDebtRepository;
import uz.barakat.market.repository.DebtPaymentRepository;
import uz.barakat.market.repository.DebtorRepository;

/** Unit tests for the dual debt flows ("my debts" + "customer debts") and shared history. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DebtServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 27);

    @Mock private DebtorRepository debtors;
    @Mock private CustomerDebtRepository customerDebts;
    @Mock private DebtPaymentRepository payments;
    @InjectMocks private DebtService service;

    private MockedStatic<LocalDate> dateMock;

    @BeforeEach
    void freezeClock() {
        dateMock = mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
        dateMock.when(LocalDate::now).thenReturn(TODAY);
        when(debtors.save(any(Debtor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerDebts.save(any(CustomerDebt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payments.save(any(DebtPayment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void releaseClock() {
        dateMock.close();
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private static Debtor existingDebtor(long id, String name, String original, String paid) {
        Debtor d = new Debtor();
        d.setId(id);
        d.setDate(LocalDate.of(2026, 5, 10));
        d.setName(name);
        d.setProductName("Sut mahsulotlari");
        d.setOriginalAmount(new BigDecimal(original));
        d.setPaidAmount(new BigDecimal(paid));
        d.setPaid(new BigDecimal(paid).compareTo(new BigDecimal(original)) >= 0);
        d.setNote("seed");
        return d;
    }

    private static CustomerDebt existingCustomerDebt(long id, String customerName,
                                                     String original, String paid) {
        CustomerDebt d = new CustomerDebt();
        d.setId(id);
        d.setDate(LocalDate.of(2026, 5, 10));
        d.setCustomerName(customerName);
        d.setProductName("Sut mahsulotlari");
        d.setOriginalAmount(new BigDecimal(original));
        d.setPaidAmount(new BigDecimal(paid));
        d.setPaid(new BigDecimal(paid).compareTo(new BigDecimal(original)) >= 0);
        d.setNote("seed");
        return d;
    }

    private static DebtPayment existingPayment(long id, Long debtorId, Long customerDebtId,
                                               String amount, DebtEntryType type) {
        DebtPayment p = new DebtPayment();
        p.setId(id);
        p.setDebtorId(debtorId);
        p.setCustomerDebtId(customerDebtId);
        p.setPaymentDate(LocalDate.of(2026, 5, 22));
        p.setAmount(new BigDecimal(amount));
        p.setEntryType(type);
        p.setNote("seed");
        return p;
    }

    // ---------- summary / totalMyDebt ----------

    @Test
    void summaryIncludesBothListsAndTotals() {
        Debtor mine1 = existingDebtor(1L, "Acme", "200", "150");   // remaining 50
        Debtor mine2 = existingDebtor(2L, "Beta", "100", "100");   // remaining 0 (paid)
        CustomerDebt their1 = existingCustomerDebt(11L, "Ali", "300", "100");  // remaining 200
        CustomerDebt their2 = existingCustomerDebt(12L, "Vali", "50", "50");   // remaining 0
        when(debtors.findAllByOrderByPaidAscDateDescIdDesc()).thenReturn(List.of(mine1, mine2));
        when(customerDebts.findAllByOrderByPaidAscDateDescIdDesc())
                .thenReturn(List.of(their1, their2));

        DebtSummary result = service.summary();

        assertEquals(2, result.myDebts().size());
        assertEquals(2, result.customerDebts().size());
        assertAmount("50", result.myDebtTotal());
        assertAmount("200", result.customerDebtTotal());
    }

    @Test
    void totalMyDebtSumsRemainingAmountsOverUnpaidOnly() {
        Debtor a = existingDebtor(1L, "Acme", "200", "120");   // remaining 80
        Debtor b = existingDebtor(2L, "Beta", "500", "100");   // remaining 400
        when(debtors.findByPaidFalse()).thenReturn(List.of(a, b));

        BigDecimal total = service.totalMyDebt();

        assertAmount("480", total);
        verify(debtors).findByPaidFalse();
        verify(debtors, never()).findAllByOrderByPaidAscDateDescIdDesc();
    }

    @Test
    void totalMyDebtClampsOverpaidRemainingToZero() {
        Debtor overpaid = existingDebtor(1L, "Acme", "100", "150"); // remaining clamped to 0
        Debtor normal = existingDebtor(2L, "Beta", "100", "20");    // remaining 80
        when(debtors.findByPaidFalse()).thenReturn(List.of(overpaid, normal));

        BigDecimal total = service.totalMyDebt();

        assertAmount("80", total);
    }

    // ---------- my debts: create ----------

    @Test
    void createMyDebtSavesAllFields() {
        DebtorRequest req = new DebtorRequest(
                LocalDate.of(2026, 5, 20), "Acme Supplies", "Cola",
                new BigDecimal("500.00"), new BigDecimal("100"), "first order");

        DebtorResponse response = service.createMyDebt(req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        Debtor saved = captor.getValue();
        assertEquals(LocalDate.of(2026, 5, 20), saved.getDate());
        assertEquals("Acme Supplies", saved.getName());
        assertEquals("Cola", saved.getProductName());
        assertAmount("500.00", saved.getOriginalAmount());
        assertAmount("100", saved.getPaidAmount());
        assertEquals("first order", saved.getNote());
        assertFalse(saved.isPaid());
        assertEquals("Acme Supplies", response.name());
    }

    @Test
    void createMyDebtDefaultsDateToTodayWhenMissing() {
        DebtorRequest req = new DebtorRequest(
                null, "Acme", null, new BigDecimal("100"), null, null);

        service.createMyDebt(req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        assertEquals(TODAY, captor.getValue().getDate());
    }

    @Test
    void createMyDebtDefaultsPaidAmountToZeroWhenMissing() {
        DebtorRequest req = new DebtorRequest(
                TODAY, "Acme", null, new BigDecimal("100"), null, null);

        service.createMyDebt(req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        assertAmount("0", captor.getValue().getPaidAmount());
    }

    @Test
    void createMyDebtStripsWhitespaceFromName() {
        DebtorRequest req = new DebtorRequest(
                TODAY, "  Acme  ", null, new BigDecimal("100"), null, null);

        service.createMyDebt(req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        assertEquals("Acme", captor.getValue().getName());
    }

    @Test
    void createMyDebtMarksPaidWhenPaidEqualsOriginal() {
        DebtorRequest req = new DebtorRequest(
                TODAY, "Acme", null, new BigDecimal("100"), new BigDecimal("100"), null);

        service.createMyDebt(req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        assertTrue(captor.getValue().isPaid());
    }

    // ---------- my debts: update ----------

    @Test
    void updateMyDebtOverwritesFields() {
        Debtor existing = existingDebtor(7L, "Old", "200", "50");
        when(debtors.findById(7L)).thenReturn(Optional.of(existing));
        DebtorRequest req = new DebtorRequest(
                LocalDate.of(2026, 6, 1), "New", "Bread",
                new BigDecimal("400"), new BigDecimal("125"), "updated");

        service.updateMyDebt(7L, req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        Debtor saved = captor.getValue();
        assertEquals(LocalDate.of(2026, 6, 1), saved.getDate());
        assertEquals("New", saved.getName());
        assertEquals("Bread", saved.getProductName());
        assertAmount("400", saved.getOriginalAmount());
        assertAmount("125", saved.getPaidAmount());
        assertEquals("updated", saved.getNote());
    }

    @Test
    void updateMyDebtPreservesExistingDateWhenRequestDateNull() {
        LocalDate keep = LocalDate.of(2026, 4, 15);
        Debtor existing = existingDebtor(7L, "Old", "200", "50");
        existing.setDate(keep);
        when(debtors.findById(7L)).thenReturn(Optional.of(existing));
        DebtorRequest req = new DebtorRequest(
                null, "Renamed", null, new BigDecimal("200"), new BigDecimal("50"), null);

        service.updateMyDebt(7L, req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        assertEquals(keep, captor.getValue().getDate());
    }

    @Test
    void updateMyDebtPreservesPaidAmountWhenRequestPaidAmountNull() {
        Debtor existing = existingDebtor(7L, "Old", "200", "75");
        when(debtors.findById(7L)).thenReturn(Optional.of(existing));
        DebtorRequest req = new DebtorRequest(
                TODAY, "Renamed", null, new BigDecimal("200"), null, null);

        service.updateMyDebt(7L, req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        assertAmount("75", captor.getValue().getPaidAmount());
    }

    @Test
    void updateMyDebtThrowsNotFoundWhenMissing() {
        when(debtors.findById(99L)).thenReturn(Optional.empty());
        DebtorRequest req = new DebtorRequest(
                TODAY, "Acme", null, new BigDecimal("100"), null, null);

        assertThrows(NotFoundException.class, () -> service.updateMyDebt(99L, req));
        verify(debtors, never()).save(any(Debtor.class));
    }

    // ---------- my debts: delete ----------

    @Test
    void deleteMyDebtRemovesExisting() {
        Debtor existing = existingDebtor(7L, "Acme", "100", "0");
        when(debtors.findById(7L)).thenReturn(Optional.of(existing));

        service.deleteMyDebt(7L);

        verify(debtors).delete(existing);
    }

    @Test
    void deleteMyDebtThrowsNotFoundWhenMissing() {
        when(debtors.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.deleteMyDebt(99L));
        verify(debtors, never()).delete(any(Debtor.class));
    }

    // ---------- my debts: pay ----------

    @Test
    void payMyDebtAddsAmountAndFlipsPaidFlagToTrueAtThreshold() {
        Debtor existing = existingDebtor(7L, "Acme", "200", "150");
        when(debtors.findById(7L)).thenReturn(Optional.of(existing));
        DebtPaymentRequest req = new DebtPaymentRequest(new BigDecimal("50"), TODAY, "final");

        DebtorResponse response = service.payMyDebt(7L, req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        Debtor saved = captor.getValue();
        assertAmount("200", saved.getPaidAmount());
        assertTrue(saved.isPaid());
        assertTrue(response.paid());
        assertAmount("0", response.remainingAmount());
    }

    @Test
    void payMyDebtRecordsHistoryWithDebtorIdAndPaymentType() {
        Debtor existing = existingDebtor(7L, "Acme", "200", "0");
        when(debtors.findById(7L)).thenReturn(Optional.of(existing));
        DebtPaymentRequest req = new DebtPaymentRequest(
                new BigDecimal("50"), LocalDate.of(2026, 5, 22), "partial");

        service.payMyDebt(7L, req);

        ArgumentCaptor<DebtPayment> captor = ArgumentCaptor.forClass(DebtPayment.class);
        verify(payments).save(captor.capture());
        DebtPayment recorded = captor.getValue();
        assertEquals(7L, recorded.getDebtorId());
        assertNull(recorded.getCustomerDebtId());
        assertEquals(DebtEntryType.PAYMENT, recorded.getEntryType());
        assertAmount("50", recorded.getAmount());
        assertEquals(LocalDate.of(2026, 5, 22), recorded.getPaymentDate());
        assertEquals("partial", recorded.getNote());
    }

    @Test
    void payMyDebtDefaultsHistoryDateToTodayWhenMissing() {
        Debtor existing = existingDebtor(7L, "Acme", "200", "0");
        when(debtors.findById(7L)).thenReturn(Optional.of(existing));
        DebtPaymentRequest req = new DebtPaymentRequest(new BigDecimal("50"), null, null);

        service.payMyDebt(7L, req);

        ArgumentCaptor<DebtPayment> captor = ArgumentCaptor.forClass(DebtPayment.class);
        verify(payments).save(captor.capture());
        assertEquals(TODAY, captor.getValue().getPaymentDate());
    }

    @Test
    void payMyDebtThrowsNotFoundWhenMissing() {
        when(debtors.findById(99L)).thenReturn(Optional.empty());
        DebtPaymentRequest req = new DebtPaymentRequest(new BigDecimal("50"), TODAY, null);

        assertThrows(NotFoundException.class, () -> service.payMyDebt(99L, req));
        verify(debtors, never()).save(any(Debtor.class));
        verifyNoInteractions(payments);
    }

    // ---------- my debts: add / increase ----------

    @Test
    void addToMyDebtIncreasesOriginalAndFlipsPaidFlagBackToFalse() {
        Debtor fullyPaid = existingDebtor(7L, "Acme", "100", "100");
        when(debtors.findById(7L)).thenReturn(Optional.of(fullyPaid));
        DebtPaymentRequest req = new DebtPaymentRequest(new BigDecimal("50"), TODAY, "extra goods");

        DebtorResponse response = service.addToMyDebt(7L, req);

        ArgumentCaptor<Debtor> captor = ArgumentCaptor.forClass(Debtor.class);
        verify(debtors).save(captor.capture());
        Debtor saved = captor.getValue();
        assertAmount("150", saved.getOriginalAmount());
        assertAmount("100", saved.getPaidAmount());
        assertFalse(saved.isPaid());
        assertFalse(response.paid());
        assertAmount("50", response.remainingAmount());
    }

    @Test
    void addToMyDebtRecordsHistoryWithIncreaseEntryType() {
        Debtor existing = existingDebtor(7L, "Acme", "100", "0");
        when(debtors.findById(7L)).thenReturn(Optional.of(existing));
        DebtPaymentRequest req = new DebtPaymentRequest(new BigDecimal("75"), TODAY, "more");

        service.addToMyDebt(7L, req);

        ArgumentCaptor<DebtPayment> captor = ArgumentCaptor.forClass(DebtPayment.class);
        verify(payments).save(captor.capture());
        DebtPayment recorded = captor.getValue();
        assertEquals(7L, recorded.getDebtorId());
        assertNull(recorded.getCustomerDebtId());
        assertEquals(DebtEntryType.INCREASE, recorded.getEntryType());
        assertAmount("75", recorded.getAmount());
    }

    @Test
    void addToMyDebtThrowsNotFoundWhenMissing() {
        when(debtors.findById(99L)).thenReturn(Optional.empty());
        DebtPaymentRequest req = new DebtPaymentRequest(new BigDecimal("50"), TODAY, null);

        assertThrows(NotFoundException.class, () -> service.addToMyDebt(99L, req));
        verify(debtors, never()).save(any(Debtor.class));
        verifyNoInteractions(payments);
    }

    // ---------- my debts: history ----------

    @Test
    void myDebtHistoryReturnsMappedPayments() {
        Debtor existing = existingDebtor(7L, "Acme", "200", "100");
        when(debtors.findById(7L)).thenReturn(Optional.of(existing));
        DebtPayment p1 = existingPayment(101L, 7L, null, "50", DebtEntryType.PAYMENT);
        DebtPayment p2 = existingPayment(102L, 7L, null, "30", DebtEntryType.INCREASE);
        when(payments.findByDebtorIdOrderByPaymentDateDescIdDesc(7L)).thenReturn(List.of(p1, p2));

        List<DebtPaymentResponse> result = service.myDebtHistory(7L);

        assertEquals(2, result.size());
        assertEquals(101L, result.get(0).id());
        assertEquals(DebtEntryType.PAYMENT, result.get(0).entryType());
        assertEquals(DebtEntryType.INCREASE, result.get(1).entryType());
        verify(payments).findByDebtorIdOrderByPaymentDateDescIdDesc(7L);
    }

    @Test
    void myDebtHistoryThrowsNotFoundBeforeQueryingRepository() {
        when(debtors.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.myDebtHistory(99L));
        verifyNoInteractions(payments);
    }

    // ---------- customer debts: mirror coverage ----------

    @Test
    void createCustomerDebtSavesAllFieldsWithCustomerName() {
        CustomerDebtRequest req = new CustomerDebtRequest(
                LocalDate.of(2026, 5, 20), "  Ali Valiyev  ", "Sut",
                new BigDecimal("250"), new BigDecimal("100"), "weekly order");

        service.createCustomerDebt(req);

        ArgumentCaptor<CustomerDebt> captor = ArgumentCaptor.forClass(CustomerDebt.class);
        verify(customerDebts).save(captor.capture());
        CustomerDebt saved = captor.getValue();
        assertEquals("Ali Valiyev", saved.getCustomerName());
        assertEquals("Sut", saved.getProductName());
        assertAmount("250", saved.getOriginalAmount());
        assertAmount("100", saved.getPaidAmount());
        assertFalse(saved.isPaid());
    }

    @Test
    void updateCustomerDebtPreservesExistingDateWhenRequestDateNull() {
        LocalDate keep = LocalDate.of(2026, 4, 15);
        CustomerDebt existing = existingCustomerDebt(7L, "Ali", "200", "50");
        existing.setDate(keep);
        when(customerDebts.findById(7L)).thenReturn(Optional.of(existing));
        CustomerDebtRequest req = new CustomerDebtRequest(
                null, "Ali", null, new BigDecimal("200"), new BigDecimal("50"), null);

        service.updateCustomerDebt(7L, req);

        ArgumentCaptor<CustomerDebt> captor = ArgumentCaptor.forClass(CustomerDebt.class);
        verify(customerDebts).save(captor.capture());
        assertEquals(keep, captor.getValue().getDate());
    }

    @Test
    void deleteCustomerDebtRemovesExisting() {
        CustomerDebt existing = existingCustomerDebt(7L, "Ali", "100", "0");
        when(customerDebts.findById(7L)).thenReturn(Optional.of(existing));

        service.deleteCustomerDebt(7L);

        verify(customerDebts).delete(existing);
    }

    @Test
    void deleteCustomerDebtThrowsNotFoundWhenMissing() {
        when(customerDebts.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.deleteCustomerDebt(99L));
        verify(customerDebts, never()).delete(any(CustomerDebt.class));
    }

    @Test
    void payCustomerDebtRecordsHistoryWithCustomerDebtIdNotDebtorId() {
        CustomerDebt existing = existingCustomerDebt(7L, "Ali", "200", "0");
        when(customerDebts.findById(7L)).thenReturn(Optional.of(existing));
        DebtPaymentRequest req = new DebtPaymentRequest(
                new BigDecimal("60"), LocalDate.of(2026, 5, 22), "cash");

        service.payCustomerDebt(7L, req);

        ArgumentCaptor<DebtPayment> captor = ArgumentCaptor.forClass(DebtPayment.class);
        verify(payments).save(captor.capture());
        DebtPayment recorded = captor.getValue();
        assertNull(recorded.getDebtorId());
        assertEquals(7L, recorded.getCustomerDebtId());
        assertEquals(DebtEntryType.PAYMENT, recorded.getEntryType());
        assertAmount("60", recorded.getAmount());
    }

    @Test
    void addToCustomerDebtRecordsHistoryWithIncreaseEntryType() {
        CustomerDebt existing = existingCustomerDebt(7L, "Ali", "100", "0");
        when(customerDebts.findById(7L)).thenReturn(Optional.of(existing));
        DebtPaymentRequest req = new DebtPaymentRequest(new BigDecimal("40"), TODAY, "extra");

        service.addToCustomerDebt(7L, req);

        ArgumentCaptor<DebtPayment> captor = ArgumentCaptor.forClass(DebtPayment.class);
        verify(payments).save(captor.capture());
        DebtPayment recorded = captor.getValue();
        assertNull(recorded.getDebtorId());
        assertEquals(7L, recorded.getCustomerDebtId());
        assertEquals(DebtEntryType.INCREASE, recorded.getEntryType());
        assertAmount("40", recorded.getAmount());
    }

    @Test
    void customerDebtHistoryUsesCustomerDebtIdQueryMethod() {
        CustomerDebt existing = existingCustomerDebt(7L, "Ali", "200", "0");
        when(customerDebts.findById(7L)).thenReturn(Optional.of(existing));
        DebtPayment p = existingPayment(101L, null, 7L, "50", DebtEntryType.PAYMENT);
        when(payments.findByCustomerDebtIdOrderByPaymentDateDescIdDesc(7L)).thenReturn(List.of(p));

        List<DebtPaymentResponse> result = service.customerDebtHistory(7L);

        assertEquals(1, result.size());
        verify(payments).findByCustomerDebtIdOrderByPaymentDateDescIdDesc(7L);
        verify(payments, never()).findByDebtorIdOrderByPaymentDateDescIdDesc(any());
    }

    @Test
    void customerDebtHistoryThrowsNotFoundBeforeQueryingRepository() {
        when(customerDebts.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.customerDebtHistory(99L));
        verifyNoInteractions(payments);
    }
}
