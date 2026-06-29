package uz.barakat.license.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean-validation constraint enforcing {@link PasswordPolicy} on a password
 * field. Applied to every DTO where a password is set (signup, reset, admin
 * create user/account, admin reset) so all flows share one rule and one
 * localised message instead of drifting {@code @Size} bounds.
 *
 * <p>Target set mirrors the standard Jakarta constraints so it propagates to
 * record components exactly like {@code @Size}/{@code @NotBlank} do.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default PasswordPolicy.MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
