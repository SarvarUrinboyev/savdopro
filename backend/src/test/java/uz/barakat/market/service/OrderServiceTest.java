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
import java.time.LocalDateTime;
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
import org.springframework.context.ApplicationEventPublisher;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.Order;
import uz.barakat.market.domain.PaymentType;
import uz.barakat.market.dto.ExpenseRequest;
import uz.barakat.market.dto.OrderCompleteRequest;
import uz.barakat.market.dto.OrderRequest;
import uz.barakat.market.dto.OrderResponse;
import uz.barakat.market.dto.OrdersByStatus;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.OrderRepository;

/** Unit tests for the expected-goods order CRUD and the "Keldi" completion flow. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 27);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 27, 14, 30);

    @Mock private OrderRepository orders;
    @Mock private ExpenseService expenseService;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private OrderService service;

    private MockedStatic<LocalDate> dateMock;
    private MockedStatic<LocalDateTime> dateTimeMock;

    @BeforeEach
    void freezeClock() {
        dateMock = mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
        dateMock.when(LocalDate::now).thenReturn(TODAY);
        dateTimeMock = mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS);
        dateTimeMock.when(LocalDateTime::now).thenReturn(NOW);
        when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void releaseClock() {
        dateMock.close();
        dateTimeMock.close();
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private static Order existingOrder(long id, String name, LocalDate deliveryDate,
                                       String amount, boolean completed) {
        Order o = new Order();
        o.setId(id);
        o.setOrderDate(deliveryDate.minusDays(2));
        o.setDeliveryDate(deliveryDate);
        o.setName(name);
        o.setSupplier("ACME");
        o.setAmount(new BigDecimal(amount));
        o.setCompleted(completed);
        o.setNote("seed");
        return o;
    }

    // ---------- create ----------

    @Test
    void createSavesOrderWithAllFieldsFromRequest() {
        OrderRequest req = new OrderRequest(
                LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 28),
                "Hydrolife", "ZelTrade", new BigDecimal("1500.00"), "first batch");

        OrderResponse response = service.create(req);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        Order saved = captor.getValue();
        assertEquals(LocalDate.of(2026, 5, 20), saved.getOrderDate());
        assertEquals(LocalDate.of(2026, 5, 28), saved.getDeliveryDate());
        assertEquals("Hydrolife", saved.getName());
        assertEquals("ZelTrade", saved.getSupplier());
        assertAmount("1500.00", saved.getAmount());
        assertEquals("first batch", saved.getNote());
        assertFalse(saved.isCompleted());
        assertEquals("Hydrolife", response.name());
    }

    @Test
    void createDefaultsOrderDateToTodayWhenMissing() {
        OrderRequest req = new OrderRequest(
                null, LocalDate.of(2026, 6, 1),
                "Non", null, new BigDecimal("100"), null);

        service.create(req);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        assertEquals(TODAY, captor.getValue().getOrderDate());
    }

    @Test
    void createDefaultsAmountToZeroWhenMissing() {
        OrderRequest req = new OrderRequest(
                TODAY, TODAY.plusDays(3), "Sut", "ACME", null, null);

        service.create(req);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        assertAmount("0", captor.getValue().getAmount());
    }

    @Test
    void createStripsWhitespaceFromName() {
        OrderRequest req = new OrderRequest(
                TODAY, TODAY.plusDays(3), "  Sut  ", null, BigDecimal.ONE, null);

        service.create(req);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        assertEquals("Sut", captor.getValue().getName());
    }

    @Test
    void createReturnsResponseWithDerivedStatus() {
        OrderRequest req = new OrderRequest(
                TODAY, TODAY.plusDays(5), "Cola", "ACME",
                new BigDecimal("250"), null);

        OrderResponse response = service.create(req);

        assertEquals("UPCOMING", response.status());
    }

    // ---------- update ----------

    @Test
    void updateOverwritesExistingOrderFields() {
        Order existing = existingOrder(7L, "Old Name", TODAY.plusDays(10), "100", false);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));
        OrderRequest req = new OrderRequest(
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 5),
                "New Name", "NewSupplier", new BigDecimal("999.99"), "updated");

        service.update(7L, req);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        Order saved = captor.getValue();
        assertEquals("New Name", saved.getName());
        assertEquals("NewSupplier", saved.getSupplier());
        assertEquals(LocalDate.of(2026, 6, 5), saved.getDeliveryDate());
        assertAmount("999.99", saved.getAmount());
        assertEquals("updated", saved.getNote());
    }

    @Test
    void updateThrowsNotFoundWhenOrderMissing() {
        when(orders.findById(99L)).thenReturn(Optional.empty());
        OrderRequest req = new OrderRequest(
                TODAY, TODAY.plusDays(1), "Sut", null, BigDecimal.ONE, null);

        assertThrows(NotFoundException.class, () -> service.update(99L, req));
        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void updateDoesNotResetCompletedFlag() {
        Order existing = existingOrder(7L, "Old", TODAY.minusDays(2), "100", true);
        LocalDateTime earlierCompletion = LocalDateTime.of(2026, 5, 25, 9, 0);
        existing.setCompletedAt(earlierCompletion);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));
        OrderRequest req = new OrderRequest(
                TODAY, TODAY.plusDays(1), "Renamed", null, BigDecimal.TEN, "note");

        service.update(7L, req);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        assertTrue(captor.getValue().isCompleted());
        assertEquals(earlierCompletion, captor.getValue().getCompletedAt());
    }

    // ---------- delete ----------

    @Test
    void deleteRemovesExistingOrder() {
        Order existing = existingOrder(7L, "Sut", TODAY.plusDays(1), "100", false);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));

        service.delete(7L);

        verify(orders).delete(existing);
    }

    @Test
    void deleteThrowsNotFoundWhenOrderMissing() {
        when(orders.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.delete(99L));
        verify(orders, never()).delete(any(Order.class));
    }

    // ---------- get ----------

    @Test
    void getReturnsMappedResponse() {
        Order existing = existingOrder(7L, "Hydrolife", TODAY.plusDays(2), "500", false);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));

        OrderResponse response = service.get(7L);

        assertEquals(7L, response.id());
        assertEquals("Hydrolife", response.name());
        assertAmount("500", response.amount());
        assertEquals("UPCOMING", response.status());
    }

    @Test
    void getThrowsNotFoundWhenOrderMissing() {
        when(orders.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(99L));
    }

    // ---------- grouped / listAll / listToday ----------

    @Test
    void groupedBucketsOrdersByDeliveryRelativeToToday() {
        Order todayOrder = existingOrder(1L, "Today", TODAY, "10", false);
        Order overdueOrder = existingOrder(2L, "Overdue", TODAY.minusDays(3), "20", false);
        Order upcomingOrder = existingOrder(3L, "Upcoming", TODAY.plusDays(7), "30", false);
        when(orders.findByCompletedFalseAndDeliveryDateOrderByIdDesc(TODAY))
                .thenReturn(List.of(todayOrder));
        when(orders.findByCompletedFalseAndDeliveryDateLessThanOrderByDeliveryDateAsc(TODAY))
                .thenReturn(List.of(overdueOrder));
        when(orders.findByCompletedFalseAndDeliveryDateGreaterThanOrderByDeliveryDateAsc(TODAY))
                .thenReturn(List.of(upcomingOrder));

        OrdersByStatus result = service.grouped();

        assertEquals(1, result.today().size());
        assertEquals("Today", result.today().get(0).name());
        assertEquals("TODAY", result.today().get(0).status());
        assertEquals(1, result.overdue().size());
        assertEquals("OVERDUE", result.overdue().get(0).status());
        assertEquals(1, result.upcoming().size());
        assertEquals("UPCOMING", result.upcoming().get(0).status());
    }

    @Test
    void listAllUsesRepositorySortOrder() {
        Order one = existingOrder(1L, "A", TODAY, "1", false);
        Order two = existingOrder(2L, "B", TODAY.plusDays(1), "2", true);
        when(orders.findAllByOrderByCompletedAscDeliveryDateAscIdDesc())
                .thenReturn(List.of(one, two));

        List<OrderResponse> result = service.listAll();

        assertEquals(2, result.size());
        assertEquals("A", result.get(0).name());
        assertEquals("B", result.get(1).name());
        verify(orders).findAllByOrderByCompletedAscDeliveryDateAscIdDesc();
    }

    @Test
    void listTodayQueriesByTodaysDate() {
        Order due = existingOrder(1L, "Sut", TODAY, "100", false);
        when(orders.findByCompletedFalseAndDeliveryDateOrderByIdDesc(TODAY))
                .thenReturn(List.of(due));

        List<OrderResponse> result = service.listToday();

        assertEquals(1, result.size());
        assertEquals("TODAY", result.get(0).status());
        verify(orders).findByCompletedFalseAndDeliveryDateOrderByIdDesc(TODAY);
    }

    // ---------- complete ----------

    @Test
    void completeMarksOrderCompletedAndPersistsTimestamp() {
        Order existing = existingOrder(7L, "Sut", TODAY, "100", false);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));
        OrderCompleteRequest req = new OrderCompleteRequest(
                new BigDecimal("100"), PaymentType.NAQD, null,
                new BigDecimal("100"), null, TODAY);

        service.complete(7L, req);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(captor.capture());
        Order saved = captor.getValue();
        assertTrue(saved.isCompleted());
        assertEquals(NOW, saved.getCompletedAt());
    }

    @Test
    void completeCreatesExpenseFromRequestPayment() {
        Order existing = existingOrder(7L, "Sut", TODAY, "100", false);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));
        OrderCompleteRequest req = new OrderCompleteRequest(
                new BigDecimal("864"), PaymentType.ARALASH,
                new BigDecimal("614"), null, new BigDecimal("250"), TODAY);

        service.complete(7L, req);

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).create(captor.capture());
        ExpenseRequest expense = captor.getValue();
        assertAmount("864", expense.amount());
        assertEquals(PaymentType.ARALASH, expense.paymentType());
        assertAmount("614", expense.cashAmount());
        assertNull(expense.naqdAmount());
        assertAmount("250", expense.cardAmount());
    }

    @Test
    void completeForcesExpenseCurrencyToUsd() {
        Order existing = existingOrder(7L, "Sut", TODAY, "100", false);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));
        OrderCompleteRequest req = new OrderCompleteRequest(
                new BigDecimal("100"), PaymentType.NAQD, null,
                new BigDecimal("100"), null, TODAY);

        service.complete(7L, req);

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).create(captor.capture());
        assertEquals(Currency.USD, captor.getValue().currency());
    }

    @Test
    void completeUsesOrderNameForExpenseName() {
        Order existing = existingOrder(7L, "Hydrolife yetkazib berish", TODAY, "100", false);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));
        OrderCompleteRequest req = new OrderCompleteRequest(
                new BigDecimal("100"), PaymentType.NAQD, null,
                new BigDecimal("100"), null, TODAY);

        service.complete(7L, req);

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).create(captor.capture());
        assertEquals("Hydrolife yetkazib berish", captor.getValue().name());
    }

    @Test
    void completeFormatsExpenseNoteAsBuyurtmadan() {
        Order existing = existingOrder(7L, "Sut", TODAY, "100", false);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));
        OrderCompleteRequest req = new OrderCompleteRequest(
                new BigDecimal("100"), PaymentType.NAQD, null,
                new BigDecimal("100"), null, TODAY);

        service.complete(7L, req);

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).create(captor.capture());
        assertEquals("Buyurtmadan: Sut", captor.getValue().note());
    }

    @Test
    void completeDefaultsExpenseDateToTodayWhenMissing() {
        Order existing = existingOrder(7L, "Sut", TODAY, "100", false);
        when(orders.findById(7L)).thenReturn(Optional.of(existing));
        OrderCompleteRequest req = new OrderCompleteRequest(
                new BigDecimal("100"), PaymentType.NAQD, null,
                new BigDecimal("100"), null, null);

        service.complete(7L, req);

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).create(captor.capture());
        assertEquals(TODAY, captor.getValue().date());
    }

    @Test
    void completeRejectsAlreadyCompletedOrder() {
        Order alreadyDone = existingOrder(7L, "Sut", TODAY, "100", true);
        when(orders.findById(7L)).thenReturn(Optional.of(alreadyDone));
        OrderCompleteRequest req = new OrderCompleteRequest(
                new BigDecimal("100"), PaymentType.NAQD, null,
                new BigDecimal("100"), null, TODAY);

        assertThrows(BadRequestException.class, () -> service.complete(7L, req));
        verifyNoInteractions(expenseService);
        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void completeThrowsNotFoundWhenOrderMissing() {
        when(orders.findById(99L)).thenReturn(Optional.empty());
        OrderCompleteRequest req = new OrderCompleteRequest(
                new BigDecimal("100"), PaymentType.NAQD, null,
                new BigDecimal("100"), null, TODAY);

        assertThrows(NotFoundException.class, () -> service.complete(99L, req));
        verifyNoInteractions(expenseService);
        verify(orders, never()).save(any(Order.class));
    }

    // ---------- status derivation ----------

    @Test
    void statusIsCompletedWhenOrderCompletedRegardlessOfDeliveryDate() {
        Order done = existingOrder(7L, "Sut", TODAY.minusDays(10), "100", true);
        done.setCompletedAt(LocalDateTime.of(2026, 5, 18, 12, 0));
        when(orders.findById(7L)).thenReturn(Optional.of(done));

        OrderResponse response = service.get(7L);

        assertEquals("COMPLETED", response.status());
    }
}
