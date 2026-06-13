package uz.barakat.market.service;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.dto.pub.PublicDtos.CustomerResource;
import uz.barakat.market.dto.pub.PublicDtos.OrderResource;
import uz.barakat.market.dto.pub.PublicDtos.PaymentResource;
import uz.barakat.market.dto.pub.PublicDtos.ProductResource;
import uz.barakat.market.dto.pub.PublicDtos.SaleResource;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.OrderRepository;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;

/**
 * Read-only queries backing the Open API ({@code /api/v1/**}). Lives in the
 * {@code service} package on purpose: {@code TenantFilterAspect} only enables
 * the Hibernate tenant filter around service-layer calls, so all external reads
 * MUST go through here (a controller hitting a repository directly would bypass
 * the filter). Mapping happens inside the transaction via {@link PublicMapper}.
 */
@Service
@Transactional(readOnly = true)
public class PublicApiQueryService {

    private static final int MAX_SALE_ROWS = 1000;

    private final ProductRepository products;
    private final SaleRepository sales;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final PaymentRepository payments;

    public PublicApiQueryService(ProductRepository products, SaleRepository sales,
                                 CustomerRepository customers, OrderRepository orders,
                                 PaymentRepository payments) {
        this.products = products;
        this.sales = sales;
        this.customers = customers;
        this.orders = orders;
        this.payments = payments;
    }

    public List<ProductResource> products(int page, int size) {
        return products.findAll(PageRequest.of(Math.max(page, 0), clamp(size), Sort.by("id")))
                .map(PublicMapper::product).getContent();
    }

    public ProductResource product(Long id) {
        return products.findById(id).map(PublicMapper::product)
                .orElseThrow(() -> new NotFoundException("Mahsulot topilmadi"));
    }

    public List<SaleResource> sales(LocalDate from, LocalDate to) {
        return sales.findByCreatedAtBetweenOrderByCreatedAtDesc(
                        from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream().limit(MAX_SALE_ROWS).map(PublicMapper::sale).toList();
    }

    public SaleResource sale(Long id) {
        return sales.findById(id).map(PublicMapper::sale)
                .orElseThrow(() -> new NotFoundException("Sotuv topilmadi"));
    }

    public List<CustomerResource> customers(int page, int size) {
        return customers.findAll(PageRequest.of(Math.max(page, 0), clamp(size), Sort.by("id")))
                .map(PublicMapper::customer).getContent();
    }

    public List<OrderResource> orders(Boolean completed, int page, int size) {
        return orders.findAll(PageRequest.of(Math.max(page, 0), clamp(size),
                        Sort.by(Sort.Direction.DESC, "id")))
                .getContent().stream()
                .filter(o -> completed == null || o.isCompleted() == completed)
                .map(PublicMapper::order)
                .toList();
    }

    public List<PaymentResource> payments(LocalDate from, LocalDate to) {
        return payments.findByDateBetweenOrderByDateDescIdDesc(from, to)
                .stream().map(PublicMapper::payment).toList();
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), 200);
    }
}
