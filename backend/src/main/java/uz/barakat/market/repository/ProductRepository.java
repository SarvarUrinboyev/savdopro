package uz.barakat.market.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.barakat.market.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByNameAsc();

    Optional<Product> findFirstByBarcode(String barcode);

    // ---- duplicate guards (tenant-scoped via the shop @Filter) ----
    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Returns every product whose current quantity is at or below its
     * low-stock threshold. Products with {@code lowStockThreshold = 0} are
     * excluded — zero means "no threshold set".
     */
    @Query("SELECT p FROM Product p WHERE p.lowStockThreshold > 0 AND p.quantity <= p.lowStockThreshold")
    List<Product> findLowStockProducts();
}
