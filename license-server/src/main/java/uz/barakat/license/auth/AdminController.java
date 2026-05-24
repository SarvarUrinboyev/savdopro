package uz.barakat.license.auth;

import jakarta.validation.Valid;
import java.util.List;
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
import uz.barakat.license.auth.AdminDtos.SetPasswordRequest;
import uz.barakat.license.auth.AdminDtos.UpdateAccountRequest;
import uz.barakat.license.domain.AdminAuditEntry;

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

    public AdminController(AdminService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
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

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        service.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
