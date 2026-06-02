package uz.barakat.market.fiscal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Registers a sale with the tax authority through an OFD and returns the
 * fiscal sign + QR for the receipt. Config-gated: until a real provider and
 * credentials are set ({@link FiscalProperties#isUsable()}) this is a logged
 * no-op and {@link #fiscalize} returns {@link Optional#empty()}, so the POS
 * flow is unchanged.
 *
 * <h2>Integration point</h2>
 * The provider-specific HTTP call lives in {@link #callProvider}. Each OFD
 * (soliq.uz / Didox / iiko-fiscal / ...) has its own request format and
 * signing; implement the matching branch there using the configured
 * {@code apiUrl}, {@code apiKey} and {@code terminalId}. The rest of the
 * pipeline (when to call, how the result is consumed) is already wired here,
 * so going live is just filling in that one method + setting the creds.
 */
@Service
public class FiscalizationService {

    private static final Logger log = LoggerFactory.getLogger(FiscalizationService.class);

    private final FiscalProperties properties;

    public FiscalizationService(FiscalProperties properties) {
        this.properties = properties;
    }

    /** Minimal sale snapshot the OFD needs. Amounts are in the base unit. */
    public record FiscalSale(
            Long saleId,
            BigDecimal total,
            String paymentMethod,
            LocalDateTime soldAt) { }

    /** What the OFD returns for the receipt. */
    public record FiscalResult(
            String fiscalSign,
            String qrUrl,
            String terminalId,
            LocalDateTime registeredAt) { }

    public boolean isUsable() {
        return properties.isUsable();
    }

    /**
     * Registers a sale with the OFD. Best-effort: returns empty (and never
     * throws) when fiscalization is off or the provider call fails, so a
     * fiscalization outage can never block a sale.
     */
    public Optional<FiscalResult> fiscalize(FiscalSale sale) {
        if (!properties.isUsable()) {
            log.debug("Fiscalization disabled — sale {} not registered with OFD", sale.saleId());
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(callProvider(sale));
        } catch (RuntimeException ex) {
            log.warn("Fiscalization of sale {} failed: {}", sale.saleId(), ex.toString());
            return Optional.empty();
        }
    }

    /**
     * Provider-specific OFD registration. NOT yet implemented for a concrete
     * operator — wire the configured provider's REST contract here. Until then
     * an enabled-but-unimplemented provider logs a clear warning and returns
     * null (treated as "not fiscalized") rather than pretending to succeed.
     */
    private FiscalResult callProvider(FiscalSale sale) {
        String provider = properties.provider() == null ? "?" : properties.provider();
        // TODO: implement per provider, e.g.:
        //   case "didox"  -> didoxClient.register(sale, properties);
        //   case "soliq"  -> soliqClient.register(sale, properties);
        log.warn("Fiscal provider '{}' is configured but its adapter is not "
                + "implemented yet — sale {} not fiscalized. See FiscalizationService.callProvider.",
                provider, sale.saleId());
        return null;
    }
}
