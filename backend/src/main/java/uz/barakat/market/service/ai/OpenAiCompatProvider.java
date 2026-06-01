package uz.barakat.market.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic OpenAI-compatible provider. Works for NVIDIA NIM, OpenRouter,
 * Together AI, vLLM, Ollama and any other endpoint that mirrors the
 * OpenAI {@code /v1/chat/completions} schema.
 *
 * <p>Constructor takes everything that varies between providers — name,
 * base URL ({@code .../v1}), API key and model id. One class for many
 * providers means we don't grow a class hierarchy for endpoints that
 * are 95% identical.
 */
public final class OpenAiCompatProvider implements AiProvider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final String name;
    private final String baseUrl;       // e.g. https://integrate.api.nvidia.com/v1
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    /** Optional extra headers — e.g. OpenRouter requires HTTP-Referer + X-Title. */
    private final Map<String, String> extraHeaders;

    public OpenAiCompatProvider(String name, String baseUrl, String apiKey, String model) {
        this(name, baseUrl, apiKey, model, 0.2, 800, Map.of());
    }

    public OpenAiCompatProvider(String name, String baseUrl, String apiKey, String model,
                                double temperature, int maxTokens,
                                Map<String, String> extraHeaders) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.extraHeaders = extraHeaders;
    }

    @Override public String name() { return name; }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }

    @Override
    public String complete(String system, String user) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        extraHeaders.forEach(rb::header);
        rb.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));

        HttpResponse<String> resp = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException(name + ": HTTP " + resp.statusCode()
                    + " — " + truncate(resp.body(), 200));
        }
        var node = JSON.readTree(resp.body());
        String text = node.path("choices").path(0).path("message").path("content").asText("");
        if (text.isBlank()) {
            throw new RuntimeException(name + ": empty response");
        }
        return text.strip();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
