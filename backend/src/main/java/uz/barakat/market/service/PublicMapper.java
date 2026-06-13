package uz.barakat.market.service;

import java.util.List;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.Order;
import uz.barakat.market.domain.Payment;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.dto.pub.PublicDtos.CustomerResource;
import uz.barakat.market.dto.pub.PublicDtos.OrderResource;
import uz.barakat.market.dto.pub.PublicDtos.PaymentResource;
import uz.barakat.market.dto.pub.PublicDtos.ProductResource;
import uz.barakat.market.dto.pub.PublicDtos.SaleLineResource;
import uz.barakat.market.dto.pub.PublicDtos.SaleResource;

/**
 * Maps internal entities to the external-stable {@code dto.pub} shapes used by
 * the Open API and webhook payloads. Static + dependency-free; the single place
 * that decides what leaves the building (no cost/margin, minimal PII).
 */
public final class PublicMapper {

    private PublicMapper() {
    }

    public static ProductResource product(Product p) {
        return new ProductResource(
                p.getId(), p.getName(), p.getBarcode(), null,
                p.getSalePrice(), p.getQuantity(), p.getQuantity() > 0,
                p.getCategoryId(), p.getUnit(), p.getVatRate(),
                p.getLowStockThreshold(), p.getExpiryDate(), p.getCreatedAt());
    }

    public static SaleResource sale(Sale s) {
        List<SaleLineResource> lines = s.getItems().stream().map(PublicMapper::saleLine).toList();
        return new SaleResource(
                s.getId(), s.getCreatedAt(), s.getSubtotalUzs(), s.getDiscountAmount(),
                s.getTotalUzs(), s.getPaymentMethod(), s.getRefundedTotalUzs(),
                s.isFullyRefunded(), s.getRefundedAt(), s.getCashier(), lines);
    }

    private static SaleLineResource saleLine(SaleItem it) {
        return new SaleLineResource(
                it.getProductId(), it.getProductName(), it.getProductSku(), it.getQuantity(),
                it.getUnitPriceUzs(), it.getLineDiscountUzs(), it.getLineTotalUzs(), it.getRefundedQty());
    }

    public static CustomerResource customer(Customer c) {
        return new CustomerResource(
                c.getId(), c.getName(), c.getPhone(), c.getPointsBalance(), c.getCreatedAt());
    }

    public static OrderResource order(Order o) {
        return new OrderResource(
                o.getId(), o.getName(), o.getSupplier(), o.getOrderDate(), o.getDeliveryDate(),
                o.getAmount(), o.isCompleted(), o.getCompletedAt(), o.getNote());
    }

    public static PaymentResource payment(Payment p) {
        return new PaymentResource(
                p.getId(), p.getDate(),
                p.getDirection() == null ? null : p.getDirection().name(),
                p.getCategory() == null ? null : p.getCategory().name(),
                p.getParty(), p.getAmount(),
                p.getMethod() == null ? null : p.getMethod().name(),
                p.getCurrency() == null ? null : p.getCurrency().name(),
                p.getNote(), p.getCustomerId(), p.getCreatedAt());
    }
}
