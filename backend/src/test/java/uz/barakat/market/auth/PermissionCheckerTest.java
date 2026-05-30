package uz.barakat.market.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Wildcard RESOURCE:ACTION matching that backs every backend authorization rule. */
class PermissionCheckerTest {

    private static Authentication authWith(String... authorities) {
        return new UsernamePasswordAuthenticationToken("u", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void superAdminWildcardGrantsEverything() {
        Authentication a = authWith("ROLE_SUPER_ADMIN", "*:*");
        assertThat(PermissionChecker.hasPermission(a, "PRODUCTS", "WRITE")).isTrue();
        assertThat(PermissionChecker.hasPermission(a, "MANAGEMENT", "READ")).isTrue();
    }

    @Test
    void exactPermissionMatchesAndOthersAreDenied() {
        Authentication a = authWith("ROLE_SHOP_USER", "SALES:WRITE", "PRODUCTS:READ");
        assertThat(PermissionChecker.hasPermission(a, "SALES", "WRITE")).isTrue();
        assertThat(PermissionChecker.hasPermission(a, "PRODUCTS", "READ")).isTrue();
        assertThat(PermissionChecker.hasPermission(a, "PRODUCTS", "WRITE")).isFalse();
        assertThat(PermissionChecker.hasPermission(a, "MANAGEMENT", "READ")).isFalse();
    }

    @Test
    void resourceWildcardGrantsAllActions() {
        Authentication a = authWith("PRODUCTS:*");
        assertThat(PermissionChecker.hasPermission(a, "PRODUCTS", "READ")).isTrue();
        assertThat(PermissionChecker.hasPermission(a, "PRODUCTS", "WRITE")).isTrue();
        assertThat(PermissionChecker.hasPermission(a, "ORDERS", "READ")).isFalse();
    }

    @Test
    void actionWildcardGrantsAcrossResources() {
        Authentication a = authWith("*:READ");
        assertThat(PermissionChecker.hasPermission(a, "ORDERS", "READ")).isTrue();
        assertThat(PermissionChecker.hasPermission(a, "PRODUCTS", "READ")).isTrue();
        assertThat(PermissionChecker.hasPermission(a, "ORDERS", "WRITE")).isFalse();
    }

    @Test
    void roleAuthorityAloneGrantsNoPermission() {
        Authentication a = authWith("ROLE_ACCOUNT_OWNER");
        assertThat(PermissionChecker.hasPermission(a, "PRODUCTS", "READ")).isFalse();
    }

    @Test
    void matchingIsCaseInsensitive() {
        Authentication a = authWith("products:write");
        assertThat(PermissionChecker.hasPermission(a, "PRODUCTS", "WRITE")).isTrue();
    }

    @Test
    void nullAuthenticationIsDenied() {
        assertThat(PermissionChecker.hasPermission(null, "PRODUCTS", "READ")).isFalse();
    }

    @Test
    void anonymousAuthenticationIsDenied() {
        Authentication anon = new AnonymousAuthenticationToken("k", "anon",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(PermissionChecker.hasPermission(anon, "PRODUCTS", "READ")).isFalse();
    }
}
