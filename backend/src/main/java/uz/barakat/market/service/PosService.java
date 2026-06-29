package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.CustomerTxType;
import uz.barakat.market.domain.Payment;
import uz.barakat.market.domain.PaymentCategory;
import uz.barakat.market.domain.PaymentDirection;
import uz.barakat.market.domain.PaymentType;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Sale;
import uz.barakat.market.domain.SaleItem;
import uz.barakat.market.domain.SoldDevice;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.dto.PageSlice;
import uz.barakat.market.dto.PosDtos.CartItem;
import uz.barakat.market.dto.PosDtos.CheckoutRequest;
import uz.barakat.market.dto.PosDtos.DeviceInput;
import uz.barakat.market.dto.PosDtos.RefundItemRequest;
import uz.barakat.market.dto.PosDtos.RefundRequest;
import uz.barakat.market.dto.PosDtos.SaleItemResponse;
import uz.barakat.market.dto.PosDtos.SaleResponse;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.CustomerTransactionRepository;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SaleRepository;
import uz.barakat.market.repository.SoldDeviceRepository;
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

    private static final DateTimeFormatter RECEIPT_DT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final SaleRepository sales;
    private final ProductRepository products;
    private final PaymentRepository payments;
    private final StockMovementRepository movements;
    private final CustomerRepository customers;
    private final CustomerTransactionRepository customerTx;
    private final SoldDeviceRepository soldDevices;
    private final RealtimeBus realtime;
    private final PromoService promos;
    private final LoyaltyService loyalty;
    private final CustomerNotificationService notifications;
    private final AnomalyAlertService anomalyAlerts;
    private final ApplicationEventPublisher events;

    public PosService(SaleRepository sales, ProductRepository products,
                      PaymentRepository payments, StockMovementRepository movements,
                      CustomerRepository customers, CustomerTransactionRepository customerTx,
                      SoldDeviceRepository soldDevices,
                      RealtimeBus realtime,
                      PromoService promos, LoyaltyService loyalty,
                      CustomerNotificationService notifications,
                      AnomalyAlertService anomalyAlerts,
                      ApplicationEventPublisher events) {
        this.sales = sales;
        this.products = products;
        this.payments = payments;
        this.movements = movements;
        this.customers = customers;
        this.customerTx = customerTx;
        this.soldDevices = soldDevices;
        this.realtime = realtime;
        this.promos = promos;
        this.loyalty = loyalty;
        this.notifications = notifications;
        this.anomalyAlerts = anomalyAlerts;
        this.events = events;
    }

    // =============================================================== checkout

    public SaleResponse checkout(CheckoutRequest req) {
        return checkout(req, null);
    }

    /** {@code cashier} = the logged-in user's username, stamped on the sale. */
    public SaleResponse checkout(CheckoutRequest req, String cashier) {
        if (req == null || req.items() == null || req.items().isEmpty()) {
            throw new BadRequestException("Savatcha bo'sh");
        }
        // Idempotency: if this checkout was already accepted under the same
        // client_ref (offline replay after a lost response), return the
        // existing sale instead of creating a duplicate — never double-charge.
        String clientRef = req.clientRef() == null ? null : req.clientRef().strip();
        if (clientRef != null && !clientRef.isEmpty()) {
            Optional<Sale> existing = sales.findFirstByClientRef(clientRef);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        PaymentType method = parseMethod(req.paymentMethod());

        Sale sale = new Sale();
        sale.setPaymentMethod(method.name());
        sale.setCashier(blankToNull(cashier));
        if (clientRef != null && !clientRef.isEmpty()) {
            sale.setClientRef(clientRef);
        }
        sale.setNote(blankToNull(req.note()));
        sale.setDiscountAmount(nz(req.discountAmount()));
        sale.setDiscountPercent(nz(req.discountPercent()));
        if (req.customerId() != null) {
            sale.setCustomerId(req.customerId());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        Set<Long> seenIds = new HashSet<>();
        // Per-unit IMEI/serial captures, keyed by productId (unique per sale).
        // Saved as sold_devices rows after the sale (and its item ids) persist.
        Map<Long, List<DeviceInput>> deviceCaptures = new HashMap<>();
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
            // Freeze the cost price now so COGS stays correct even if the
            // product's purchase_price is edited after this sale.
            item.setCostAtSaleUzs(nz(p.getPurchasePrice()));
            sale.addItem(item);

            // Capture per-unit IMEIs for IMEI-tracked goods (saved after the sale).
            if (c.devices() != null && !c.devices().isEmpty()) {
                if (c.devices().size() > c.quantity()) {
                    throw new BadRequestException(
                            "IMEI soni miqdordan ko'p: " + p.getName());
                }
                deviceCaptures.put(p.getId(), c.devices());
            }

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
        // On-credit (QARZGA) sale to a known customer: raise what they owe in
        // their ledger (GOODS line) so the credit sale actually shows up as
        // customer debt. Walk-in credit sales (no customer) only hit the
        // payment/receivable ledger. A repayment is a later PAYMENT line.
        if (method == PaymentType.QARZGA && saved.getCustomerId() != null) {
            recordCreditSaleDebt(saved);
        }
        // Record per-unit IMEIs now that the sale + its item ids exist.
        if (!deviceCaptures.isEmpty()) {
            recordSoldDevices(saved, deviceCaptures);
        }
        // Best-effort owner alert if any line sold below cost (loss / mis-pricing).
        anomalyAlerts.checkBelowCost(saved);
        // Credit loyalty points and push the receipt if tied to a customer.
        if (saved.getCustomerId() != null) {
            customers.findById(saved.getCustomerId()).ifPresent(c -> {
                loyalty.credit(c, saved.getTotalUzs());
                // Best-effort: send the receipt to the customer's channel.
                notifications.notify(c, customerReceiptText(saved, c));
            });
        }
        // Broadcast the sale itself — POS history / monitor pages live-update.
        realtime.publishSale(saved.getId(), saved.getTotalUzs(),
                saved.getPaymentMethod(), saved.getShopId());
        // Post to the double-entry ledger AFTER this tx commits (best-effort;
        // a ledger failure never affects the sale — see LedgerPostingListener).
        events.publishEvent(new LedgerEvents.SalePosted(saved.getId()));
        return toResponse(saved);
    }

    /**
     * Raises the customer's outstanding balance by the net total of an on-credit
     * sale, as one GOODS line in their ledger. Stock is NOT touched here — the
     * checkout already moved it — this only records what the customer now owes.
     */
    private void recordCreditSaleDebt(Sale saved) {
        CustomerTransaction tx = new CustomerTransaction();
        tx.setCustomerId(saved.getCustomerId());
        tx.setDate(LocalDate.now());
        tx.setType(CustomerTxType.GOODS);
        tx.setAmount(nz(saved.getTotalUzs()));
        tx.setDescription("POS qarz sotuvi #" + saved.getId());
        customerTx.save(tx);
    }

    /**
     * Persist one {@link SoldDevice} per captured unit, linked to the saved sale,
     * its line, and the customer. Entries with no IMEI/serial at all are skipped.
     */
    private void recordSoldDevices(Sale saved, Map<Long, List<DeviceInput>> captures) {
        String customerName = saved.getCustomerId() == null ? null
                : customers.findById(saved.getCustomerId())
                        .map(Customer::getName).orElse(null);
        for (SaleItem item : saved.getItems()) {
            List<DeviceInput> devs = captures.get(item.getProductId());
            if (devs == null) {
                continue;
            }
            for (DeviceInput d : devs) {
                String imei1 = blankToNull(d.imei1());
                String imei2 = blankToNull(d.imei2());
                String serial = blankToNull(d.serial());
                String appleId = blankToNull(d.appleId());
                if (imei1 == null && imei2 == null && serial == null && appleId == null) {
                    continue;   // nothing captured for this unit — skip
                }
                // Prefer flipping an already-registered in-stock unit to SOLD, so the
                // intake record and the sale are ONE row (one device, full history).
                SoldDevice dev = imei1 == null ? null
                        : soldDevices.findFirstByImei1AndStatus(imei1, "IN_STOCK").orElse(null);
                if (dev == null) {
                    // Not pre-registered at intake — create the row now.
                    dev = new SoldDevice();
                    dev.setProductId(item.getProductId());
                    dev.setProductName(item.getProductName());
                    dev.setImei1(imei1);
                    dev.setIntakeDate(saved.getCreatedAt());
                }
                // Fill any ids captured at the till that intake didn't have.
                if (imei2 != null) {
                    dev.setImei2(imei2);
                }
                if (serial != null) {
                    dev.setSerialNumber(serial);
                }
                if (appleId != null) {
                    dev.setAppleId(appleId);
                }
                dev.setSaleId(saved.getId());
                dev.setSaleItemId(item.getId());
                dev.setCustomerId(saved.getCustomerId());
                dev.setCustomerName(customerName);
                dev.setPaymentMethod(saved.getPaymentMethod());
                dev.setSalePriceUzs(item.getUnitPriceUzs());
                dev.setStatus("SOLD");
                dev.setSoldAt(saved.getCreatedAt());
                soldDevices.save(dev);
            }
        }
    }

    // ========================================================= receipt send

    /**
     * Re-sends the receipt for a past sale to its customer over the best
     * channel. Returns the channel name (TELEGRAM / SMS / NONE). Fails only
     * if the sale has no customer attached.
     */
    @Transactional(readOnly = true)
    public String sendReceipt(Long saleId) {
        Sale sale = sales.findById(saleId)
                .orElseThrow(() -> NotFoundException.of("Sotuv", saleId));
        if (sale.getCustomerId() == null) {
            throw new BadRequestException("Bu sotuvga mijoz biriktirilmagan");
        }
        Customer customer = customers.findById(sale.getCustomerId())
                .orElseThrow(() -> NotFoundException.of("Mijoz", sale.getCustomerId()));
        return notifications.notify(customer, customerReceiptText(sale, customer)).name();
    }

    /** Per-cashier performance over [from, to] (inclusive): receipts, net, avg check. */
    @Transactional(readOnly = true)
    public java.util.List<uz.barakat.market.dto.CashierStat> cashierStats(
            java.time.LocalDate from, java.time.LocalDate to) {
        java.time.LocalDateTime f = from.atStartOfDay();
        java.time.LocalDateTime t = to.plusDays(1).atStartOfDay();
        return sales.cashierStats(f, t).stream().map(r -> {
            String name = (r.getCashier() == null || r.getCashier().isBlank())
                    ? "—" : r.getCashier();
            BigDecimal net = r.getNet() == null ? BigDecimal.ZERO : r.getNet();
            BigDecimal avg = r.getReceipts() == 0 ? BigDecimal.ZERO
                    : net.divide(BigDecimal.valueOf(r.getReceipts()), 2, RoundingMode.HALF_UP);
            return new uz.barakat.market.dto.CashierStat(name, r.getReceipts(), net, avg);
        }).toList();
    }

    /** Concise, human-friendly receipt text for a customer message. */
    private String customerReceiptText(Sale sale, Customer customer) {
        StringBuilder sb = new StringBuilder();
        sb.append("🧾 BARAKAT SUPERMARKET\n");
        sb.append("Chek #").append(sale.getId());
        if (sale.getCreatedAt() != null) {
            sb.append(" — ").append(sale.getCreatedAt().format(RECEIPT_DT));
        }
        sb.append("\n\n");
        if (customer.getName() != null && !customer.getName().isBlank()) {
            sb.append("Hurmatli ").append(customer.getName()).append("! 👋\n\n");
        }
        for (SaleItem i : sale.getItems()) {
            sb.append("• ").append(i.getProductName())
              .append(" x").append(i.getQuantity())
              .append(" — ").append(MoneyFormat.usd(i.getLineTotalUzs())).append('\n');
        }
        sb.append("————————————\n");
        sb.append("Jami: ").append(MoneyFormat.usd(sale.getTotalUzs())).append('\n');
        if ("QARZGA".equalsIgnoreCase(sale.getPaymentMethod())) {
            sb.append("To'lov: Qarzga yozildi\n");
        } else if (sale.getPaymentMethod() != null) {
            sb.append("To'lov: ").append(paymentLabel(sale.getPaymentMethod())).append('\n');
        }
        sb.append("\nXaridingiz uchun rahmat! Yana keling 😊");
        return sb.toString();
    }

    private static String paymentLabel(String method) {
        return switch (method == null ? "" : method.toUpperCase()) {
            case "NAQD" -> "Naqd";
            case "KARTA" -> "Karta";
            case "QARZGA" -> "Qarzga";
            default -> method;
        };
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
        List<LedgerEvents.RefundLine> returnedLines = new ArrayList<>();
        for (RefundItemRequest r : requested) {
            SaleItem si = findItem(sale, r.saleItemId());
            int remaining = si.getQuantity() - si.getRefundedQty();
            if (r.quantity() > remaining) {
                throw new BadRequestException(
                        "Qaytarish miqdori qoldiqdan ko'p: "
                        + si.getProductName() + " (qoldiq " + remaining + ")");
            }
            si.setRefundedQty(si.getRefundedQty() + r.quantity());
            returnedLines.add(new LedgerEvents.RefundLine(
                    si.getProductId(), r.quantity(), si.getCostAtSaleUzs()));

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

        Sale persisted = sales.save(sale);
        // Post the refund to the ledger after commit (best-effort). Unique ref
        // per refund event so partial refunds each get their own storno.
        if (refundedAmount.signum() > 0) {
            String ref = "SR:" + persisted.getId() + ":" + System.currentTimeMillis();
            events.publishEvent(new LedgerEvents.SaleRefunded(
                    persisted.getShopId(), persisted.getId(), LocalDate.now(),
                    persisted.getPaymentMethod(), refundedAmount, returnedLines, ref));
        }
        return toResponse(persisted);
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
