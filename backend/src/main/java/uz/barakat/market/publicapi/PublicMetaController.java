package uz.barakat.market.publicapi;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.auth.ApiKeyAuthFilter;

/**
 * Open API — meta endpoints for integrators: an unauthenticated-shape {@code ping}
 * health check and {@code me} (which shop + scopes the presented key resolves to),
 * handy for verifying credentials. Requires a valid API key (any scope).
 */
@RestController
@RequestMapping("/api/v1")
public class PublicMetaController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "service", "savdopro", "ts", Instant.now().toString());
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req, Authentication auth) {
        List<String> scopes = auth == null ? List.of()
                : auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("SCOPE_"))
                .map(a -> a.substring("SCOPE_".length()))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("apiKeyId", req.getAttribute(ApiKeyAuthFilter.ATTR_API_KEY_ID));
        out.put("shopId", req.getAttribute(ApiKeyAuthFilter.ATTR_API_KEY_SHOP_ID));
        out.put("scopes", scopes);
        return out;
    }
}
