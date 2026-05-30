package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.Payment;
import uz.barakat.market.domain.PaymentCategory;
import uz.barakat.market.domain.PaymentDirection;
import uz.barakat.market.domain.PaymentType;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.dto.PageSlice;
import uz.barakat.market.dto.PosDtos.CartItem;
import uz.barakat.market.dto.PosDtos.CheckoutRequest;
import uz.barakat.market.dto.PosDtos.RefundItemRequest;
import uz.barakat.market.dto.PosDtos.RefundRequest;
import uz.barakat.market.dto.PosDtos.SaleItemResponse;
import uz.barakat.market.dto.PosDtos.SaleResponse;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.StockMovementRepository;

/**
 * POS checkout + refund engine — the only place that knows how to
 * atomically:
 *
 * <ol>
 *   <li>Decrement product stock (one {@link StockMovement} per cart line).</li>
 *   <li>Record the Payment-journal entry (one {@link Payment}, except
 *       for fully-on-credit sales).</li>
 *   <li>Snapshot the cart into the {@link Sale} aggregate so the
 *       receipt can be re-rendered and the line can be refunded later.</li>
 * </ol>
 *
 * Discount handling: line-level discount lives on each {@link SaleItem};
 * an additional sale-wide discount (percent and/or flat) is recorded on
 * the {@link Sale} itself. The {@link Payment}'s {@code amount} is the
 * net total after all discounts (= what the customer actually paid).
 */
@Service
@Transactional
public class PosService {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final PaymentRepository payments;
    private final StockMovementRepository movements;
    private final CustomerRepository customers;
    private final RealtimeBus realtime;
    private final PromoService promos;
    private final LoyaltyService loyalty;

    public PosService(SaleRepository sales, ProductRepository products,
                      PaymentRepository payments, StockMovementRepository movements,
                      CustomerRepository customers, RealtimeBus realtime,
                      PromoService promos, LoyaltyService loyalty) {
        this.sales = sales;
        this.products = products;
        this.payments = payments;
        this.movements = movements;
        this.customers = customers;
        this.realtime = realtime;
        this.promos = promos;
        this.loyalty = loyalty;
    }

    // =============================================================== checkout

    public SaleResponse checkout(CheckoutRequest req) {
        if (req == null || req.items() == null || req.items().isEmpty()) {
            throw new BadRequestException("Savatcha bo'sh");
        }
        PaymentType method = parseMethod(req.paymentMethod());

        Sale sale = new Sale();
        sale.setPaymentMethod(method.name());
        sale.setNote(blankToNull(req.note()));
        sale.setDiscountAmount(nz(req.discountAmount()));
        sale.setDiscountPercent(nz(req.discountPercent()));
        if (req.customerId() != null) {
            sale.setCustomerId(req.customerId());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        Set<Long> seenIds = new HashSet<>();
        for (CartItem c : req.items()) {
            if (c.productId() == null) {
                throw new BadRequestException("Mahsulot tanlanmagan");
            }
            if (c.quantity() <= 0) {
                throw new BadRequestException("Miqdor 0 dan katta bo'lishi kerak");
            }
            if (!seenIds.add(c.productId())) {
                throw new BadRequestException(
                        "Bir xil mahsulot ikki marta: id=" + c.productId());
            }
            Product p = products.findById(c.productId())
                    .orElseThrow(() -> NotFoundException.of("Mahsulot", c.productId()));
            if (p.getQuantity() < c.quantity()) {
                throw new BadRequestException(
                        "Yetarli ombor yo'q: " + p.getName()
                        + " (qoldiq " + p.getQuantity() + ", talab " + c.quantity() + ")");
            }
            BigDecimal unit = nz(p.getSalePrice());
            BigDecimal gross = unit.multiply(BigDecimal.valueOf(c.quantity()));
            BigDecimal lineDisc = nz(c.lineDiscountUzs());
            if (lineDisc.compareTo(gross) > 0) {
                throw new BadRequestException(
                        "Chegirma chiziq summasidan oshib ketdi: " + p.getName());
            }
            BigDecimal lineTotal = gross.subtract(lineDisc);

            SaleItem item = new SaleItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductSku(p.getBarcode());
            item.setQuantity(c.quantity());
            item.setUnitPriceUzs(unit);
            item.setLineDiscountUzs(lineDisc);
            item.setLineTotalUzs(lineTotal);
            sale.addItem(item);

            subtotal = subtotal.add(gross);

            // Decrement stock + log the movement.
            int newQty = p.getQuantity() - c.quantity();
            p.setQuantity(newQty);
            products.save(p);

            StockMovement mv = new StockMovement();
            mv.setProductId(p.getId());
            mv.setDelta(-c.quantity());
            mv.setResultingQuantity(newQty);
            mv.setReason(StockReason.SALE);
            mv.setNote("POS sale");
            // Freeze the price for historical profit reporting.
            mv.setUnitSalePrice(p.getSalePrice());
            mv.setUnitCostPrice(p.getPurchasePrice());
            movements.save(mv);

            // Broadcast stock change — live warehouse view picks this up.
            realtime.publishStock(p.getId(), p.getName(), -c.quantity(),
                    newQty, "SALE", p.getShopId());
        }

        // Auto-apply the best matching active promo campaign — folds into
        // the sale's flat discountAmount so the receipt + journal already
        // include it. Cashier-typed manual discount stacks on top.
        List<PromoService.CartLine> promoCart = new ArrayList<>();
        for (SaleItem si : sale.getItems()) {
            Product p = products.findById(si.getProductId()).orElse(null);
            promoCart.add(new PromoService.CartLine(
                    si.getProductId(),
                    p == null ? null : p.getCategoryId(),
                    si.getQuantity(),
                    si.getUnitPriceUzs()));
        }
        PromoService.PromoMatch match = promos.findBest(promoCart);
        if (match != null) {
            sale.setDiscountAmount(sale.getDiscountAmount().add(match.discountUzs()));
            String tag = match.reason();
            sale.setNote(sale.getNote() == null ? tag : sale.getNote() + " | " + tag);
        }

        // Apply sale-wide discount: flat amount + percent (in that order).
        BigDecimal totalAfterLines = subtotal.subtract(
                sale.getItems().stream().map(SaleItem::getLineDiscountUzs)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal afterFlat = totalAfterLines.subtract(sale.getDiscountAmount());
        if (afterFlat.signum() < 0) afterFlat = BigDecimal.ZERO;
        BigDecimal percent = sale.getDiscountPercent();
        BigDecimal afterPercent = percent.signum() == 0
                ? afterFlat
                : afterFlat.multiply(BigDecimal.ONE.subtract(percent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
        BigDecimal total = afterPercent.setScale(2, RoundingMode.HALF_UP);
        sale.setSubtotalUzs(subtotal);
        sale.setTotalUzs(total);

        // Book the money in the Payment journal — skipped for fully-on-credit
        // sales (those are reflected in customer_debts, not payments).
        if (method != PaymentType.QARZGA) {
            Payment pay = new Payment();
            pay.setDate(LocalDate.now());
            pay.setDirection(PaymentDirection.INCOMING);
            pay.setCategory(PaymentCategory.CUSTOMER);
            pay.setAmount(total);
            pay.setMethod(method);
            pay.setDiscountAmount(sale.getDiscountAmount());
            pay.setDiscountPercent(sale.getDiscountPercent());
            if (req.customerId() != null) {
                Customer c = customers.findById(req.customerId()).orElse(null);
                if (c != null) {
                    pay.setParty(c.getName());
                    pay.setCustomerId(c.getId());
                }
            }
            pay.setNote("POS sale" + (sale.getNote() == null ? "" : ": " + sale.getNote()));
            payments.save(pay);
            sale.setPaymentId(pay.getId());
        }

        Sale saved = sales.save(sale);
        // Credit loyalty points if this sale is tied to a customer.
        if (saved.getCustomerId() != null) {
            customers.findById(saved.getCustomerId())
                    .ifPresent(c -> loyalty.credit(c, saved.getTotalUzs()));
        }
        // Broadcast the sale itself — POS history / monitor pages live-update.
        realtime.publishSale(saved.getId(), saved.getTotalUzs(),
                saved.getPaymentMethod(), saved.getShopId());
        return toResponse(saved);
    }

    // ================================================================ refund

    public SaleResponse refund(Long saleId, RefundRequest req) {
        Sale sale = sales.findById(saleId)
                .orElseThrow(() -> NotFoundException.of("Sotuv", saleId));
        if (sale.isFullyRefunded()) {
            throw new BadRequestException("Bu sotuv allaqachon to'liq qaytarilgan");
        }

        // Build the (saleItemId -> qty) map. Empty list => refund every
        // remaining unit on every line.
        List<RefundItemRequest> requested = req == null || req.items() == null
                ? List.of()
                : req.items();
        if (requested.isEmpty()) {
            for (SaleItem si : sale.getItems()) {
                int remaining = si.getQuantity() - si.getRefundedQty();
                if (remaining > 0) {
                    requested = new ArrayList<>(requested);
                    requested.add(new RefundItemRequest(si.getId(), remaining));
                }
            }
        }

        BigDecimal refundedAmount = BigDecimal.ZERO;
        for (RefundItemRequest r : requested) {
            SaleItem si = findItem(sale, r.saleItemId());
            int remaining = si.getQuantity() - si.getRefundedQty();
            if (r.quantity() > remaining) {
                throw new BadRequestException(
                        "Qaytarish miqdori qoldiqdan ko'p: "
                        + si.getProductName() + " (qoldiq " + remaining + ")");
            }
            si.setRefundedQty(si.getRefundedQty() + r.quantity());

            // Restore stock.
            if (si.getProductId() != null) {
                Product p = products.findById(si.getProductId()).orElse(null);
                if (p != null) {
                    int newQty = p.getQuantity() + r.quantity();
                    p.setQuantity(newQty);
                    products.save(p);

                    StockMovement mv = new StockMovement();
                    mv.setProductId(p.getId());
                    mv.setDelta(r.quantity());
                    mv.setResultingQuantity(newQty);
                    mv.setReason(StockReason.RETURN);
                    mv.setNote("Refund sale#" + sale.getId());
                    movements.save(mv);

                    realtime.publishStock(p.getId(), p.getName(), r.quantity(),
                            newQty, "RETURN", p.getShopId());
                }
            }

            // Pro-rated refund amount for this line.
            BigDecimal unitNet = si.getQuantity() == 0
                    ? BigDecimal.ZERO
                    : si.getLineTotalUzs().divide(
                            BigDecimal.valueOf(si.getQuantity()), 2, RoundingMode.HALF_UP);
            refundedAmount = refundedAmount.add(
                    unitNet.multiply(BigDecimal.valueOf(r.quantity())));
        }

        // Apply the sale-wide discount proportionally to the refund.
        if (sale.getSubtotalUzs().signum() > 0) {
            BigDecimal saleDiscountRatio = BigDecimal.ONE.subtract(
                    sale.getTotalUzs().divide(sale.getSubtotalUzs(), 4, RoundingMode.HALF_UP));
            refundedAmount = refundedAmount.subtract(
                    refundedAmount.multiply(saleDiscountRatio).setScale(2, RoundingMode.HALF_UP));
        }

        sale.setRefundedTotalUzs(sale.getRefundedTotalUzs().add(refundedAmount));
        sale.setRefundedAt(LocalDateTime.now());

        // If every remaining line has been fully refunded, flip the flag.
        boolean stillUnRefunded = sale.getItems().stream()
                .anyMatch(it -> it.getRefundedQty() < it.getQuantity());
        if (!stillUnRefunded) sale.setFullyRefunded(true);

        // Book a counter-payment (OUTGOING) so the journal stays balanced.
        if (refundedAmount.signum() > 0
                && !"QARZGA".equals(sale.getPaymentMethod())) {
            Payment refundPay = new Payment();
            refundPay.setDate(LocalDate.now());
            refundPay.setDirection(PaymentDirection.OUTGOING);
            refundPay.setCategory(PaymentCategory.CUSTOMER);
            refundPay.setAmount(refundedAmount);
            refundPay.setMethod(parseMethod(sale.getPaymentMethod()));
            refundPay.setNote("Refund sale#" + sale.getId()
                    + (req != null && req.reason() != null ? " — " + req.reason() : ""));
            if (sale.getCustomerId() != null) {
                refundPay.setCustomerId(sale.getCustomerId());
            }
            payments.save(refundPay);
        }

        return toResponse(sales.save(sale));
    }

    // ============================================================== queries

    @Transactional(readOnly = true)
    public PageSlice<SaleResponse> recent(int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);   // clamp the page size
        Slice<Sale> slice = sales.findByOrderByCreatedAtDescIdDesc(PageRequest.of(p, s));
        return new PageSlice<>(
                slice.getContent().stream().map(this::toResponse).toList(),
                slice.hasNext());
    }

    @Transactional(readOnly = true)
    public SaleResponse byId(Long id) {
        return toResponse(sales.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sotuv", id)));
    }

    // ============================================================== helpers

    private static SaleItem findItem(Sale sale, Long itemId) {
        return sale.getItems().stream()
                .filter(it -> itemId.equals(it.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Sotuv qatori topilmadi: id=" + itemId));
    }

    private SaleResponse toResponse(Sale s) {
        String customerName = null;
        if (s.getCustomerId() != null) {
            customerName = customers.findById(s.getCustomerId())
                    .map(Customer::getName).orElse(null);
        }
        List<SaleItemResponse> items = s.getItems().stream()
                .map(i -> new SaleItemResponse(
                        i.getId(), i.getProductId(), i.getProductName(), i.getProductSku(),
                        i.getQuantity(), i.getRefundedQty(),
                        i.getUnitPriceUzs(), i.getLineDiscountUzs(), i.getLineTotalUzs()))
                .toList();
        return new SaleResponse(
                s.getId(), s.getCreatedAt(),
                s.getPaymentId(), s.getCustomerId(), customerName,
                s.getSubtotalUzs(), s.getDiscountAmount(), s.getDiscountPercent(),
                s.getTotalUzs(), s.getRefundedTotalUzs(),
                s.isFullyRefunded(), s.getRefundedAt(),
                s.getPaymentMethod(), s.getNote(), items);
    }

    private static PaymentType parseMethod(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("To'lov turi tanlanmagan");
        }
        try {
            return PaymentType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Noma'lum to'lov turi: " + name);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
