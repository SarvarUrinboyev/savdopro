package uz.barakat.mobile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Barakat Market mobile backend — mijozlar uchun xarid/yetkazib berish REST API.
 */
@SpringBootApplication
@EnableScheduling   // CatalogSyncService POS katalogini muntazam tortadi
public class MobileApplication {
    public static void main(String[] args) {
        SpringApplication.run(MobileApplication.class, args);
    }
}
