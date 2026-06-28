package uz.barakat.market.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.ShopTransfer;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.ShopRepository;
import uz.barakat.market.repository.ShopTransferRepository;

/**
 * Cross-shop stock transfers.
 *
 * <p>A transfer moves N units of one product from shop A to shop B in
 * the same account. Implemented with native SQL on purpose: the tenant
 * filter on {@code Product} would otherwise hide the source row when
 * we look it up via the active shop context, and the destination row
 * is in a different shop entirely.
 *
 * <p>All the writes happen inside a single {@code @Transactional}
 * boundary so a crash mid-flight can't leave half the stock missing.
 */
@Service
@Transactional
public class TransferService {

    private final ShopRepository shops;
    private final ShopTransferRepository transfers;

    @PersistenceContext
    private EntityManager em;

    public TransferService(ShopRepository shops, ShopTransferRepository transfers) {
        this.shops = shops;
        this.transfers = transfers;
    }

    public record TransferRequest(
            @NotNull(message = "Manba do'kon kerak") Long fromShopId,
            @NotNull(message = "Qabul do'kon kerak") Long toShopId,
            @NotNull(message = "Mahsulot kerak") Long sourceProductId,
            @NotNull(message = "Miqdor kerak") @Positive BigDecimal qty,
            String note) {
    }

    public record TransferResponse(
            Long id,
            Long fromShopId, String fromShopName,
            Long toShopId,   String toShopName,
            Long sourceProductId, Long destProductId,
            String productName, String productBarcode,
            BigDecimal qty,
            String note,
            String createdBy,
            LocalDateTime createdAt) {
    }

    /**
     * Move {@code qty} units of the source product from {@code fromShopId}
     * to {@code toShopId}. If a product with the same barcode already
     * exists in the destination shop, its stock is incremented;
     * otherwise a brand-new product row is created at the destination
     * mirroring the source product's name / price / category.
     */
    public TransferResponse create(Long accountId, String createdBy, TransferRequest r) {
        if (accountId == null) throw new BadRequestException("Akkaunt aniqlanmadi");
        if (r.fromShopId().equals(r.toShopId())) {
            throw new BadRequestException("Manba va qabul do'koni bir xil bo'lishi mumkin emas");
        }
        // Both shops must belong to the caller's account — otherwise we
        // could be moving stock across tenants.
        requireOwnedShop(accountId, r.fromShopId());
        requireOwnedShop(accountId, r.toShopId());

        BigDecimal qty = r.qty();
        if (qty == null || qty.signum() <= 0) {
            throw new BadRequestException("Miqdor 0 dan katta bo'lishi kerak");
        }
        // products.quantity is an INTEGER column — a transfer moves whole units.
        // Reject a fractional request up front instead of letting the DB silently
        // truncate it (which would corrupt the stock count).
        long units;
        try {
            units = qty.stripTrailingZeros().longValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Miqdor butun son bo'lishi kerak");
        }

        // 1. Load source product (native — bypasses tenant filter).
        Object[] src = loadProduct(r.sourceProductId());
        Long srcShop = ((Number) src[1]).longValue();
        if (!srcShop.equals(r.fromShopId())) {
            throw new BadRequestException("Tanlangan mahsulot manba do'konga tegishli emas");
        }
        // quantity comes back from JDBC as an Integer (INTEGER column); read it
        // through Number — casting straight to BigDecimal throws ClassCastException.
        long srcStock = ((Number) src[5]).longValue();
        if (srcStock < units) {
            throw new BadRequestException(
                    "Yetarli zaxira yo'q: mavjud " + srcStock + ", so'ralgan " + units);
        }
        String productName    = (String) src[2];
        String productBarcode = (String) src[3];

        // 2. Find or create the destination row.
        Long destId = findDestProduct(r.toShopId(), productBarcode);
        if (destId == null) {
            destId = cloneProductInto(r.sourceProductId(), r.toShopId(), src);
        }

        // 3. Apply the stock changes atomically (integer units).
        adjustStock(r.sourceProductId(), -units);
        adjustStock(destId, units);

        // 4. Audit row.
        ShopTransfer row = new ShopTransfer();
        row.setAccountId(accountId);
        row.setFromShopId(r.fromShopId());
        row.setToShopId(r.toShopId());
        row.setSourceProductId(r.sourceProductId());
        row.setDestProductId(destId);
        row.setProductName(productName);
        row.setProductBarcode(productBarcode);
        row.setQty(qty);
        row.setNote(blankToNull(r.note()));
        row.setCreatedBy(blankToNull(createdBy));
        ShopTransfer saved = transfers.save(row);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> recent(Long accountId) {
        return transfers.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ------------------------------------------------------------ helpers

    private void requireOwnedShop(Long accountId, Long shopId) {
        shops.findById(shopId).filter(s -> accountId.equals(s.getAccountId()))
                .orElseThrow(() -> NotFoundException.of("Do'kon", shopId));
    }

    /**
     * Native lookup of the source product. Returns id, shop_id, name,
     * barcode, sale_price, quantity so the caller has everything it
     * needs to clone to the destination.
     */
    private Object[] loadProduct(Long id) {
        Query q = em.createNativeQuery(
                "SELECT id, shop_id, name, barcode, sale_price, quantity "
                + "FROM products WHERE id = ?");
        q.setParameter(1, id);
        List<?> rows = q.getResultList();
        if (rows.isEmpty()) throw NotFoundException.of("Mahsulot", id);
        return (Object[]) rows.get(0);
    }

    private Long findDestProduct(Long shopId, String barcode) {
        if (barcode == null || barcode.isBlank()) return null;
        Query q = em.createNativeQuery(
                "SELECT id FROM products WHERE shop_id = ? AND barcode = ?");
        q.setParameter(1, shopId);
        q.setParameter(2, barcode);
        List<?> rows = q.getResultList();
        return rows.isEmpty() ? null : ((Number) rows.get(0)).longValue();
    }

    /**
     * Copy the source product into the destination shop with zero stock
     * (the transfer itself will bump stock right after). Returns the new
     * product id. Uses a native INSERT-SELECT so we don't have to enumerate
     * every column the source has — schema additions stay portable.
     */
    private Long cloneProductInto(Long sourceProductId, Long toShopId, Object[] src) {
        // Pull a wider snapshot so we copy category, unit, tax fields etc.
        // NB: the national catalogue column is `mxik_code` (see V9 migration /
        // Product#mxikCode); the old `ikpu_code` name never existed in the schema
        // and made every transfer-to-new-product crash with a missing-column error.
        Query snapshot = em.createNativeQuery(
                "SELECT name, barcode, sale_price, purchase_price, category_id, "
                + "       unit, mxik_code, vat_rate "
                + "FROM products WHERE id = ?");
        snapshot.setParameter(1, sourceProductId);
        Object[] s = (Object[]) snapshot.getSingleResult();

        Query ins = em.createNativeQuery(
                "INSERT INTO products (shop_id, name, barcode, sale_price, purchase_price, "
                + "  category_id, unit, mxik_code, vat_rate, quantity, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP)");
        ins.setParameter(1, toShopId);
        for (int i = 0; i < s.length; i++) {
            ins.setParameter(i + 2, s[i]);
        }
        ins.executeUpdate();

        Query idLookup = em.createNativeQuery(
                "SELECT id FROM products WHERE shop_id = ? AND name = ? "
                + "ORDER BY id DESC LIMIT 1");
        idLookup.setParameter(1, toShopId);
        idLookup.setParameter(2, s[0]);
        return ((Number) idLookup.getSingleResult()).longValue();
    }

    private void adjustStock(Long productId, long delta) {
        Query q = em.createNativeQuery(
                "UPDATE products SET quantity = quantity + ? WHERE id = ?");
        q.setParameter(1, delta);
        q.setParameter(2, productId);
        q.executeUpdate();
    }

    private TransferResponse toResponse(ShopTransfer t) {
        // Friendly shop names for the list view; fall back to "?" so a
        // deleted shop doesn't crash the rendering path.
        Map<Long, String> names = shops.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        s -> s.getId(), s -> s.getName(), (a, b) -> a));
        return new TransferResponse(
                t.getId(),
                t.getFromShopId(), names.getOrDefault(t.getFromShopId(), "?"),
                t.getToShopId(),   names.getOrDefault(t.getToShopId(),   "?"),
                t.getSourceProductId(), t.getDestProductId(),
                t.getProductName(), t.getProductBarcode(),
                t.getQty(), t.getNote(), t.getCreatedBy(), t.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
