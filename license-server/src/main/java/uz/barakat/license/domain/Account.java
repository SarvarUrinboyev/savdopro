package uz.barakat.license.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One paying tenant of the SavdoPRO platform — a single shop owner who
 * may have many sub-shops underneath. The subscription lifecycle is
 * encoded by {@code subscriptionExpires} (cut-off date) and {@code blocked}
 * (super-admin manual lock). The desktop app checks these fields at
 * every login and refuses access if blocked or expired.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account extends BaseEntity {

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Column(name = "contact_note", length = 500)
    private String contactNote;

    /**
     * Last paid day. {@code null} = no subscription tracking (e.g. the
     * super-admin account). After this date the account auto-blocks.
     */
    @Column(name = "subscription_expires")
    private LocalDate subscriptionExpires;

    /** Manual lock by the super-admin. Overrides the expiry check. */
    @Column(nullable = false)
    private boolean blocked = false;

    /**
     * When true, the account's users are nudged to enable 2FA (surfaced as
     * {@code mfaSetupRequired} in /me). Soft — never hard-blocks login (V13).
     */
    @Column(name = "require_mfa", nullable = false)
    private boolean requireMfa = false;

    /** Subscription tier — sets the per-account limits (users / shops). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionPlan plan = SubscriptionPlan.TRIAL;

    /**
     * White-label brand fields (Phase 4.6 / v2.0). All optional — when
     * set, the desktop applies them as CSS variables on login so the
     * customer sees their own brand in the title bar, sidebar and
     * receipts. When null we fall back to the SavdoPRO defaults.
     *
     * <ul>
     *   <li>{@code brandName} — what shows on the title bar and printed receipt header</li>
     *   <li>{@code brandColorPrimary} — hex (e.g. {@code #1e3a8a}) used for buttons, links, sidebar accent</li>
     *   <li>{@code brandColorSecondary} — hex used for badges + the green totals accent</li>
     *   <li>{@code brandLogoUrl} — fully-qualified URL to a PNG / SVG; loaded by the splash + login</li>
     *   <li>{@code brandFooterNote} — bottom-of-receipt one-liner</li>
     * </ul>
     */
    @Column(name = "brand_name", length = 120)
    private String brandName;

    @Column(name = "brand_color_primary", length = 20)
    private String brandColorPrimary;

    @Column(name = "brand_color_secondary", length = 20)
    private String brandColorSecondary;

    @Column(name = "brand_logo_url", length = 500)
    private String brandLogoUrl;

    @Column(name = "brand_footer_note", length = 300)
    private String brandFooterNote;

    /**
     * Comma-separated module keys this account is allowed to see in the
     * sidebar (e.g. "dashboard,warehouse,reports,customers"). NULL means
     * all modules are visible — the default for any legacy account that
     * existed before the per-module gating feature was added.
     *
     * The frontend's Sidebar.jsx owns the canonical list of module keys;
     * the backend treats this column as opaque text so future modules
     * don't require a schema change.
     */
    @Column(name = "enabled_modules", length = 2000)
    private String enabledModules;
}
