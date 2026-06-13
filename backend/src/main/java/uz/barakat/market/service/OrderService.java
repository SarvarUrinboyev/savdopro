package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.service.webhook.WebhookEvents;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.Order;
import uz.barakat.market.dto.ExpenseRequest;
import uz.barakat.market.dto.OrderCompleteRequest;
import uz.barakat.market.dto.OrderRequest;
import uz.barakat.market.dto.OrderResponse;
import uz.barakat.market.dto.OrdersByStatus;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.OrderRepository;

/** Expected goods orders, including the "Keldi" auto-expense flow. */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orders;
    private final ExpenseService expenseService;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orders, ExpenseService expenseService,
                        ApplicationEventPublisher events) {
        this.orders = orders;
        this.expenseService = expenseService;
        this.events = events;
    }

    /** Open orders grouped into today / overdue / upcoming. */
    @Transactional(readOnly = true)
    public OrdersByStatus grouped() {
        LocalDate today = LocalDate.now();
        return new OrdersByStatus(
                map(orders.findByCompletedFalseAndDeliveryDateOrderByIdDesc(today), today),
                map(orders.findByCompletedFalseAndDeliveryDateLessThanOrderByDeliveryDateAsc(today), today),
                map(orders.findByCompletedFalseAndDeliveryDateGreaterThanOrderByDeliveryDateAsc(today), today));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listAll() {
        return map(orders.findAllByOrderByCompletedAscDeliveryDateAscIdDesc(), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listToday() {
        LocalDate today = LocalDate.now();
        return map(orders.findByCompletedFalseAndDeliveryDateOrderByIdDesc(today), today);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return Mappers.order(find(id), LocalDate.now());
    }

    public OrderResponse create(OrderRequest request) {
        Order order = new Order();
        apply(order, request);
        Order saved = orders.save(order);
        events.publishEvent(new WebhookEvents.OrderChanged(saved.getId(), "created"));
        return Mappers.order(saved, LocalDate.now());
    }

    public OrderResponse update(Long id, OrderRequest request) {
        Order order = find(id);
        apply(order, request);
        return Mappers.order(orders.save(order), LocalDate.now());
    }

    public void delete(Long id) {
        orders.delete(find(id));
    }

    /**
     * Goods arrived ("Keldi"): the order is marked completed and a
     * matching supermarket expense is recorded from the payment details.
     */
    public OrderResponse complete(Long id, OrderCompleteRequest request) {
        Order order = find(id);
        if (order.isCompleted()) {
            throw new BadRequestException("Buyurtma allaqachon qabul qilingan");
        }
        LocalDate date = request.date() != null ? request.date() : LocalDate.now();
        expenseService.create(new ExpenseRequest(
                date, order.getName(), request.amount(), request.paymentType(),
                request.cashAmount(), request.naqdAmount(), request.cardAmount(),
                Currency.USD, "Buyurtmadan: " + order.getName()));
        order.setCompleted(true);
        order.setCompletedAt(LocalDateTime.now());
        Order saved = orders.save(order);
        events.publishEvent(new WebhookEvents.OrderChanged(saved.getId(), "status_changed"));
        return Mappers.order(saved, LocalDate.now());
    }

    private void apply(Order order, OrderRequest request) {
        order.setOrderDate(request.orderDate() != null ? request.orderDate() : LocalDate.now());
        order.setDeliveryDate(request.deliveryDate());
        order.setName(request.name().strip());
        order.setSupplier(request.supplier());
        order.setAmount(request.amount() != null ? request.amount() : BigDecimal.ZERO);
        order.setNote(request.note());
    }

    private List<OrderResponse> map(List<Order> list, LocalDate today) {
        return list.stream().map(o -> Mappers.order(o, today)).toList();
    }

    private Order find(Long id) {
        return orders.findById(id).orElseThrow(() -> NotFoundException.of("Buyurtma", id));
    }
}
