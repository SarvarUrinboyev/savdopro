package uz.barakat.mobile.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.barakat.mobile.domain.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySourceProductId(Long sourceProductId);

    List<Product> findBySourceProductIdIsNotNull();

    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
          AND (:categoryId IS NULL OR p.category.id = :categoryId)
          AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<Product> search(@Param("categoryId") Long categoryId,
                         @Param("q") String q,
                         Pageable pageable);

    List<Product> findTop10ByActiveTrueAndPopularTrueOrderByIdDesc();

    long countByCategoryIdAndActiveTrue(Long categoryId);
}
