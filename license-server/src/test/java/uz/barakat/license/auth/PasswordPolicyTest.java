package uz.barakat.license.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.barakat.license.auth.AdminDtos.CreateAccountRequest;
import uz.barakat.license.auth.AdminDtos.CreateUserRequest;
import uz.barakat.license.auth.AdminDtos.SetPasswordRequest;
import uz.barakat.license.auth.AuthDtos.RegisterRequest;
import uz.barakat.license.auth.AuthDtos.ResetPasswordRequest;

/**
 * Unifies the password rule across signup, reset and the admin flows
 * (audit: signup min 9, reset min 6, admin min 4 → now all min 9 + letter +
 * digit). The {@code *RejectsShort} cases prove a 4/6-char password that the
 * old {@code @Size} rules accepted is now refused at the DTO boundary.
 */
class PasswordPolicyTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    // ---------------------------------------------------------- policy logic

    @ParameterizedTest
    @ValueSource(strings = {"password1", "Pa$$w0rd1", "abcdefg12", "12345678a"})
    void acceptsStrongPasswords(String pw) {
        assertTrue(PasswordPolicy.isValid(pw), pw + " should pass");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short1",      // 6 chars — too short (old reset min)
            "test",        // 4 chars — too short (old admin min)
            "abcdefg1",    // 8 chars — one below the boundary
            "abcdefghi",   // 9 letters, no digit
            "123456789",   // 9 digits, no letter
            ""             // blank
    })
    void rejectsWeakPasswords(String pw) {
        assertFalse(PasswordPolicy.isValid(pw), pw + " should fail");
    }

    @Test
    void boundaryAtNineCharacters() {
        assertFalse(PasswordPolicy.isValid("abcdefg1"));   // 8
        assertTrue(PasswordPolicy.isValid("abcdefg12"));   // 9
    }

    @Test
    void nullIsDeferredToNotBlank() {
        // null passes the policy so a separate @NotBlank owns "required".
        assertTrue(PasswordPolicy.isValid(null));
    }

    @Test
    void rejectsAbsurdlyLongPassword() {
        assertFalse(PasswordPolicy.isValid("a1".repeat(100)));   // 200 chars
    }

    // -------------------------------------------------- DTO constraint wiring

    @Test
    void registerRejectsShortPassword() {
        var dto = new RegisterRequest("Biznes", "Ism", "egasi", "weak1", null, null);
        assertTrue(hasPasswordViolation(validator.validate(dto)));
    }

    @Test
    void registerAcceptsStrongPassword() {
        var dto = new RegisterRequest("Biznes", "Ism", "egasi", "Parol1234", null, null);
        assertFalse(hasPasswordViolation(validator.validate(dto)));
    }

    @Test
    void resetPasswordRejectsOldSixCharMinimum() {
        var dto = new ResetPasswordRequest("+998901112233", "123456", "abc123");
        assertTrue(hasPasswordViolation(validator.validate(dto)));
    }

    @Test
    void adminSetPasswordRejectsOldFourCharMinimum() {
        assertTrue(hasPasswordViolation(validator.validate(new SetPasswordRequest("ab12"))));
        assertFalse(hasPasswordViolation(validator.validate(new SetPasswordRequest("Parol1234"))));
    }

    @Test
    void adminCreateUserRejectsShortPassword() {
        var dto = new CreateUserRequest("kassir", "1234", "Kassir", "USER");
        assertTrue(hasPasswordViolation(validator.validate(dto)));
    }

    @Test
    void adminCreateAccountRejectsShortOwnerPassword() {
        var dto = new CreateAccountRequest("Do'kon", null, null, null,
                "egasi", "1234", "Ism");
        assertTrue(hasPasswordViolation(validator.validate(dto)));
    }

    private static boolean hasPasswordViolation(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().toLowerCase().contains("password"));
    }
}
