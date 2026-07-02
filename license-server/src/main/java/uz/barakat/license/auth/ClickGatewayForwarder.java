package uz.barakat.license.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Transparent relay for Click SHOP-API callbacks that belong to ANOTHER
 * project sharing the same Click service (one merchant, one service_id, one
 * secret — but two backends). SavdoPRO subscription payments use a numeric
 * {@code merchant_trans_id} (the Payment id); the co-tenant (TezGo taxi) uses
 * a 20-char hex id. {@link ClickController} routes numeric ids locally and
 * hands everything else here to be forwarded byte-faithfully to the other
 * backend, whose JSON reply is returned to Click verbatim.
 *
 * <p>The forward preserves the exact field set + values, so the co-tenant's
 * own MD5 sign check (same shared secret) passes unchanged — this box never
 * inspects or re-signs the payload. Disabled (returns empty) when
 * {@code CLICK_FORWARD_BASE_URL} is blank, so a single-tenant install is
 * unaffected.
 */
@Component
public class ClickGatewayForwarder {

    private static final Logger log = LoggerFactory.getLogger(ClickGatewayForwarder.class);

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6)).build();

    public ClickGatewayForwarder(
            @Value("${click.gateway.forward-base-url:${CLICK_FORWARD_BASE_URL:}}") String baseUrl) {
        // Trailing slash trimmed so we can append /prepare, /complete.
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "").trim();
    }

    public boolean isEnabled() {
        return !baseUrl.isBlank();
    }

    /**
     * POST the form params to {@code baseUrl + "/" + leg} and return the raw
     * JSON body. Returns null on transport failure or when disabled — the
     * caller then answers Click with a retryable error so the co-tenant's
     * payment is re-delivered rather than falsely finalised.
     */
    public String forward(String leg, Map<String, String> params) {
        if (!isEnabled()) {
            log.warn("Click forward requested but CLICK_FORWARD_BASE_URL is unset — dropping {}", leg);
            return null;
        }
        String body = formEncode(params);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + leg))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Click forward {} -> HTTP {} from co-tenant", leg, resp.statusCode());
                return null;
            }
            return resp.body();
        } catch (Exception ex) {
            log.warn("Click forward {} failed: {}", leg, ex.toString());
            return null;
        }
    }

    private static String formEncode(Map<String, String> params) {
        StringJoiner sj = new StringJoiner("&");
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            sj.add(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sj.toString();
    }
}
