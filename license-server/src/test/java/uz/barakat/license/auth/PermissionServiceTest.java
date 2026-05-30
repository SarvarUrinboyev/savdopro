package uz.barakat.license.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.barakat.license.domain.AppUser;
import uz.barakat.license.domain.UserRole;
import uz.barakat.license.exception.BadRequestException;
import uz.barakat.license.repository.AppUserRepository;

/** Unit tests for the role + override permission model. */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock private AppUserRepository users;
    @InjectMocks private PermissionService service;

    @Test
    void superAdminHasEveryPermissionRegardlessOfOverrides() {
        AppUser sa = user(UserRole.SUPER_ADMIN, null);

        assertTrue(service.has(sa, "ACCOUNTS:WRITE"));
        assertTrue(service.has(sa, "DEBTS:READ"));
        assertTrue(service.has(sa, "ANYTHING:ANYTHING"));   // never reached as a key,
                                                            // but the SA shortcut returns true.
    }

    @Test
    void accountOwnerGetsFullOperationalControlButNotPlatformAdmin() {
        AppUser owner = user(UserRole.ACCOUNT_OWNER, null);

        // Full control of their own account's data + staff.
        assertTrue(service.has(owner, "USERS:WRITE"));
        assertTrue(service.has(owner, "AUDIT:READ"));
        assertTrue(service.has(owner, "PRODUCTS:WRITE"));
        assertTrue(service.has(owner, "DEBTS:WRITE"));
        assertTrue(service.has(owner, "SALES:WRITE"));
        assertTrue(service.has(owner, "MANAGEMENT:WRITE"));
        // But NOT platform-level account administration (SUPER_ADMIN only).
        assertFalse(service.has(owner, "ACCOUNTS:WRITE"));
    }

    @Test
    void overrideAddsGrantsBeyondRoleDefaults() {
        AppUser owner = user(UserRole.ACCOUNT_OWNER, "PRODUCTS:WRITE,DEBTS:READ");

        assertTrue(service.has(owner, "PRODUCTS:WRITE"));
        assertTrue(service.has(owner, "DEBTS:READ"));
        // Role default still applies.
        assertTrue(service.has(owner, "USERS:WRITE"));
    }

    @Test
    void wildcardActionGrantsBothReadAndWrite() {
        AppUser owner = user(UserRole.ACCOUNT_OWNER, "PRODUCTS:*");

        assertTrue(service.has(owner, "PRODUCTS:READ"));
        assertTrue(service.has(owner, "PRODUCTS:WRITE"));
    }

    @Test
    void effectiveReturnsUnionOfDefaultsAndOverrides() {
        AppUser owner = user(UserRole.ACCOUNT_OWNER, "PRODUCTS:WRITE");
        Set<String> eff = service.effective(owner);

        // Role defaults present
        assertTrue(eff.contains("USERS:WRITE"), "should keep role defaults, got: " + eff);
        // Override present
        assertTrue(eff.contains("PRODUCTS:WRITE"), "should include override, got: " + eff);
    }

    @Test
    void setPermissionsNormalisesAndPersists() {
        AppUser owner = user(UserRole.ACCOUNT_OWNER, null);
        owner.setId(42L);
        when(users.findById(42L)).thenReturn(Optional.of(owner));

        service.setPermissions(42L, "  products:write , USERS:read,  ");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        AppUser saved = captor.getValue();
        assertEquals("PRODUCTS:WRITE,USERS:READ", saved.getPermissions());
    }

    @Test
    void setPermissionsRejectsUnknownTokens() {
        AppUser owner = user(UserRole.ACCOUNT_OWNER, null);
        owner.setId(42L);
        // Don't stub findById — the validation throws before the DB call.

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.setPermissions(42L, "TYPO:WRITE"));
        assertTrue(ex.getMessage().contains("TYPO"),
                "error should name the bad resource, got: " + ex.getMessage());
    }

    @Test
    void blankCsvClearsOverrideToRoleDefaults() {
        AppUser owner = user(UserRole.ACCOUNT_OWNER, "PRODUCTS:WRITE");
        owner.setId(42L);
        when(users.findById(42L)).thenReturn(Optional.of(owner));

        service.setPermissions(42L, "   ");

        verify(users).save(any(AppUser.class));
        assertEquals(null, owner.getPermissions(),
                "blank input should null out the column, got: " + owner.getPermissions());
    }

    @Test
    void shopUserHasFrontOfShopWritesOnly() {
        AppUser shop = user(UserRole.SHOP_USER, null);
        // Can run the till: sell, take payments, manage customers + debts.
        assertTrue(service.has(shop, "SALES:WRITE"));
        assertTrue(service.has(shop, "PAYMENTS:WRITE"));
        assertTrue(service.has(shop, "CUSTOMERS:WRITE"));
        assertTrue(service.has(shop, "REPORTS:READ"));
        assertTrue(service.has(shop, "PRODUCTS:READ"));
        // But not owner / admin functions.
        assertFalse(service.has(shop, "PRODUCTS:WRITE"));
        assertFalse(service.has(shop, "MANAGEMENT:READ"));
        assertFalse(service.has(shop, "USERS:WRITE"));
        assertFalse(service.has(shop, "ACCOUNTS:WRITE"));
    }

    private static AppUser user(UserRole role, String permissions) {
        AppUser u = new AppUser();
        u.setRole(role);
        u.setAccountId(1L);
        u.setUsername("test");
        u.setPasswordHash("x");
        u.setPermissions(permissions);
        return u;
    }
}
