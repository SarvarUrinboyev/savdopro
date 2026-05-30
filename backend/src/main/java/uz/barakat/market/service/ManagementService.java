package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.ManagementCost;
import uz.barakat.market.domain.ManagementCostType;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.dto.ManagementCostRequest;
import uz.barakat.market.dto.ManagementCostResponse;
import uz.barakat.market.dto.ManagementSummary;
import uz.barakat.market.dto.SoldGoodsLine;
import uz.barakat.market.dto.SoldGoodsReport;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.ManagementCostRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.StockMovementRepository;

/**
 * The Management page ("Menejment"). Sales volume and gross profit are
 * derived automatically from warehouse SALE stock movements; salary /
 * tax / other costs are entered by hand. Every figure in the summary is
 * expressed in USD - the frontend converts for display.
 */
@Service
@Transactional
public class ManagementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ManagementCostRepository costs;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final MoneyConverter converter;

    public ManagementService(ManagementCostRepository costs,
                             StockMovementRepository movements,
                             ProductRepository products,
                             MoneyConverter converter) {
        this.costs = costs;
        this.movements = movements;
        this.products = products;
        this.converter = converter;
    }

    @Transactional(readOnly = true)
    public ManagementSummary summary(LocalDate from, LocalDate to) {
        // --- Sales: every SALE stock movement inside the window (prices are USD) ---
        List<StockMovement> sales = movements.findByReasonAndCreatedAtBetween(
                StockReason.SALE, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        Map<Long, Product> priceBook = products.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        BigDecimal revenue = ZERO;
        BigDecimal costOfGoods = ZERO;
        int unitsSold = 0;
        for (StockMovement m : sales) {
            int units = Math.max(0, -m.getDelta());
            Product product = priceBook.get(m.getProductId());
            if (units == 0 || product == null) {
                continue;
            }
            BigDecimal qty = BigDecimal.valueOf(units);
            BigDecimal salePrice = priceAt(m.getUnitSalePrice(), product.getSalePrice());
            BigDecimal costPrice = priceAt(m.getUnitCostPrice(), product.getPurchasePrice());
            revenue = revenue.add(salePrice.multiply(qty));
            costOfGoods = costOfGoods.add(costPrice.multiply(qty));
            unitsSold += units;
        }
        BigDecimal grossProfit = revenue.subtract(costOfGoods);

        // --- Costs: manual salary / tax / other entries, each converted to USD ---
        List<ManagementCost> costRows =
                costs.findByDateBetweenOrderByDateDescIdDesc(from, to);
        BigDecimal salary = sumOfType(costRows, ManagementCostType.SALARY);
        BigDecimal tax = sumOfType(costRows, ManagementCostType.TAX);
        BigDecimal other = sumOfType(costRows, ManagementCostType.OTHER);
        BigDecimal costTotal = salary.add(tax).add(other);

        List<ManagementCostResponse> costList = costRows.stream()
                .map(Mappers::managementCost).toList();

        return new ManagementSummary(from, to, revenue, costOfGoods, grossProfit, unitsSold,
                salary, tax, other, costTotal, grossProfit.subtract(costTotal), costList);
    }

    /**
     * The list of goods sold inside a date range — one line per SALE stock
     * movement, priced with the product's current sale / cost price. Powers
     * the Management CSV / Excel / PDF export.
     */
    @Transactional(readOnly = true)
    public SoldGoodsReport soldGoods(LocalDate from, LocalDate to) {
        List<StockMovement> sales = movements.findByReasonAndCreatedAtBetween(
                StockReason.SALE, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        Map<Long, Product> priceBook = products.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<SoldGoodsLine> lines = new ArrayList<>();
        int totalUnits = 0;
        BigDecimal totalRevenue = ZERO;
        BigDecimal totalCost = ZERO;
        for (StockMovement m : sales) {
            int units = Math.max(0, -m.getDelta());
            Product product = priceBook.get(m.getProductId());
            if (units == 0 || product == null) {
                continue;
            }
            BigDecimal qty = BigDecimal.valueOf(units);
            BigDecimal salePrice = priceAt(m.getUnitSalePrice(), product.getSalePrice());
            BigDecimal costPrice = priceAt(m.getUnitCostPrice(), product.getPurchasePrice());
            BigDecimal lineRevenue = salePrice.multiply(qty);
            BigDecimal lineCost = costPrice.multiply(qty);
            lines.add(new SoldGoodsLine(
                    m.getCreatedAt(), product.getName(), units,
                    salePrice, costPrice,
                    lineRevenue, lineCost, lineRevenue.subtract(lineCost),
                    m.getNote()));
            totalUnits += units;
            totalRevenue = totalRevenue.add(lineRevenue);
            totalCost = totalCost.add(lineCost);
        }
        lines.sort(Comparator.comparing(SoldGoodsLine::soldAt));
        return new SoldGoodsReport(from, to, lines, totalUnits,
                totalRevenue, totalCost, totalRevenue.subtract(totalCost));
    }

    public ManagementCostResponse createCost(ManagementCostRequest request) {
        ManagementCost cost = new ManagementCost();
        apply(cost, request);
        return Mappers.managementCost(costs.save(cost));
    }

    public ManagementCostResponse updateCost(Long id, ManagementCostRequest request) {
        ManagementCost cost = find(id);
        apply(cost, request);
        return Mappers.managementCost(costs.save(cost));
    }

    public void deleteCost(Long id) {
        costs.delete(find(id));
    }

    // --------------------------------------------------------------- helpers

    private static void apply(ManagementCost cost, ManagementCostRequest request) {
        cost.setDate(request.date() != null ? request.date() : LocalDate.now());
        cost.setType(request.type());
        cost.setName(request.name().strip());
        cost.setAmount(request.amount());
        cost.setCurrency(request.currency() != null ? request.currency() : Currency.UZS);
        cost.setNote(request.note() == null || request.note().isBlank()
                ? null : request.note().strip());
    }

    /** Snapshot price if recorded on the movement, else the product's current
     *  price (legacy rows from before V23). */
    private static BigDecimal priceAt(BigDecimal snapshot, BigDecimal current) {
        if (snapshot != null) {
            return snapshot;
        }
        return current != null ? current : ZERO;
    }

    /** Sums costs of one type, converting each entry to USD. */
    private BigDecimal sumOfType(List<ManagementCost> rows, ManagementCostType type) {
        return rows.stream()
                .filter(c -> c.getType() == type)
                .map(c -> converter.toUsd(c.getAmount(), c.getCurrency()))
                .reduce(ZERO, BigDecimal::add);
    }

    private ManagementCost find(Long id) {
        return costs.findById(id).orElseThrow(() -> NotFoundException.of("Xarajat", id));
    }
}
