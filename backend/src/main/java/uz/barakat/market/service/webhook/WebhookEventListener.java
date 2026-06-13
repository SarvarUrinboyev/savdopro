package uz.barakat.market.service.webhook;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import uz.barakat.market.domain.Product;
import uz.barakat.market.dto.pub.PublicDtos.WebhookEnvelope;
import uz.barakat.market.repository.OrderRepository;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.StockMovementRepository;
import uz.barakat.market.service.LedgerEvents.PaymentRecorded;
import uz.barakat.market.service.LedgerEvents.SalePosted;
import uz.barakat.market.service.LedgerEvents.SaleRefunded;
import uz.barakat.market.service.LedgerEvents.StockMovementRecorded;
import uz.barakat.market.service.PublicMapper;
import uz.barakat.market.service.webhook.WebhookEvents.OrderChanged;
import uz.barakat.market.service.webhook.WebhookEvents.ProductChanged;

/**
 * Turns committed domain events into outbound webhook deliveries. Mirrors
 * {@code LedgerPostingListener}: AFTER_COMMIT, on the request thread (TenantContext
 * still set), best-effort. Each handler loads the entity, maps it to the external
 * {@code dto.pub} shape, and enqueues one delivery per matching subscription.
 * Reuses {@code LedgerEvents} for sale/refund/payment/stock; product and order
 * changes come from {@link WebhookEvents}.
 */
@Component
public class WebhookEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventListener.class);

    private final WebhookSubscriptionService webhooks;
    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final OrderRepository orders;

    public WebhookEventListener(WebhookSubscriptionService webhooks, SaleRepository sales,
                                PaymentRepository payments, ProductRepository products,
                                StockMovementRepository movements, OrderRepository orders) {
        this.webhooks = webhooks;
        this.sales = sales;
        this.payments = payments;
        this.products = products;
        this.movements = movements;
        this.orders = orders;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSale(SalePosted ev) {
        safely("sale.created " + ev.saleId(), () ->
                sales.findById(ev.saleId()).ifPresent(s ->
                        emit("sale.created", s.getShopId(), PublicMapper.sale(s))));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSaleRefund(SaleRefunded ev) {
        safely("sale.refunded " + ev.saleId(), () ->
                sales.findById(ev.saleId()).ifPresent(s ->
                        emit("sale.refunded", s.getShopId(), PublicMapper.sale(s))));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPayment(PaymentRecorded ev) {
        safely("payment.recorded " + ev.paymentId(), () ->
                payments.findById(ev.paymentId()).ifPresent(p ->
                        emit("payment.recorded", p.getShopId(), PublicMapper.payment(p))));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStockMovement(StockMovementRecorded ev) {
        safely("stock.updated " + ev.movementId(), () ->
                movements.findById(ev.movementId()).ifPresent(m ->
                        products.findById(m.getProductId()).ifPresent(p -> {
                            emit("stock.updated", p.getShopId(), PublicMapper.product(p));
                            if (isLowStock(p)) {
                                emit("product.low_stock", p.getShopId(), PublicMapper.product(p));
                            }
                        })));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductChanged(ProductChanged ev) {
        String type = "created".equalsIgnoreCase(ev.type()) ? "product.created" : "product.updated";
        safely(type + " " + ev.productId(), () ->
                products.findById(ev.productId()).ifPresent(p ->
                        emit(type, p.getShopId(), PublicMapper.product(p))));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderChanged(OrderChanged ev) {
        String type = "created".equalsIgnoreCase(ev.status()) ? "order.created" : "order.status_changed";
        safely(type + " " + ev.orderId(), () ->
                orders.findById(ev.orderId()).ifPresent(o ->
                        emit(type, o.getShopId(), PublicMapper.order(o))));
    }

    private void emit(String eventType, Long shopId, Object data) {
        webhooks.enqueue(eventType, new WebhookEnvelope(eventType, LocalDateTime.now(), shopId, data));
    }

    private static boolean isLowStock(Product p) {
        return p.getLowStockThreshold() > 0 && p.getQuantity() <= p.getLowStockThreshold();
    }

    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            // Webhook fan-out is best-effort; never affect the committed operation.
            log.warn("Webhook enqueue failed for {} — skipped: {}", what, ex.toString());
        }
    }
}
