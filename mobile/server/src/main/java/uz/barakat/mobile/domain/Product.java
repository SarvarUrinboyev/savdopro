package uz.barakat.mobile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    /** Joriy sotuv narxi (so'm, butun). */
    @Column(nullable = false)
    private long price;

    /** Chegirmadan oldingi narx (null bo'lsa chegirma yo'q). */
    private Long oldPrice;

    @Column(length = 40)
    private String unit = "dona";

    @Column(length = 300)
    private String imageUrl;

    @Column(nullable = false)
    private int stockQty = 100;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean popular = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * POS backend'dagi mahsulot IDsi (CatalogSyncService upsert kaliti).
     * null = qo'lda kiritilgan mahsulot — sinxron unga hech qachon tegmaydi.
     */
    @Column(name = "source_product_id", unique = true)
    private Long sourceProductId;

    /** Shtrix-kod (POS'dan sinxronlanadi; qidiruv/moslashtirish uchun). */
    @Column(length = 64)
    private String barcode;

    public boolean isInStock() { return active && stockQty > 0; }

    public Integer getDiscountPercent() {
        if (oldPrice == null || oldPrice <= 0 || oldPrice <= price) return null;
        return (int) Math.round((oldPrice - price) * 100.0 / oldPrice);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
    public Long getOldPrice() { return oldPrice; }
    public void setOldPrice(Long oldPrice) { this.oldPrice = oldPrice; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public int getStockQty() { return stockQty; }
    public void setStockQty(int stockQty) { this.stockQty = stockQty; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isPopular() { return popular; }
    public void setPopular(boolean popular) { this.popular = popular; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Long getSourceProductId() { return sourceProductId; }
    public void setSourceProductId(Long sourceProductId) { this.sourceProductId = sourceProductId; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}
