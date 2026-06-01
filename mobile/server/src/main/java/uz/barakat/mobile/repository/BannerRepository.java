package uz.barakat.mobile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.mobile.domain.Banner;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findAllByActiveTrueOrderBySortOrderAsc();
}
