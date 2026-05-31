package uz.barakat.license.auth;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.license.auth.AdminDtos.AccountDetailResponse;
import uz.barakat.license.auth.AdminDtos.AdminAccountResponse;
import uz.barakat.license.auth.AdminDtos.AdminUserResponse;
import uz.barakat.license.auth.AdminDtos.BlockRequest;
import uz.barakat.license.auth.AdminDtos.CreateAccountRequest;
import uz.barakat.license.auth.AdminDtos.CreateUserRequest;
import uz.barakat.license.auth.AdminDtos.GrantSubscriptionRequest;
import uz.barakat.license.auth.AdminDtos.ModulesRequest;
import uz.barakat.license.auth.AdminDtos.SetPasswordRequest;
import uz.barakat.license.auth.AdminDtos.SetPermissionsRequest;
import uz.barakat.license.auth.AdminDtos.UpdateAccountRequest;
import uz.barakat.license.domain.AdminAuditEntry;
import uz.barakat.license.domain.SubscriptionPlan;
import uz.barakat.license.exception.BadRequestException;

/**
 * Super-admin REST API. All endpoints require the caller to hold the
 * {@code SUPER_ADMIN} role; the role check is enforced via Spring
 * Security's {@link PreAuthorize}.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService service;
    private final AuditService audit;
    private final PermissionService permissions;
    private final BillingService billing;

    public AdminController(AdminService service, AuditService audit,
                           PermissionService permissions, BillingService billing) {
        this.service = service;
        this.audit = audit;
        this.permissions = permissions;
        this.billing = billing;
    }

    /**
     * Paginated read of the audit trail, newest first. The panel calls
     * this on the "Audit log" tab and on every refresh; the implementation
     * caps page size at 200 so a runaway client can't drag the whole
     * table over the wire.
     */
    @GetMapping("/audit")
    public List<AdminAuditEntry> auditLog(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return audit.recent(page, size);
    }

    /**
     * Stream the audit log as RFC 4180 CSV for download. Date range is
     * inclusive on both ends ({@code from}/{@code to} are dates, not
     * timestamps — the server widens them to cover the full UTC day).
     * Either bound is optional; omitting both exports everything.
     */
    @GetMapping(value = "/audit/export", produces = "text/csv")
    public ResponseEntity<byte[]> auditExport(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = audit.exportCsv(from, to);
        String filename = "audit-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    @GetMapping("/accounts")
    public List<AdminAccountResponse> listAccounts() {
        return service.listAccounts();
    }

    @GetMapping("/accounts/{id}")
    public AccountDetailResponse accountDetail(@PathVariable Long id) {
        return service.accountDetail(id);
    }

    @PostMapping("/accounts")
    public AdminAccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return service.createAccount(request);
    }

    @PutMapping("/accounts/{id}")
    public AdminAccountResponse updateAccount(@PathVariable Long id,
                                              @Valid @RequestBody UpdateAccountRequest request) {
        return service.updateAccount(id, request);
    }

    @PatchMapping("/accounts/{id}/block")
    public AdminAccountResponse setBlocked(@PathVariable Long id,
                                           @RequestBody BlockRequest request) {
        return service.setBlocked(id, request.blocked());
    }

    /** Manually grant / extend a subscription (set the plan for N months, no
     *  charge) — comp an account or extend a trial from the admin panel. */
    @PostMapping("/accounts/{id}/grant")
    public AdminAccountResponse grantSubscription(@PathVariable Long id,
                                                  @Valid @RequestBody GrantSubscriptionRequest request) {
        SubscriptionPlan plan;
        try {
            plan = SubscriptionPlan.valueOf(request.plan().trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new BadRequestException("Noma'lum reja: " + request.plan());
        }
        int months = request.months() == null ? 1 : request.months();
        billing.grantSubscription(id, plan, months);
        audit.record("SUBSCRIPTION_GRANT", "ACCOUNT", id, null,
                "plan=" + plan + ", months=" + months);
        return service.accountDetail(id).account();
    }

    /**
     * Replace the per-account sidebar-module allow-list. Send the full
     * CSV of allowed keys (or an empty / null value to mean "all
     * modules visible"). The frontend owns the canonical set of keys.
     */
    @PatchMapping("/accounts/{id}/modules")
    public AdminAccountResponse setModules(@PathVariable Long id,
                                           @RequestBody ModulesRequest request) {
        return service.setModules(id, request == null ? null : request.enabledModules());
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        service.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{id}/users")
    public AdminUserResponse createUser(@PathVariable Long id,
                                        @Valid @RequestBody CreateUserRequest request) {
        return service.createUser(id, request);
    }

    @PatchMapping("/users/{userId}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long userId,
                                              @Valid @RequestBody SetPasswordRequest request) {
        service.resetPassword(userId, request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Replace the per-user permission CSV (Phase 4.5 granular ACL).
     * Body: {@code {"permissions":"USERS:READ,AUDIT:READ"}}. Send null
     * or empty to clear the override and fall back to role defaults.
     */
    @PatchMapping("/users/{userId}/permissions")
    public ResponseEntity<Void> setPermissions(@PathVariable Long userId,
                                               @RequestBody SetPermissionsRequest request) {
        permissions.setPermissions(userId,
                request == null ? null : request.permissions());
        audit.record("USER_SET_PERMISSIONS", "USER", userId, null,
                request == null ? null : request.permissions());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        service.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
