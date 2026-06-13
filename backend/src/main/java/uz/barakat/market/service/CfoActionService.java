package uz.barakat.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import uz.barakat.market.domain.Product;
import uz.barakat.market.dto.CustomerResponse;
import uz.barakat.market.repository.ProductRepository;

/**
 * Turns the AI CFO's free-text {@code ACTION ...} lines into validated,
 * id-resolved action suggestions the UI renders as buttons.
 *
 * <p>The model is told it may append lines like
 * {@code ACTION ORDER | <product> | <qty>} to its answer; we parse them here,
 * resolve the named product / customer against THIS shop's data, attach the
 * real ids + current figures, and drop anything that doesn't resolve. The AI
 * itself never mutates data — these are suggestions; execution happens later
 * through the normal, permission-gated write endpoints when the user clicks.
 *
 * <p>Read-only and tenant-scoped exactly like {@link AiToolService} (same
 * request thread, same repository calls), so a shop only ever sees its own rows.
 */
@Service
public class CfoActionService {

    /** A button the UI offers after an answer. {@code params} carries resolved ids. */
    public record CfoAction(String type, String label, String detail, Map<String, Object> params) { }

    /** The answer with ACTION lines stripped out, plus the parsed actions. */
    public record Extracted(String text, List<CfoAction> actions) { }

    /** {@code ACTION <TYPE> | <name> | <number>} at the start of a line (number optional). */
    private static final Pattern ACTION_RE = Pattern.compile(
            "(?im)^\\s*ACTION\\s+([A-Za-z_]+)\\s*\\|\\s*([^|\\r\\n]*?)\\s*(?:\\|\\s*([^|\\r\\n]*?)\\s*)?$");
    private static final Pattern ACTION_LINE = Pattern.compile("(?im)^\\s*ACTION\\s+.*$");

    private final ProductRepository products;
    private final CustomerService customers;

    public CfoActionService(ProductRepository products, CustomerService customers) {
        this.products = products;
        this.customers = customers;
    }

    public Extracted extract(String answer) {
        if (answer == null || answer.isBlank()) {
            return new Extracted(answer == null ? "" : answer, List.of());
        }
        List<CfoAction> actions = new ArrayList<>();
        Matcher m = ACTION_RE.matcher(answer);
        while (m.find()) {
            CfoAction a = build(
                    m.group(1).trim().toUpperCase(Locale.ROOT),
                    m.group(2) == null ? "" : m.group(2).trim(),
                    m.group(3) == null ? "" : m.group(3).trim());
            if (a != null) {
                actions.add(a);
            }
        }
        String cleaned = ACTION_LINE.matcher(answer).replaceAll("")
                .replaceAll("\\n{3,}", "\n\n").trim();
        return new Extracted(cleaned, dedup(actions));
    }

    private CfoAction build(String type, String name, String num) {
        if (name.isBlank()) {
            return null;
        }
        return switch (type) {
            case "ORDER" -> orderAction(name, num);
            case "DISCOUNT" -> discountAction(name, num);
            case "PRICE" -> priceAction(name, num);
            case "NOTIFY" -> notifyAction(name);
            default -> null;
        };
    }

    private CfoAction orderAction(String name, String num) {
        Product p = findProduct(name);
        if (p == null) {
            return null;
        }
        int qty = parseInt(num, 0);
        BigDecimal cost = nz(p.getPurchasePrice());
        BigDecimal est = cost.multiply(BigDecimal.valueOf(Math.max(qty, 0))).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", p.getId());
        params.put("productName", p.getName());
        params.put("qty", qty);
        params.put("unitCostUsd", cost);
        params.put("estAmountUsd", est);
        String detail = qty > 0 ? qty + " dona (~$" + money(est) + ")" : "buyurtma yaratish";
        return new CfoAction("ORDER", "📦 Buyurtma: " + p.getName(), detail, params);
    }

    private CfoAction discountAction(String name, String num) {
        Product p = findProduct(name);
        if (p == null) {
            return null;
        }
        int pct = clampPct(parseInt(num, 10));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", p.getId());
        params.put("productName", p.getName());
        params.put("percent", pct);
        params.put("currentPriceUsd", nz(p.getSalePrice()));
        return new CfoAction("DISCOUNT", "🏷️ Chegirma −" + pct + "%: " + p.getName(),
                "hozirgi narx $" + money(p.getSalePrice()), params);
    }

    private CfoAction priceAction(String name, String num) {
        Product p = findProduct(name);
        if (p == null) {
            return null;
        }
        int pct = clampPct(parseInt(num, 5));
        BigDecimal cur = nz(p.getSalePrice());
        BigDecimal next = cur.multiply(BigDecimal.valueOf(100 + pct))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("productId", p.getId());
        params.put("productName", p.getName());
        params.put("percent", pct);
        params.put("currentPriceUsd", cur);
        params.put("newPriceUsd", next);
        return new CfoAction("PRICE", "⬆️ Narx +" + pct + "%: " + p.getName(),
                "$" + money(cur) + " → $" + money(next), params);
    }

    private CfoAction notifyAction(String name) {
        CustomerResponse c = customers.list().stream()
                .filter(x -> x.name() != null
                        && x.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                .findFirst().orElse(null);
        if (c == null) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("customerId", c.id());
        params.put("customerName", c.name());
        params.put("phone", c.phone());
        params.put("balanceUsd", nz(c.balance()));
        String detail = c.balance() != null && c.balance().signum() > 0
                ? "$" + money(c.balance()) + " qarz" : "xabar yuborish";
        return new CfoAction("NOTIFY", "✉️ Eslatma: " + c.name(), detail, params);
    }

    // --------------------------------------------------------------- helpers

    private Product findProduct(String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        return products.findAllByOrderByNameAsc().stream()
                .filter(p -> p.getName() != null
                        && p.getName().toLowerCase(Locale.ROOT).contains(needle))
                .findFirst().orElse(null);
    }

    /** Drop duplicate suggestions (same type + same resolved entity). */
    private static List<CfoAction> dedup(List<CfoAction> in) {
        Map<String, CfoAction> seen = new LinkedHashMap<>();
        for (CfoAction a : in) {
            Object id = a.params().getOrDefault("productId", a.params().get("customerId"));
            seen.putIfAbsent(a.type() + ":" + id, a);
        }
        return List.copyOf(seen.values());
    }

    private static int parseInt(String s, int dflt) {
        if (s == null) {
            return dflt;
        }
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return dflt;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static int clampPct(int pct) {
        return Math.min(Math.max(pct, 1), 90);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String money(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
