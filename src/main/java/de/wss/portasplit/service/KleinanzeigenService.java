package de.wss.portasplit.service;

import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.jobs.JobLogger;
import de.wss.portasplit.kleinanzeigen.KleinanzeigenClient;
import de.wss.portasplit.kleinanzeigen.KleinanzeigenListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches a kleinanzeigen.de search-results URL and fires a one-off Telegram notification for every
 * <em>freshly-posted</em> offer. Unlike the other checkers this does not track per-shop availability
 * state: kleinanzeigen offers are individual, transient listings, so we only care about "is there a
 * new one".
 *
 * <p>Each poll notifies every listing younger than {@code app.kleinanzeigen.freshness-minutes} whose
 * ad id has not been seen yet (oldest-first, so the newest is reported last). Seen ids are remembered
 * in a small, bounded ring (persisted via {@link SettingsService}), so each offer is reported at most
 * once - even if it later drops off the page and reappears, and even when two offers share the same
 * (minute-precise) timestamp. An offer is only marked seen once its Telegram message is actually
 * accepted, so a transient send failure is retried on the next poll instead of being lost.
 */
@Service
public class KleinanzeigenService {

    private static final Logger log = LoggerFactory.getLogger(KleinanzeigenService.class);

    /** Cap on the remembered ad-id ring - large enough to cover any realistic freshness window. */
    private static final int MAX_REMEMBERED_IDS = 50;

    private final KleinanzeigenClient client;
    private final NotificationService notificationService;
    private final SettingsService settings;
    private final KleinanzeigenStatusHolder statusHolder;
    private final AppProperties props;
    private final JobLogger jobLog;

    public KleinanzeigenService(KleinanzeigenClient client,
                                NotificationService notificationService,
                                SettingsService settings,
                                KleinanzeigenStatusHolder statusHolder,
                                AppProperties props,
                                JobLogger jobLog) {
        this.client = client;
        this.notificationService = notificationService;
        this.settings = settings;
        this.statusHolder = statusHolder;
        this.props = props;
        this.jobLog = jobLog;
    }

    /**
     * The effective search-results URL: a runtime override set from the settings page takes precedence
     * over the static {@code app.kleinanzeigen.url} config. Empty when neither is configured.
     */
    public String effectiveUrl() {
        String url = settings.get(SettingsService.KLEINANZEIGEN_URL)
                .orElseGet(() -> props.kleinanzeigen().url());
        return url == null ? "" : url.trim();
    }

    /** Whether a search URL is configured at all. */
    public boolean hasUrl() {
        return !effectiveUrl().isBlank();
    }

    /**
     * Whether the watcher is active. It is enabled precisely when a search URL is configured (via the
     * settings page or the {@code KLEINANZEIGEN_URL} env/config) - no link means disabled. The
     * effective on/off (incl. the runtime worker override) is owned by
     * {@code CheckJobService.enabled(KLEINANZEIGEN)}, which gates enqueuing.
     */
    public boolean enabled() {
        return hasUrl();
    }

    /**
     * Runs one kleinanzeigen poll. Synchronized so scheduled and manual runs never overlap. Whether
     * this runs at all is decided upstream by the job queue (a disabled source is never enqueued).
     */
    public synchronized ScrapeCheckResult runCheck() {
        Instant runAt = Instant.now();
        String url = effectiveUrl();
        if (url.isBlank()) {
            return ScrapeCheckResult.skipped(runAt, "Keine kleinanzeigen-URL konfiguriert");
        }

        long start = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        List<KleinanzeigenListing> listings = client.scrape(url.trim());
        if (listings == null) {
            // Page could not be read this run (bot wall / render hiccup): record the attempt but do
            // not notify, so a transient failure never produces a bogus alert.
            errors.add("Suchseite nicht ausgelesen");
            statusHolder.recordFailure(runAt, "Suchseite nicht ausgelesen");
            long d = System.currentTimeMillis() - start;
            log.warn("kleinanzeigen check could not read the results page");
            jobLog.warn("Kleinanzeigen: Suchseite nicht ausgelesen (Bot-Wall / Render-Problem)");
            return new ScrapeCheckResult(runAt, true, 0, 0, d, errors);
        }

        long windowSeconds = Math.max(1, props.kleinanzeigen().freshnessMinutes()) * 60L;
        Set<String> seen = loadSeenIds();

        // Listings are newest-first; notify oldest-first so the newest ends up most-recently
        // remembered, and surface every fresh offer (not just the single newest) so a second new
        // offer in the same window is never dropped.
        List<KleinanzeigenListing> toNotify = new ArrayList<>();
        for (KleinanzeigenListing l : listings) {
            if (l.ageSeconds(runAt) <= windowSeconds && !seen.contains(l.adId())) {
                toNotify.add(l);
            }
        }
        Collections.reverse(toNotify);

        jobLog.info("Kleinanzeigen: {} Anzeige(n) gelesen · {} im Frische-Fenster ({} Min) · {} davon neu",
                listings.size(), countFresh(listings, runAt, windowSeconds),
                props.kleinanzeigen().freshnessMinutes(), toNotify.size());

        int notified = 0;
        boolean changed = false;
        for (KleinanzeigenListing l : toNotify) {
            if (notificationService.notifyNewListing(l)) {
                seen.add(l.adId());
                changed = true;
                notified++;
                log.info("New kleinanzeigen offer: adId={} '{}' ({}s old) - notification sent",
                        l.adId(), l.title(), l.ageSeconds(runAt));
                jobLog.info("Kleinanzeigen: neues Angebot gemeldet - „{}“ ({}s alt)",
                        l.title(), l.ageSeconds(runAt));
            } else {
                // Send failed/not delivered: leave it unseen so the next poll retries it (while it is
                // still fresh). Stop here - further sends this run would almost certainly fail too.
                errors.add("Telegram-Versand fehlgeschlagen für Anzeige " + l.adId());
                log.warn("Telegram send failed for kleinanzeigen ad {} - will retry next poll", l.adId());
                jobLog.warn("Kleinanzeigen: Telegram-Versand fehlgeschlagen für Anzeige {} - Wiederholung beim nächsten Lauf", l.adId());
                break;
            }
        }
        if (changed) {
            saveSeenIds(seen);
        }
        statusHolder.recordSuccess(runAt, listings, windowSeconds, seen);

        long duration = System.currentTimeMillis() - start;
        log.info("kleinanzeigen check done in {} ms: {} listing(s) seen, {} new offer(s) notified",
                duration, listings.size(), notified);
        return new ScrapeCheckResult(runAt, true, listings.size(), notified, duration, errors);
    }

    /** Counts how many of the listings fall within the freshness window. */
    private static long countFresh(List<KleinanzeigenListing> listings, Instant runAt, long windowSeconds) {
        long c = 0;
        for (KleinanzeigenListing l : listings) {
            if (l.ageSeconds(runAt) <= windowSeconds) {
                c++;
            }
        }
        return c;
    }

    /** Loads the remembered ad-id ring (insertion order preserved). */
    private Set<String> loadSeenIds() {
        Set<String> ids = new LinkedHashSet<>();
        settings.get(SettingsService.KLEINANZEIGEN_NOTIFIED_IDS).ifPresent(csv -> {
            for (String id : csv.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    ids.add(trimmed);
                }
            }
        });
        return ids;
    }

    /** Persists the ad-id ring, keeping only the most recent {@link #MAX_REMEMBERED_IDS} entries. */
    private void saveSeenIds(Set<String> ids) {
        List<String> ordered = new ArrayList<>(ids);
        if (ordered.size() > MAX_REMEMBERED_IDS) {
            ordered = ordered.subList(ordered.size() - MAX_REMEMBERED_IDS, ordered.size());
        }
        settings.set(SettingsService.KLEINANZEIGEN_NOTIFIED_IDS, String.join(",", ordered));
    }
}
