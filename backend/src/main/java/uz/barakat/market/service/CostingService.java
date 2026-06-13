package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.PurchaseLot;
import uz.barakat.market.dto.PurchaseDtos.CostHistory;
import uz.barakat.market.dto.PurchaseDtos.LotRow;
import uz.barakat.market.dto.PurchaseDtos.ValuationReport;
import uz.barakat.market.dto.PurchaseDtos.ValuationRow;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.PurchaseLotRepository;

/**
 * Purchase-price history and inventory valuation. The product's
 * {@code purchasePrice} is the maintained weighted-average cost (WAC); this
 * service exposes the per-receipt cost layers (history) and a FIFO valuation of
 * the stock currently on hand (oldest units assumed sold first → remaining
 * stock valued at the most recent purchase costs).
 */
@Service
@Transactional(readOnly = true)
public class CostingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PurchaseLotRepository lots;
    private final ProductRepository products;

    public CostingService(PurchaseLotRepository lots, ProductRepository products) {
        this.lots = lots;
        this.products = products;
    }

    /** A product's purchase-price history (cost layers, oldest first). */
    public CostHistory history(Long productId) {
        Product p = products.findById(productId)
                .orElseThrow(() -> NotFoundException.of("Mahsulot", productId));
        List<LotRow> rows = lots.findByProductIdOrderByReceiptDateAscIdAsc(productId).stream()
                .map(l -> new LotRow(l.getId(), l.getReceiptDate(), l.getSupplierName(),
                        l.getQty(), l.getUnitCostUsd(), l.getInvoiceNumber(), l.getPoId()))
                .toList();
        return new CostHistory(p.getId(), p.getName(), nz(p.getPurchasePrice()),
                p.getQuantity(), rows);
    }

    /** WAC vs FIFO valuation of all in-stock products. */
    public ValuationReport valuation() {
        // Newest-first lots grouped per product (oldest assumed sold first).
        Map<Long, List<PurchaseLot>> byProduct = new HashMap<>();
        for (PurchaseLot l : lots.findAllByOrderByReceiptDateDescIdDesc()) {
            byProduct.computeIfAbsent(l.getProductId(), k -> new ArrayList<>()).add(l);
        }
        List<ValuationRow> rows = new ArrayList<>();
        BigDecimal wacTotal = ZERO;
        BigDecimal fifoTotal = ZERO;
        for (Product p : products.findAllByOrderByNameAsc()) {
            int qty = p.getQuantity();
            if (qty <= 0) {
                continue;
            }
            BigDecimal wac = nz(p.getPurchasePrice());
            BigDecimal wacValue = wac.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            int need = qty;
            BigDecimal fifoValue = ZERO;
            for (PurchaseLot l : byProduct.getOrDefault(p.getId(), List.of())) {
                if (need <= 0) {
                    break;
                }
                int take = Math.min(need, l.getQty());
                fifoValue = fifoValue.add(nz(l.getUnitCostUsd()).multiply(BigDecimal.valueOf(take)));
                need -= take;
            }
            if (need > 0) {
                // No cost layer for this stock (legacy / opening) — fall back to WAC.
                fifoValue = fifoValue.add(wac.multiply(BigDecimal.valueOf(need)));
            }
            fifoValue = fifoValue.setScale(2, RoundingMode.HALF_UP);
            rows.add(new ValuationRow(p.getId(), p.getName(), qty, wac, wacValue, fifoValue));
            wacTotal = wacTotal.add(wacValue);
            fifoTotal = fifoTotal.add(fifoValue);
        }
        return new ValuationReport(rows, wacTotal, fifoTotal);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}
