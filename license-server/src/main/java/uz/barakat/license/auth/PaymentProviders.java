package uz.barakat.license.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Registry of the available {@link PaymentProvider}s, keyed by
 * {@link PaymentProvider#name()} (upper-cased). Spring injects every
 * provider bean, so adding a new PSP is just dropping in a new
 * {@code @Component} implementation — no change here.
 */
@Component
public class PaymentProviders {

    private final Map<String, PaymentProvider> byName;

    public PaymentProviders(List<PaymentProvider> providers) {
        this.byName = providers.stream().collect(
                Collectors.toMap(p -> p.name().toUpperCase(), Function.identity()));
    }

    public Optional<PaymentProvider> find(String name) {
        return (name == null || name.isBlank())
                ? Optional.empty()
                : Optional.ofNullable(byName.get(name.trim().toUpperCase()));
    }
}
