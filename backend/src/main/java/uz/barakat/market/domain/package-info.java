/**
 * Domain entities for SavdoPRO.
 *
 * <p>Defines the package-level {@link org.hibernate.annotations.FilterDef}
 * {@code tenantFilter} consumed by every entity annotated with
 * {@link org.hibernate.annotations.Filter} {@code (name = "tenantFilter",
 * condition = "shop_id = :shopId")}. The filter is enabled per request
 * by {@code TenantFilterAspect} so reads are automatically scoped to
 * the active shop.
 */
@org.hibernate.annotations.FilterDef(
        name = "tenantFilter",
        parameters = @org.hibernate.annotations.ParamDef(name = "shopId", type = Long.class))
package uz.barakat.market.domain;
