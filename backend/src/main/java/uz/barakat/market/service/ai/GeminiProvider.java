package uz.barakat.market.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini REST provider. Different request shape from OpenAI:
 * Gemini bundles system instruction + user turn under {@code contents}
 * and uses an URL-embedded API key instead of a bearer header.
 *
 * <p>Endpoint: {@code https://generativelanguage.googleapis.com/v1beta/
 * models/{model}:generateContent?key=...}
 */
public final class GeminiProvider implements AiProvider {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public GeminiProvider(String apiKey, String model) {
        this(apiKey, model, 0.2, 800);
    }

    public GeminiProvider(String apiKey, String model, double temperature, int maxTokens) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override public String name() { return "gemini/" + model; }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }

    @Override
    public String complete(String system, String user) throws Exception {
        Map<String, Object> body = new HashMap<>();
        // Gemini wraps the conversation in contents[].parts[]
        body.put("system_instruction",
                Map.of("parts", List.of(Map.of("text", system))));
        body.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", user)))
        ));
        body.put("generationConfig", Map.of(
                "temperature", temperature,
                "maxOutputTokens", maxTokens
        ));

        String url = BASE + model + ":generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException(name() + ": HTTP " + resp.statusCode()
                    + " — " + truncate(resp.body(), 200));
        }
        var node = JSON.readTree(resp.body());
        // candidates[0].content.parts[0].text
        var first = node.path("candidates").path(0).path("content").path("parts").path(0);
        String text = first.path("text").asText("");
        if (text.isBlank()) {
            // PROMPT_BLOCKED or RECITATION shows up here
            var reason = node.path("candidates").path(0).path("finishReason").asText("");
            throw new RuntimeException(name() + ": empty response (finishReason=" + reason + ")");
        }
        return text.strip();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
