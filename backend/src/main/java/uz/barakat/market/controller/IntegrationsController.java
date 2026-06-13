package uz.barakat.market.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.auth.ApiKeyService;
import uz.barakat.market.auth.ApiKeyService.IssuedKey;
import uz.barakat.market.domain.ApiKey;
import uz.barakat.market.domain.WebhookDelivery;
import uz.barakat.market.domain.WebhookSubscription;
import uz.barakat.market.service.webhook.WebhookSubscriptionService;
import uz.barakat.market.service.webhook.WebhookSubscriptionService.CreatedSubscription;

/**
 * Owner-facing management of external integrations: API keys + webhook
 * subscriptions for the current shop. Gated to SHOPS:WRITE in SecurityConfig
 * (owner action). Secrets — the API-key plaintext and the webhook signing
 * secret — are returned ONCE at creation and never again; list views expose
 * only the key prefix (never the hash or secret).
 */
@RestController
@RequestMapping("/api/integrations")
public class IntegrationsController {

    private final ApiKeyService apiKeys;
    private final WebhookSubscriptionService webhooks;

    public IntegrationsController(ApiKeyService apiKeys, WebhookSubscriptionService webhooks) {
        this.apiKeys = apiKeys;
        this.webhooks = webhooks;
    }

    // ---- request/response shapes ----
    public record CreateKeyRequest(@NotBlank String name, List<String> scopes, LocalDateTime expiresAt) { }

    public record ApiKeyResponse(Long id, String name, List<String> scopes, String prefix,
                                 boolean active, LocalDateTime expiresAt, LocalDateTime lastUsedAt,
                                 LocalDateTime createdAt) { }

    public record CreateWebhookRequest(@NotBlank String url, List<String> events) { }

    public record WebhookResponse(Long id, String url, List<String> events, boolean active,
                                  LocalDateTime createdAt) { }

    public record DeliveryResponse(Long id, Long subscriptionId, String eventType, String status,
                                   int attempts, String lastError, LocalDateTime createdAt,
                                   LocalDateTime deliveredAt) { }

    public record IntegrationMeta(List<String> scopes, List<String> eventTypes) { }

    // ---- meta (for the UI to render choices) ----
    @GetMapping("/meta")
    public IntegrationMeta meta() {
        return new IntegrationMeta(
                ApiKeyService.VALID_SCOPES.stream().sorted().toList(),
                WebhookSubscriptionService.EVENT_TYPES.stream().sorted().toList());
    }

    // ---- API keys ----
    @GetMapping("/api-keys")
    public List<ApiKeyResponse> listKeys() {
        return apiKeys.list().stream().map(this::toKeyResponse).toList();
    }

    @PostMapping("/api-keys")
    public IssuedKey createKey(@Valid @RequestBody CreateKeyRequest req) {
        return apiKeys.create(req.name(), req.scopes(), req.expiresAt());
    }

    @DeleteMapping("/api-keys/{id}")
    public void revokeKey(@PathVariable Long id) {
        apiKeys.revoke(id);
    }

    // ---- webhooks ----
    @GetMapping("/webhooks")
    public List<WebhookResponse> listWebhooks() {
        return webhooks.list().stream().map(this::toWebhookResponse).toList();
    }

    @PostMapping("/webhooks")
    public CreatedSubscription createWebhook(@Valid @RequestBody CreateWebhookRequest req) {
        return webhooks.create(req.url(), req.events());
    }

    @DeleteMapping("/webhooks/{id}")
    public void deleteWebhook(@PathVariable Long id) {
        webhooks.delete(id);
    }

    @PostMapping("/webhooks/{id}/test")
    public void testWebhook(@PathVariable Long id) {
        webhooks.test(id);
    }

    @GetMapping("/webhooks/deliveries")
    public List<DeliveryResponse> recentDeliveries() {
        return webhooks.recentDeliveries(100).stream().map(this::toDeliveryResponse).toList();
    }

    // ---- mappers (drop secrets) ----
    private ApiKeyResponse toKeyResponse(ApiKey k) {
        return new ApiKeyResponse(k.getId(), k.getName(), apiKeys.scopesOf(k), k.getKeyPrefix(),
                k.isActive(), k.getExpiresAt(), k.getLastUsedAt(), k.getCreatedAt());
    }

    private WebhookResponse toWebhookResponse(WebhookSubscription s) {
        return new WebhookResponse(s.getId(), s.getUrl(), webhooks.eventsOf(s), s.isActive(),
                s.getCreatedAt());
    }

    private DeliveryResponse toDeliveryResponse(WebhookDelivery d) {
        return new DeliveryResponse(d.getId(), d.getSubscriptionId(), d.getEventType(), d.getStatus(),
                d.getAttempts(), d.getLastError(), d.getCreatedAt(), d.getDeliveredAt());
    }
}
