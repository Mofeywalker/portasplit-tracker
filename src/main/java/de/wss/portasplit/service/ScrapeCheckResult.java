package de.wss.portasplit.service;

import java.time.Instant;
import java.util.List;

/**
 * Summary of one browser-scraped poll run (Amazon, Lidl, …).
 *
 * @param notices non-fatal, human-readable heads-ups worth surfacing on the dashboard even when the
 *                run technically succeeded - currently "the product page is delisted / no longer
 *                reachable". Distinct from {@code errors} (scrape/network failures): a notice reflects
 *                a real, observed catalogue state, not a transient glitch.
 */
public record ScrapeCheckResult(
        Instant runAt,
        boolean ran,
        int scanned,
        int available,
        long durationMs,
        List<String> errors,
        List<String> notices
) {
    /** Back-compatible constructor for sources that never raise delisted-page notices. */
    public ScrapeCheckResult(Instant runAt, boolean ran, int scanned, int available,
                             long durationMs, List<String> errors) {
        this(runAt, ran, scanned, available, durationMs, errors, List.of());
    }

    public static ScrapeCheckResult skipped(Instant runAt, String reason) {
        return new ScrapeCheckResult(runAt, false, 0, 0, 0, List.of(reason));
    }
}
