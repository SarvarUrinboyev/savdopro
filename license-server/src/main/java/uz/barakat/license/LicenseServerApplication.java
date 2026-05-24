package uz.barakat.license;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SavdoPRO License Server entry point.
 *
 * <p>The server owns all account / user / subscription data centrally.
 * Every installed SavdoPRO desktop client calls this server's
 * {@code /api/auth/login} endpoint to authenticate; the desktop's
 * local backend never stores credentials. Sarvar (the platform owner)
 * uses the admin web panel served at {@code /admin} to create accounts
 * and manage subscriptions from one place.
 */
@SpringBootApplication
@EnableScheduling   // RefreshTokenService.purgeExpired() runs nightly
public class LicenseServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LicenseServerApplication.class, args);
    }
}
