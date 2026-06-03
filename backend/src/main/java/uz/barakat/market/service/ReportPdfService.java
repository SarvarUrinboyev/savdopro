package uz.barakat.market.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.service.ReportPdfRenderer.InventoryRow;
import uz.barakat.market.service.ReportPdfRenderer.LedgerRow;
import uz.barakat.market.service.ReportPdfRenderer.SalesReportInput;
import uz.barakat.market.service.ReportPdfRenderer.SalesRow;

/**
 * Pulls the data the {@link ReportPdfRenderer} needs and hands back the
 * rendered PDF bytes.
 *
 * <p>These reports run <em>native</em> SQL, which Hibernate's tenant
 * {@code @Filter} does NOT rewrite — so every query must scope itself
 * explicitly with {@code AND shop_id IN (:shopIds)} bound from
 * {@link TenantContext#activeScope()}. Forgetting that leaks another
 * shop's data; an empty scope yields an empty report.
 */
@Service
@Transactional(readOnly = true)
public class ReportPdfService {

    private final ReportPdfRenderer renderer;
    private final CustomerRepository customers;

    @PersistenceContext
    private EntityManager em;

    public ReportPdfService(ReportPdfRenderer renderer, CustomerRepository customers) {
        this.renderer = renderer;
        this.customers = customers;
    }

    /**
     * Sales summary for the given range. Aggregates every INCOMING
     * payment row by date, splitting NAQD (cash) from KARTA / other
     * card-like methods. Returns the PDF bytes for the controller to
     * stream back.
     */
    public byte[] salesReport(LocalDate from, LocalDate to) {
        LocalDate effFrom = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate effTo   = to   == null ? LocalDate.now()                : to;

        List<SalesRow> rows = new ArrayList<>();
        int totalItems = 0;
        BigDecimal totalCash = BigDecimal.ZERO;
        BigDecimal totalCard = BigDecimal.ZERO;
        BigDecimal totalRev  = BigDecimal.ZERO;
        List<Long> scope = TenantContext.activeScope();
        if (!scope.isEmpty()) {
            Query q = em.createNativeQuery(
                    "SELECT date, "
                    + "       COUNT(*)                                   AS cnt, "
                    + "       COALESCE(SUM(CASE WHEN method = 'NAQD' "
                    + "                         THEN amount ELSE 0 END), 0) AS cash, "
                    + "       COALESCE(SUM(CASE WHEN method <> 'NAQD' "
                    + "                         THEN amount ELSE 0 END), 0) AS card, "
                    + "       COALESCE(SUM(amount), 0)                   AS total "
                    + "FROM payments "
                    + "WHERE direction = 'INCOMING' "
                    + "  AND shop_id IN (:shopIds) "
                    + "  AND date BETWEEN :from AND :to "
                    + "GROUP BY date "
                    + "ORDER BY date DESC");
            q.setParameter("shopIds", scope);
            q.setParameter("from", effFrom);
            q.setParameter("to", effTo);
            @SuppressWarnings("unchecked")
            List<Object[]> rs = q.getResultList();
            for (Object[] r : rs) {
                LocalDate d = toLocalDate(r[0]);
                int cnt = ((Number) r[1]).intValue();
                BigDecimal cash = toDecimal(r[2]);
                BigDecimal card = toDecimal(r[3]);
                BigDecimal tot  = toDecimal(r[4]);
                rows.add(new SalesRow(d, cnt, cash, card, tot));
                totalItems += cnt;
                totalCash = totalCash.add(cash);
                totalCard = totalCard.add(card);
                totalRev  = totalRev.add(tot);
            }
        }

        String subtitle = effFrom.equals(effTo)
                ? formatDate(effFrom)
                : formatDate(effFrom) + " — " + formatDate(effTo);
        SalesReportInput input = new SalesReportInput(
                subtitle, rows, totalItems, totalCash, totalCard, totalRev);
        return renderer.renderSalesReport(input);
    }

    /**
     * Snapshot of every product's current stock in the active shop,
     * sorted A→Z. Out-of-stock rows are still included so the report
     * doubles as a reorder shopping list.
     */
    public byte[] inventoryReport(String shopLabel) {
        // Column names mirror the JPA mappings on Product (quantity:int,
        // sale_price:BigDecimal). We cast `quantity` to NUMERIC so the
        // toDecimal helper doesn't fan out per-row type checks.
        List<InventoryRow> rows = new ArrayList<>();
        List<Long> scope = TenantContext.activeScope();
        if (!scope.isEmpty()) {
            Query q = em.createNativeQuery(
                    "SELECT name, barcode, unit, "
                    + "       CAST(COALESCE(quantity,   0) AS NUMERIC(15,3)) AS qty, "
                    + "       CAST(COALESCE(sale_price, 0) AS NUMERIC(15,2)) AS price "
                    + "FROM products "
                    + "WHERE shop_id IN (:shopIds) "
                    + "ORDER BY name ASC");
            q.setParameter("shopIds", scope);
            @SuppressWarnings("unchecked")
            List<Object[]> rs = q.getResultList();
            for (Object[] r : rs) {
                rows.add(new InventoryRow(
                        (String) r[0], (String) r[1], (String) r[2],
                        toDecimal(r[3]), toDecimal(r[4])));
            }
        }
        return renderer.renderInventoryReport(
                shopLabel == null ? "Joriy do'kon" : shopLabel, rows);
    }

    /**
     * One customer's transaction history as a running ledger. The
     * "opening balance" is the customer's debt before the first
     * transaction in our records (i.e. {@code 0} for accounts created
     * inside the app — there's no carry-over to track yet).
     */
    public byte[] customerLedger(Long customerId) {
        // findById bypasses the Hibernate tenant @Filter, so verify the customer
        // is in the caller's shop scope before exposing their ledger.
        List<Long> scope = TenantContext.activeScope();
        var customer = customers.findById(customerId)
                .filter(c -> scope.contains(c.getShopId()))
                .orElseThrow(() -> NotFoundException.of("Mijoz", customerId));

        Query q = em.createNativeQuery(
                "SELECT date, type, description, "
                + "       COALESCE(amount, 0) AS amount "
                + "FROM customer_transactions "
                + "WHERE customer_id = :cid "
                + "  AND shop_id IN (:shopIds) "
                + "ORDER BY date ASC, id ASC");
        q.setParameter("cid", customerId);
        q.setParameter("shopIds", scope);
        @SuppressWarnings("unchecked")
        List<Object[]> rs = q.getResultList();

        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal running = opening;
        List<LedgerRow> rows = new ArrayList<>(rs.size());
        for (Object[] r : rs) {
            LocalDate d = toLocalDate(r[0]);
            String kind = (String) r[1];
            String desc = (String) r[2];
            BigDecimal amount = toDecimal(r[3]);
            running = running.add(signedAmount(kind, amount));
            rows.add(new LedgerRow(d, prettyKind(kind),
                    desc == null ? "" : desc, amount, running));
        }
        return renderer.renderCustomerLedger(
                customer.getName(), customer.getPhone(), opening, rows, running);
    }

    // ------------------------------------------------------------ helpers

    /**
     * Customer ledger sign convention: GOODS (we gave items on credit)
     * increases what they owe us; PAYMENT (they paid us) decreases it.
     * Unknown types are treated as neutral so legacy rows can't silently
     * skew the running balance.
     */
    private static BigDecimal signedAmount(String type, BigDecimal amount) {
        if (type == null) return BigDecimal.ZERO;
        return switch (type.toUpperCase()) {
            case "GOODS", "GIVE", "SALE", "DEBT_ADD" -> amount;
            case "PAYMENT", "PAY", "DEBT_PAY"        -> amount.negate();
            default -> BigDecimal.ZERO;
        };
    }

    private static String prettyKind(String type) {
        if (type == null) return "—";
        return switch (type.toUpperCase()) {
            case "GOODS", "GIVE", "SALE", "DEBT_ADD" -> "Tovar berildi";
            case "PAYMENT", "PAY", "DEBT_PAY"        -> "To'lov olindi";
            default -> type;
        };
    }

    private static LocalDate toLocalDate(Object v) {
        if (v == null) return LocalDate.now();
        if (v instanceof LocalDate ld) return ld;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(v.toString().substring(0, 10));
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }

    private static String formatDate(LocalDate d) {
        return d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    /**
     * Roll-up of the most-recent sales day, exposed for the alerts
     * pipeline that Phase 4.2 will plug into. Kept in this class so the
     * dependency stays one-way: alerts code never has to reach into
     * the renderer directly. {@code null} means "today".
     */
    @SuppressWarnings("unused")
    public Map<String, BigDecimal> dailyTotals(LocalDate date) {
        LocalDate eff = date == null ? LocalDate.now() : date;
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        out.put("cash",  BigDecimal.ZERO);
        out.put("card",  BigDecimal.ZERO);
        out.put("total", BigDecimal.ZERO);
        List<Long> scope = TenantContext.activeScope();
        if (scope.isEmpty()) return out;
        Query q = em.createNativeQuery(
                "SELECT COALESCE(SUM(CASE WHEN method = 'NAQD' "
                + "                       THEN amount ELSE 0 END), 0) AS cash, "
                + "       COALESCE(SUM(CASE WHEN method <> 'NAQD' "
                + "                       THEN amount ELSE 0 END), 0) AS card, "
                + "       COALESCE(SUM(amount), 0)                  AS total "
                + "FROM payments "
                + "WHERE direction = 'INCOMING' AND shop_id IN (:shopIds) AND date = :d");
        q.setParameter("shopIds", scope);
        q.setParameter("d", eff);
        Object[] r = (Object[]) q.getSingleResult();
        out.put("cash",  toDecimal(r[0]));
        out.put("card",  toDecimal(r[1]));
        out.put("total", toDecimal(r[2]));
        return out;
    }
}
