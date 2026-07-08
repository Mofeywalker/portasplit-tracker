package de.wss.portasplit.web.dto;

import de.wss.portasplit.domain.Product;
import de.wss.portasplit.domain.ProductAvailability;

import java.math.BigDecimal;
import java.time.Instant;

/** Per-product availability status used in the dashboard overview. */
public record ProductStatusDto(
        String product,
        String displayName,
        boolean tracked,
        Boolean available,
        Integer currentStock,
        BigDecimal price,
        String url,
        String note,
        Long sourceTimestamp,
        Instant lastCheckedAt,
        Instant lastChangedAt,
        Instant lastAvailableAt,
        /**
         * True when the most recent check(s) failed to read this article (an error): the last known
         * state is kept, but it can no longer be updated. The dashboard flags such rows with a warning.
         */
        boolean stale,
        /**
         * Set when a real reservation attempt for this row was rejected despite it showing as available
         * (see {@link de.wss.portasplit.domain.ProductAvailability#getReserveIssueNote()}). The dashboard
         * flags such rows with a distinct warning, separate from {@link #stale}.
         */
        String reserveIssueNote
) {

    public static ProductStatusDto untracked(Product product) {
        return new ProductStatusDto(product.name(), product.displayName(), false,
                null, null, null, null, null, null, null, null, null, false, null);
    }

    public static ProductStatusDto from(ProductAvailability a) {
        return from(a, null);
    }

    /**
     * As {@link #from(ProductAvailability)}, but falls back to {@code fallbackPrice} when this reading
     * has no price of its own. Used for chains (e.g. toom) whose per-branch availability API carries no
     * price: since the catalogue price is uniform across a chain's markets, the price known from other
     * branches is shown so every branch displays one.
     */
    public static ProductStatusDto from(ProductAvailability a, BigDecimal fallbackPrice) {
        return new ProductStatusDto(
                a.getProduct().name(),
                a.getProduct().displayName(),
                true,
                a.isAvailable(),
                a.getCurrentStock(),
                a.getPrice() != null ? a.getPrice() : fallbackPrice,
                a.getUrl(),
                a.getNote(),
                a.getSourceTimestamp(),
                a.getLastCheckedAt(),
                a.getLastChangedAt(),
                a.getLastAvailableAt(),
                isStale(a),
                a.getReserveIssueNote()
        );
    }

    /**
     * A reading is stale when it has been observed at least once but the latest check attempt did not
     * produce a reading - i.e. {@code lastCheckedAt} has pulled ahead of {@code lastObservedAt}. This
     * is threshold-free and self-clearing: the next successful observation brings the two back in sync.
     */
    private static boolean isStale(ProductAvailability a) {
        Instant observed = a.getLastObservedAt();
        Instant checked = a.getLastCheckedAt();
        return observed != null && checked != null && checked.isAfter(observed);
    }
}
