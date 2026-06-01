package uz.barakat.mobile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.barakat.mobile.domain.OtpCode;

import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findTopByPhoneAndUsedFalseOrderByIdDesc(String phone);
}
