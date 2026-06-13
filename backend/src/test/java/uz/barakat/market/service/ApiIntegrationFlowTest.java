package uz.barakat.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import uz.barakat.market.auth.ApiKeyService;
import uz.barakat.market.auth.ApiKeyService.IssuedKey;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.dto.pub.PublicDtos.WebhookEnvelope;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.ShopRepository;
import uz.barakat.market.service.webhook.WebhookDispatcher;
import uz.barakat.market.service.webhook.WebhookSender;
import uz.barakat.market.service.webhook.WebhookSubscriptionService;

/**
 * End-to-end Open-API + webhook checks: API-key auth + scope enforcement +
 * cross-shop tenant isolation through the real filter chain, and the
 * enqueue → dispatch → DELIVERED webhook path (HTTP send mocked).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ShopRepository shops;
    @Autowired ProductRepository products;
    @Autowired ApiKeyService apiKeys;
    @Autowired WebhookSubscriptionService webhooks;
    @Autowired WebhookDispatcher dispatcher;

    @MockBean WebhookSender sender;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void apiKeyScopesAndTenantIsolationAreEnforced() throws Exception {
        Shop shopA = shops.findAll().stream().findFirst().orElseThrow();
        Shop shopB = new Shop();
        shopB.setAccountId(shopA.getAccountId());
        shopB.setName("Isolation test shop B");
        shopB.setMain(false);
        shopB = shops.save(shopB);

        TenantContext.setShopId(shopB.getId());
        products.save(product("B-ONLY-PRODUCT"));

        TenantContext.setShopId(shopA.getId());
        products.save(product("A-ONLY-PRODUCT"));
        IssuedKey key = apiKeys.create("test integration", List.of("catalog:read"), null);
        TenantContext.clear();

        String bearer = "Bearer " + key.secret();

        // Sees only its own shop's catalogue.
        mvc.perform(get("/api/v1/products").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A-ONLY-PRODUCT")))
                .andExpect(content().string(not(containsString("B-ONLY-PRODUCT"))));

        // No credential → 401.
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());

        // Key lacks sales:read → 403.
        mvc.perform(get("/api/v1/sales").header("Authorization", bearer))
                .andExpect(status().isForbidden());

        // Revoked key → 401.
        apiKeys.revoke(key.id());
        mvc.perform(get("/api/v1/products").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookEnqueueAndDispatchMarksDelivered() {
        when(sender.send(any(), any(), any(), any(), any()))
                .thenReturn(new WebhookSender.Result(true, 200, null));

        Shop shopA = shops.findAll().stream().findFirst().orElseThrow();
        TenantContext.setShopId(shopA.getId());
        webhooks.create("https://8.8.8.8/hook", List.of("sale.created"));
        webhooks.enqueue("sale.created", new WebhookEnvelope(
                "sale.created", LocalDateTime.now(), shopA.getId(), Map.of("k", "v")));
        TenantContext.clear();

        dispatcher.dispatch();

        TenantContext.setShopId(shopA.getId());
        assertThat(webhooks.recentDeliveries(20))
                .anyMatch(d -> "sale.created".equals(d.getEventType())
                        && "DELIVERED".equals(d.getStatus()));
    }

    private static Product product(String name) {
        Product p = new Product();
        p.setName(name);
        p.setSalePrice(new BigDecimal("10"));
        p.setPurchasePrice(new BigDecimal("6"));
        p.setQuantity(5);
        return p;
    }
}
