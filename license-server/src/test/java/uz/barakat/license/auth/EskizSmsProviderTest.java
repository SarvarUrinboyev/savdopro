package uz.barakat.license.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the pure parts of the Eskiz adapter — phone normalisation
 * and login-token extraction. The HTTP round-trip itself needs live credentials
 * and is exercised in the go-live sandbox plan, not here.
 */
class EskizSmsProviderTest {

    private final EskizSmsProvider provider =
            new EskizSmsProvider("user@example.com", "secret", "4546", "https://notify.eskiz.uz/");

    @Test
    void normalizePhoneStripsSeparatorsAndPlus() {
        assertThat(EskizSmsProvider.normalizePhone("+998 90 123 45 67")).isEqualTo("998901234567");
        assertThat(EskizSmsProvider.normalizePhone("998901234567")).isEqualTo("998901234567");
    }

    @Test
    void normalizePhonePrefixesBareNineDigitLocalNumber() {
        assertThat(EskizSmsProvider.normalizePhone("901234567")).isEqualTo("998901234567");
    }

    @Test
    void normalizePhoneHandlesNullAndJunk() {
        assertThat(EskizSmsProvider.normalizePhone(null)).isEmpty();
        assertThat(EskizSmsProvider.normalizePhone("abc")).isEmpty();
    }

    @Test
    void parseTokenReadsDataToken() {
        String json = "{\"message\":\"token_generated\",\"data\":{\"token\":\"abc.123.xyz\"},"
                + "\"token_type\":\"bearer\"}";
        assertThat(provider.parseToken(json)).isEqualTo("abc.123.xyz");
    }

    @Test
    void parseTokenReturnsNullWhenAbsentOrInvalid() {
        assertThat(provider.parseToken("{\"message\":\"unauthorized\"}")).isNull();
        assertThat(provider.parseToken("not json")).isNull();
    }
}
