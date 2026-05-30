package uz.barakat.license.domain;

/**
 * Subscription tiers and the per-account limits each one grants. A new
 * self-service signup starts on {@link #TRIAL}; paid tiers are reached by
 * the (upcoming) billing flow. Limits are enforced where accounts/shops/
 * users are created. Prices are in UZS/month and are operator-tunable
 * placeholders — the real numbers come from the pricing page / PSP.
 */
public enum SubscriptionPlan {

    TRIAL(1, 2, 0),
    BASIC(1, 5, 99_000),
    STANDARD(3, 15, 249_000),
    PRO(20, 100, 599_000);

    private final int maxShops;
    private final int maxUsers;
    private final long monthlyPriceUzs;

    SubscriptionPlan(int maxShops, int maxUsers, long monthlyPriceUzs) {
        this.maxShops = maxShops;
        this.maxUsers = maxUsers;
        this.monthlyPriceUzs = monthlyPriceUzs;
    }

    public int maxShops() {
        return maxShops;
    }

    public int maxUsers() {
        return maxUsers;
    }

    public long monthlyPriceUzs() {
        return monthlyPriceUzs;
    }
}
