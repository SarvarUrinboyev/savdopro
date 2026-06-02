package uz.barakat.market.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thin Telegram Bot API client for the customer self-service bot:
 * long-poll for updates, send messages (with optional keyboards) and
 * acknowledge callback queries. Every failure is logged, never thrown.
 */
@Component
public class TelegramBotApi {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotApi.class);
    private static final String API = "https://api.telegram.org/bot";

    private final CustomerBotProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public TelegramBotApi(CustomerBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Long-polls for updates. Returns the {@code result} array node, or
     * {@code null} on any failure.
     */
    public JsonNode getUpdates(long offset, int timeoutSeconds) {
        try {
            String url = API + properties.token() + "/getUpdates?offset=" + offset
                    + "&timeout=" + timeoutSeconds;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds + 15L))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Telegram getUpdates failed: HTTP {}", response.statusCode());
                return null;
            }
            return objectMapper.readTree(response.body()).path("result");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            log.warn("Telegram getUpdates error: {}", ex.toString());
            return null;
        }
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    /** Sends a message; {@code replyMarkup} may be null or a keyboard structure. */
    public void sendMessage(long chatId, String text, Object replyMarkup) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("disable_web_page_preview", true);
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }
        post("sendMessage", body);
    }

    public void answerCallback(String callbackQueryId) {
        post("answerCallbackQuery", Map.of("callback_query_id", callbackQueryId));
    }

    private void post(String method, Map<String, Object> body) {
        try {
            String payload = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API + properties.token() + "/" + method))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Telegram {} failed: HTTP {} - {}",
                        method, response.statusCode(), response.body());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Telegram {} error: {}", method, ex.toString());
        }
    }

    /** Uploads a document (e.g. a PDF report) to a chat via multipart/form-data. */
    public void sendDocument(long chatId, byte[] bytes, String fileName, String caption) {
        String boundary = "----savdopro-cust-" + System.nanoTime();
        try {
            var baos = new java.io.ByteArrayOutputStream();
            writePart(baos, boundary, "chat_id", String.valueOf(chatId));
            if (caption != null && !caption.isBlank()) {
                writePart(baos, boundary, "caption", caption);
            }
            writeFilePart(baos, boundary, "document", fileName, "application/pdf", bytes);
            baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API + properties.token() + "/sendDocument"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Telegram sendDocument failed: HTTP {} - {}",
                        resp.statusCode(), resp.body());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Telegram sendDocument error: {}", ex.toString());
        }
    }

    private static void writePart(java.io.ByteArrayOutputStream out, String boundary,
                                  String name, String value) throws java.io.IOException {
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(java.io.ByteArrayOutputStream out, String boundary,
                                      String name, String fileName, String contentType,
                                      byte[] bytes) throws java.io.IOException {
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; "
                + "filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
