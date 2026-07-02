package de.wss.portasplit.service;

import de.wss.portasplit.kleinanzeigen.KleinanzeigenListing;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the most recent kleinanzeigen scrape result so the dashboard can show the current offers and
 * the last-check status. A failed scrape keeps the last known listings (so the UI does not flash
 * empty on a transient hiccup) but flips {@code ok} to false.
 */
@Component
public class KleinanzeigenStatusHolder {

    /**
     * @param checkedAt    when the last poll ran.
     * @param ok           whether that poll could read the page.
     * @param error        error note if the poll failed, otherwise {@code null}.
     * @param listings     the offers from the last successful scrape (newest first).
     * @param windowSeconds the freshness window in effect, so the dashboard can flag "fresh" offers.
     * @param notifiedIds  ad ids already notified (so the dashboard can flag them).
     */
    public record Snapshot(Instant checkedAt, boolean ok, String error,
                           List<KleinanzeigenListing> listings, long windowSeconds,
                           Set<String> notifiedIds) {}

    private final AtomicReference<Snapshot> ref = new AtomicReference<>();

    public void recordSuccess(Instant at, List<KleinanzeigenListing> listings, long windowSeconds,
                              Set<String> notifiedIds) {
        ref.set(new Snapshot(at, true, null, List.copyOf(listings), windowSeconds, Set.copyOf(notifiedIds)));
    }

    public void recordFailure(Instant at, String error) {
        Snapshot prev = ref.get();
        ref.set(new Snapshot(
                at, false, error,
                prev != null ? prev.listings() : List.of(),
                prev != null ? prev.windowSeconds() : 0L,
                prev != null ? prev.notifiedIds() : Set.of()));
    }

    public Snapshot last() {
        return ref.get();
    }
}
