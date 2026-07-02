package de.wss.portasplit.service;

import java.time.Instant;
import java.util.List;

/** Summary of one browser-scraped poll run (Amazon, Lidl, …). */
public record ScrapeCheckResult(
        Instant runAt,
        boolean ran,
        int scanned,
        int available,
        long durationMs,
        List<String> errors
) {
    public static ScrapeCheckResult skipped(Instant runAt, String reason) {
        return new ScrapeCheckResult(runAt, false, 0, 0, 0, List.of(reason));
    }
}
