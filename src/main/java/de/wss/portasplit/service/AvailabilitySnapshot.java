package de.wss.portasplit.service;

import java.math.BigDecimal;

/**
 * Source-neutral availability reading for a single (shop, product), produced by a checker
 * (a per-chain checker, Amazon or Lidl) and fed into {@link AvailabilityReconciler}.
 *
 * @param observed   whether we got a definitive reading this run. When {@code false} (e.g. a
 *                   transient scrape/network failure) the reconciler only bumps the
 *                   "last checked" timestamp and leaves the known state untouched, so a single
 *                   failed run never produces a spurious "sold out" event.
 * @param refreshMeta whether to refresh url/price/note/sourceTimestamp from this reading. A source
 *                   that could not refresh the article's metadata this run passes
 *                   {@code observed=true, refreshMeta=false} so it keeps its last known link/price.
 * @param available  the final availability decision (in stock and, for Amazon, actually shippable
 *                   within the configured window).
 * @param stock      latest stock count if known, otherwise {@code null}.
 * @param price      latest price if known, otherwise {@code null}.
 * @param url        product url if known, otherwise {@code null}.
 * @param sourceTimestamp epoch millis of the source reading if known, otherwise {@code null}.
 * @param note       optional short human note (e.g. "Prime · Lieferung Do, 26.06."), otherwise {@code null}.
 * @param managesReserveIssue whether this source authoritatively reports the reserve-issue state on
 *                   every run. When {@code true} the reconciler overwrites the stored
 *                   {@link #reserveIssueNote} with this reading's value (setting or clearing it);
 *                   when {@code false} (the default for sources that don't check reservability, and
 *                   for toom, whose note is maintained out-of-band by {@code ToomReserveVerifier})
 *                   the reconciler leaves the stored note untouched.
 * @param reserveIssueNote when {@code managesReserveIssue} is set, the note to store: a short human
 *                   message when the article shows stock but cannot be reserved, or {@code null} for
 *                   "no reserve issue". Ignored when {@code managesReserveIssue} is {@code false}.
 */
public record AvailabilitySnapshot(
        boolean observed,
        boolean refreshMeta,
        boolean available,
        Integer stock,
        BigDecimal price,
        String url,
        Long sourceTimestamp,
        String note,
        boolean managesReserveIssue,
        String reserveIssueNote
) {

    /**
     * A reading from a source that does not report reservability: keeps the stored reserve-issue note
     * untouched ({@code managesReserveIssue = false}).
     */
    public AvailabilitySnapshot(boolean observed, boolean refreshMeta, boolean available, Integer stock,
                               BigDecimal price, String url, Long sourceTimestamp, String note) {
        this(observed, refreshMeta, available, stock, price, url, sourceTimestamp, note, false, null);
    }

    /** A failed reading: nothing definitive was observed this run. */
    public static AvailabilitySnapshot notObserved() {
        return new AvailabilitySnapshot(false, false, false, null, null, null, null, null);
    }
}
