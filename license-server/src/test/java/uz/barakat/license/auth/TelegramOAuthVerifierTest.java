package uz.barakat.license.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import uz.barakat.license.exception.BadRequestException;

/**
 * Unit tests for {@link TelegramOAuthVerifier}. We can't talk to the real
 * Telegram service in a unit test, so we mint the HMAC ourselves with the
 * exact same algorithm and feed the result back through the verifier —
 * matches → accepts, anything-tampered → rejects.
 */
class TelegramOAuthVerifierTest {

    private static final String BOT_TOKEN = "test-bot:token-123456";

    @Test
    void disabledWhenBotTokenIsBlank() {
        TelegramOAuthVerifier verifier = new TelegramOAuthVerifier("");
        assertEquals(false, verifier.isConfigured());
        assertThrows(BadRequestException.class,
                () -> verifier.verifyAndExtractId(Map.of("id", "1", "hash", "x")));
    }

    @Test
    void acceptsCorrectlySignedPayload() {
        TelegramOAuthVerifier verifier = new TelegramOAuthVerifier(BOT_TOKEN);
        Map<String, String> fields = freshPayload(123456789L, BOT_TOKEN);

        long id = verifier.verifyAndExtractId(fields);

        assertEquals(123456789L, id);
    }

    @Test
    void rejectsTamperedHash() {
        TelegramOAuthVerifier verifier = new TelegramOAuthVerifier(BOT_TOKEN);
        Map<String, String> fields = freshPayload(1L, BOT_TOKEN);
        // Flip one character of the hash.
        String tampered = fields.get("hash");
        char first = tampered.charAt(0);
        tampered = (first == '0' ? '1' : '0') + tampered.substring(1);
        fields.put("hash", tampered);

        assertThrows(BadRequestException.class,
                () -> verifier.verifyAndExtractId(fields));
    }

    @Test
    void rejectsTamperedField() {
        TelegramOAuthVerifier verifier = new TelegramOAuthVerifier(BOT_TOKEN);
        Map<String, String> fields = freshPayload(1L, BOT_TOKEN);
        // Modify a data field after the hash was computed → signature must fail.
        fields.put("first_name", "Mallory");

        assertThrows(BadRequestException.class,
                () -> verifier.verifyAndExtractId(fields));
    }

    @Test
    void rejectsStaleAuthDate() {
        TelegramOAuthVerifier verifier = new TelegramOAuthVerifier(BOT_TOKEN);
        // Use an auth_date from 2 hours ago — past the MAX_AUTH_AGE_MIN (60).
        long stale = Instant.now().minusSeconds(2 * 60 * 60).getEpochSecond();
        Map<String, String> fields = payloadAtAuthDate(1L, stale, BOT_TOKEN);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> verifier.verifyAndExtractId(fields));
        assertTrue(ex.getMessage().contains("muddati"),
                "stale rejection message should mention expiry, got: " + ex.getMessage());
    }

    @Test
    void rejectsMissingHash() {
        TelegramOAuthVerifier verifier = new TelegramOAuthVerifier(BOT_TOKEN);
        Map<String, String> fields = freshPayload(1L, BOT_TOKEN);
        fields.remove("hash");

        assertThrows(BadRequestException.class,
                () -> verifier.verifyAndExtractId(fields));
    }

    @Test
    void rejectsNonNumericId() {
        TelegramOAuthVerifier verifier = new TelegramOAuthVerifier(BOT_TOKEN);
        Map<String, String> fields = freshPayload(1L, BOT_TOKEN);
        // Re-sign with a non-numeric id so the hash matches but the id parse fails.
        fields.put("id", "not-a-number");
        fields.remove("hash");
        fields.put("hash", TelegramOAuthVerifier.hmacHex(BOT_TOKEN, dataCheckString(fields)));

        assertThrows(BadRequestException.class,
                () -> verifier.verifyAndExtractId(fields));
    }

    // ============================================================ helpers

    /** Build a fresh, correctly signed payload for the given Telegram user id. */
    private static Map<String, String> freshPayload(long telegramId, String botToken) {
        return payloadAtAuthDate(telegramId, Instant.now().getEpochSecond(), botToken);
    }

    private static Map<String, String> payloadAtAuthDate(long telegramId, long authDate,
                                                         String botToken) {
        // Use a LinkedHashMap so the order is stable for debugging; the verifier
        // sorts internally so insertion order doesn't matter for correctness.
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", Long.toString(telegramId));
        m.put("first_name", "Alice");
        m.put("username", "alice_test");
        m.put("auth_date", Long.toString(authDate));
        // Compute the hash from the SORTED non-hash fields.
        String hash = TelegramOAuthVerifier.hmacHex(botToken, dataCheckString(m));
        m.put("hash", hash);
        return m;
    }

    /** Mirror the verifier's data_check_string construction. */
    private static String dataCheckString(Map<String, String> fields) {
        TreeMap<String, String> sorted = new TreeMap<>(fields);
        sorted.remove("hash");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append('\n');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }
}
