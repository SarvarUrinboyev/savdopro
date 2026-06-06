package uz.barakat.market.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wiring for the global barcode lookup (the warehouse scan fallback): a
 * short-timeout {@link RestClient} for the external product databases and a
 * 1-hour in-memory cache so repeated scans of the same code don't re-hit them.
 *
 * <p>This is currently the app's only {@code @Cacheable}, so {@code @EnableCaching}
 * lives here next to the cache it serves.
 */
@Configuration
@EnableCaching
public class BarcodeLookupConfig {

    /**
     * HTTP client dedicated to the barcode lookup, with a hard connect + read
     * timeout (default 3s) so a slow external API can never stall a scan.
     */
    @Bean
    RestClient barcodeLookupRestClient(
            @Value("${barakat.barcode.timeout-ms:3000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * In-memory cache for {@code BarcodeLookupService.lookup}. Entries expire an
     * hour after they're written and the cache is bounded so it can't grow
     * without limit.
     */
    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("barcodeLookup");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10_000));
        return manager;
    }
}
