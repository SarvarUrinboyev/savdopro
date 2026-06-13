package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.PurchaseLot;
import uz.barakat.market.domain.PurchaseOrder;
import uz.barakat.market.domain.PurchaseOrderLine;
import uz.barakat.market.domain.PurchaseOrderStatus;
import uz.barakat.market.domain.Supplier;
import uz.barakat.market.dto.PurchaseDtos.LineRequest;
import uz.barakat.market.dto.PurchaseDtos.LineResponse;
import uz.barakat.market.dto.PurchaseDtos.PoRequest;
import uz.barakat.market.dto.PurchaseDtos.PoResponse;
import uz.barakat.market.dto.PurchaseDtos.ReceiveLine;
import uz.barakat.market.dto.PurchaseDtos.ReceiveRequest;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.PurchaseLotRepository;
import uz.barakat.market.repository.PurchaseOrderRepository;
import uz.barakat.market.repository.SupplierRepository;

/**
 * Supplier purchase orders: draft → order → (partial) receive → received.
 * Receiving books stock at the actual invoice cost via
 * {@link ProductService#receiveStock} (weighted-average cost + ledger A/P) and
 * records a {@link PurchaseLot} cost layer (price history + FIFO source).
 */
@Service
@Transactional
public class PurchaseOrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PurchaseOrderRepository orders;
    private final PurchaseLotRepository lots;
    private final ProductService productService;
    private final ProductRepository products;
    private final SupplierRepository suppliers;

    public PurchaseOrderService(PurchaseOrderRepository orders, PurchaseLotRepository lots,
                                ProductService productService, ProductRepository products,
                                SupplierRepository suppliers) {
        this.orders = orders;
        this.lots = lots;
        this.productService = productService;
        this.products = products;
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public List<PoResponse> list() {
        return orders.findAllByOrderByIdDesc().stream().map(PurchaseOrderService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PoResponse get(Long id) {
        return toResponse(find(id));
    }

    public PoResponse create(PoRequest req) {
        PurchaseOrder po = new PurchaseOrder();
        po.setStatus(PurchaseOrderStatus.DRAFT);
        applyHeader(po, req);
        applyLines(po, req.lines());
        return toResponse(orders.save(po));
    }

    public PoResponse update(Long id, PoRequest req) {
        PurchaseOrder po = find(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BadRequestException("Faqat qoralama (DRAFT) buyurtmani tahrirlash mumkin");
        }
        applyHeader(po, req);
        po.getLines().clear();
        applyLines(po, req.lines());
        return toResponse(orders.save(po));
    }

    /** DRAFT → ORDERED (sent to the supplier). */
    public PoResponse markOrdered(Long id) {
        PurchaseOrder po = find(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BadRequestException("Buyurtma allaqachon yuborilgan yoki yopilgan");
        }
        po.setStatus(PurchaseOrderStatus.ORDERED);
        if (po.getOrderDate() == null) {
            po.setOrderDate(LocalDate.now());
        }
        return toResponse(orders.save(po));
    }

    public PoResponse cancel(Long id) {
        PurchaseOrder po = find(id);
        if (po.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new BadRequestException("To'liq qabul qilingan buyurtmani bekor qilib bo'lmaydi");
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        return toResponse(orders.save(po));
    }

    public void delete(Long id) {
        PurchaseOrder po = find(id);
        boolean anyReceived = po.getLines().stream().anyMatch(l -> l.getReceivedQty() > 0);
        if (anyReceived) {
            throw new BadRequestException(
                    "Qisman/to'liq qabul qilingan buyurtmani o'chirib bo'lmaydi (bekor qiling)");
        }
        orders.delete(po);
    }

    /**
     * Receives some/all of a PO's lines. Each received line books stock at its
     * actual cost, writes a cost layer, and advances the PO status.
     */
    public PoResponse receive(Long id, ReceiveRequest req) {
        PurchaseOrder po = find(id);
        if (po.getStatus() == PurchaseOrderStatus.CANCELLED
                || po.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new BadRequestException("Bu buyurtmaga tovar qabul qilib bo'lmaydi");
        }
        if (req == null || req.lines() == null || req.lines().isEmpty()) {
            throw new BadRequestException("Qabul qilinadigan qatorlar yo'q");
        }
        LocalDate receiptDate = req.receiptDate() != null ? req.receiptDate() : LocalDate.now();
        String invoice = blankToNull(req.invoiceNumber()) != null
                ? req.invoiceNumber().strip() : po.getInvoiceNumber();

        for (ReceiveLine rl : req.lines()) {
            if (rl.lineId() == null || rl.qty() == null || rl.qty() <= 0) {
                continue;
            }
            PurchaseOrderLine line = po.getLines().stream()
                    .filter(l -> rl.lineId().equals(l.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Qator topilmadi: " + rl.lineId()));
            if (line.getProductId() == null) {
                throw new BadRequestException(
                        "Mahsulot biriktirilmagan qatorni qabul qilib bo'lmaydi: " + line.getProductName());
            }
            int remaining = line.getOrderedQty() - line.getReceivedQty();
            if (rl.qty() > remaining) {
                throw new BadRequestException("Qabul miqdori qoldiqdan ko'p: "
                        + line.getProductName() + " (qoldiq " + remaining + ")");
            }
            BigDecimal unitCost = rl.unitCostUsd() != null ? rl.unitCostUsd() : line.getUnitCostUsd();

            productService.receiveStock(line.getProductId(), rl.qty(), unitCost,
                    "PO #" + po.getId() + " qabul");

            PurchaseLot lot = new PurchaseLot();
            lot.setProductId(line.getProductId());
            lot.setPoId(po.getId());
            lot.setPoLineId(line.getId());
            lot.setSupplierName(po.getSupplierName());
            lot.setReceiptDate(receiptDate);
            lot.setQty(rl.qty());
            lot.setUnitCostUsd(unitCost);
            lot.setInvoiceNumber(invoice);
            lots.save(lot);

            line.setReceivedQty(line.getReceivedQty() + rl.qty());
        }

        boolean allReceived = po.getLines().stream()
                .allMatch(l -> l.getReceivedQty() >= l.getOrderedQty());
        boolean anyReceived = po.getLines().stream().anyMatch(l -> l.getReceivedQty() > 0);
        po.setStatus(allReceived ? PurchaseOrderStatus.RECEIVED
                : anyReceived ? PurchaseOrderStatus.PARTIAL : po.getStatus());
        if (invoice != null) {
            po.setInvoiceNumber(invoice);
            if (po.getInvoiceDate() == null) {
                po.setInvoiceDate(receiptDate);
            }
        }
        return toResponse(orders.save(po));
    }

    // --------------------------------------------------------------- helpers

    private void applyHeader(PurchaseOrder po, PoRequest req) {
        String name = blankToNull(req.supplierName());
        if (name == null && req.supplierId() != null) {
            Supplier s = suppliers.findById(req.supplierId()).orElse(null);
            name = s != null ? s.getName() : null;
        }
        if (name == null) {
            throw new BadRequestException("Yetkazib beruvchi tanlanishi shart");
        }
        po.setSupplierId(req.supplierId());
        po.setSupplierName(name);
        po.setOrderDate(req.orderDate());
        po.setExpectedDate(req.expectedDate());
        po.setInvoiceNumber(blankToNull(req.invoiceNumber()));
        po.setInvoiceDate(req.invoiceDate());
        po.setNote(blankToNull(req.note()));
    }

    private void applyLines(PurchaseOrder po, List<LineRequest> lineReqs) {
        if (lineReqs == null || lineReqs.isEmpty()) {
            throw new BadRequestException("Kamida bitta mahsulot qatori kerak");
        }
        for (LineRequest lr : lineReqs) {
            int qty = lr.orderedQty() != null ? lr.orderedQty() : 0;
            if (qty <= 0) {
                throw new BadRequestException("Buyurtma miqdori 0 dan katta bo'lishi kerak");
            }
            PurchaseOrderLine line = new PurchaseOrderLine();
            String name = blankToNull(lr.productName());
            if (lr.productId() != null) {
                Product p = products.findById(lr.productId()).orElseThrow(
                        () -> NotFoundException.of("Mahsulot", lr.productId()));
                line.setProductId(p.getId());
                name = p.getName();
            }
            if (name == null) {
                throw new BadRequestException("Mahsulot tanlanishi shart");
            }
            line.setProductName(name);
            line.setOrderedQty(qty);
            line.setReceivedQty(0);
            line.setUnitCostUsd(lr.unitCostUsd() != null ? lr.unitCostUsd() : ZERO);
            line.setNote(blankToNull(lr.note()));
            po.addLine(line);
        }
    }

    private PurchaseOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> NotFoundException.of("Buyurtma", id));
    }

    static PoResponse toResponse(PurchaseOrder po) {
        BigDecimal orderedTotal = ZERO;
        BigDecimal receivedTotal = ZERO;
        java.util.List<LineResponse> lineRows = new java.util.ArrayList<>();
        for (PurchaseOrderLine l : po.getLines()) {
            BigDecimal unit = l.getUnitCostUsd() == null ? ZERO : l.getUnitCostUsd();
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(l.getOrderedQty()));
            orderedTotal = orderedTotal.add(lineTotal);
            receivedTotal = receivedTotal.add(unit.multiply(BigDecimal.valueOf(l.getReceivedQty())));
            lineRows.add(new LineResponse(l.getId(), l.getProductId(), l.getProductName(),
                    l.getOrderedQty(), l.getReceivedQty(),
                    Math.max(0, l.getOrderedQty() - l.getReceivedQty()), unit, lineTotal));
        }
        return new PoResponse(po.getId(), po.getSupplierId(), po.getSupplierName(), po.getStatus(),
                po.getOrderDate(), po.getExpectedDate(), po.getInvoiceNumber(), po.getInvoiceDate(),
                po.getNote(), orderedTotal, receivedTotal, po.getCreatedAt(), lineRows);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
