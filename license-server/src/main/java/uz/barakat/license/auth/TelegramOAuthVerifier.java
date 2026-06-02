package uz.barakat.license.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.barakat.license.exception.BadRequestException;

/**
 * Verifies the payload Telegram's Login Widget posts to our server.
 *
 * <p>Algorithm (per <a href="https://core.telegram.org/widgets/login#checking-authorization">
 * Telegram docs</a>):
 * <ol>
 *   <li>Take every received field except {@code hash}.</li>
 *   <li>Sort the (key, value) pairs alphabetically by key.</li>
 *   <li>Join them as {@code key=value} lines separated by {@code \n}.
 *       This is the {@code data_check_string}.</li>
 *   <li>Compute {@code secret = SHA-256(bot_token)} as raw bytes.</li>
 *   <li>Compute {@code HMAC-SHA-256(secret, data_check_string)} and
 *       compare its lowercase hex to the {@code hash} field.</li>
 *   <li>Reject if {@code auth_date} is more than {@link #MAX_AUTH_AGE_MIN}
 *       minutes old — protects against replay of a captured payload.</li>
 * </ol>
 *
 * <p>The verifier is a pure utility: it never touches the database and
 * doesn't know about {@code AppUser}. Callers map the verified
 * {@code telegram_id} to a user themselves.
 */
@Component
public class TelegramOAuthVerifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramOAuthVerifier.class);

    /** Reject auth payloads older than this. Telegram recommends 24h max. */
    static final long MAX_AUTH_AGE_MIN = 60;   // tighter than the spec; replay is cheap so we cap.

    private final String botToken;

    public TelegramOAuthVerifier(
            @Value("${savdopro.license.telegram-oauth.bot-token:}") String botToken) {
        this.botToken = botToken;
    }

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank();
    }

    /**
     * The numeric bot id — the part before ':' in the bot token. The browser's
     * Telegram Login Widget needs it (as {@code bot_id}) to open the popup. The
     * bot id is public (it appears in the widget's iframe URL), so surfacing it
     * to the signup screen leaks nothing. Returns null when unconfigured.
     */
    public Long botId() {
        if (!isConfigured()) return null;
        int colon = botToken.indexOf(':');
        if (colon <= 0) return null;
        try {
            return Long.parseLong(botToken.substring(0, colon).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Validate the payload and return the verified Telegram user id.
     * Throws {@link BadRequestException} on any failure — the caller
     * surfaces a generic "Telegram orqali kirish bekor qilindi" message
     * so an attacker can't tell which step failed.
     */
    public long verifyAndExtractId(Map<String, String> fields) {
        if (!isConfigured()) {
            log.warn("Telegram OAuth attempted but bot token is not configured");
            throw new BadRequestException("Telegram orqali kirish hozir o'chirilgan");
        }
        if (fields == null) {
            throw new BadRequestException("Telegram payload bo'sh");
        }
        String receivedHash = fields.get("hash");
        if (receivedHash == null || receivedHash.isBlank()) {
            throw new BadRequestException("Telegram payload imzosiz");
        }
        String idStr = fields.get("id");
        if (idStr == null || idStr.isBlank()) {
            throw new BadRequestException("Telegram payload id'siz");
        }
        long telegramId;
        try {
            telegramId = Long.parseLong(idStr.trim());
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Telegram id raqam bo'lishi kerak");
        }
        // Build the data_check_string from sorted non-hash fields.
        TreeMap<String, String> sorted = new TreeMap<>(fields);
        sorted.remove("hash");
        StringBuilder data = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) data.append('\n');
            data.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        String expectedHash = hmacHex(botToken, data.toString());
        if (!constantTimeEquals(expectedHash, receivedHash.toLowerCase(java.util.Locale.ROOT))) {
            throw new BadRequestException("Telegram imzosi to'g'ri kelmadi");
        }
        // Replay window.
        String authDateStr = fields.get("auth_date");
        if (authDateStr != null && !authDateStr.isBlank()) {
            try {
                long authEpoch = Long.parseLong(authDateStr.trim());
                Duration age = Duration.between(
                        Instant.ofEpochSecond(authEpoch), Instant.now());
                if (age.toMinutes() > MAX_AUTH_AGE_MIN) {
                    throw new BadRequestException("Telegram payload muddati o'tgan");
                }
            } catch (NumberFormatException ex) {
                throw new BadRequestException("auth_date raqam bo'lishi kerak");
            }
        }
        return telegramId;
    }

    /** Visible for testing — compute the expected hex hash for a payload. */
    static String hmacHex(String botToken, String dataCheckString) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] secretKey = sha.digest(botToken.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] mac256 = mac.doFinal(
                    dataCheckString.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(mac256.length * 2);
            for (byte b : mac256) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM missing SHA-256 / HmacSHA256", ex);
        } catch (java.security.InvalidKeyException ex) {
            throw new IllegalStateException("Cannot init HmacSHA256", ex);
        }
    }

    /** Constant-time hex comparison — no early-exit on first byte mismatch. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
