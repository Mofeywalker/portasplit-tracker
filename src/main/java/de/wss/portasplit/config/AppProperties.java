package de.wss.portasplit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

import de.wss.portasplit.domain.Product;

/**
 * Central configuration for the PortaSplit tracker, bound from the {@code app.*} properties.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Telegram telegram,
        @DefaultValue Notifications notifications,
        @DefaultValue Shops shops,
        @DefaultValue Cloakbrowser cloakbrowser,
        @DefaultValue Amazon amazon,
        @DefaultValue Lidl lidl,
        @DefaultValue Kleinanzeigen kleinanzeigen,
        @DefaultValue Chain obi,
        @DefaultValue Chain toom,
        @DefaultValue Chain globus,
        @DefaultValue Chain hagebau,
        @DefaultValue Chain hornbach,
        @DefaultValue Chain bauhaus,
        @DefaultValue ToomReserve toomReserve
) {

    /**
     * toom auto-reserve. Static config only; the feature on/off switch and the login credentials are
     * runtime state (dashboard) — the switch lives in {@code SettingsService} and the credentials are
     * stored encrypted at rest. This feature is separate from scraping and requires a logged-in toom
     * account: an item added to the cart is only secured for a logged-in customer.
     *
     * @param fingerprintSeed dedicated CloakBrowser fingerprint seed so the logged-in toom session
     *                        lives in its own warm tab, isolated from the scrapers' tabs.
     * @param cryptoKey       Base64-encoded AES key (16/24/32 bytes) used to encrypt the stored toom
     *                        password at rest. From {@code APP_TOOM_RESERVE_CRYPTO_KEY}; never committed.
     *                        When blank, credentials cannot be stored and the feature stays disabled.
     */
    public record ToomReserve(
            @DefaultValue("55268") int fingerprintSeed,
            @DefaultValue("") String cryptoKey
    ) {}

    /**
     * Per-chain branch availability checking (OBI, toom, Globus, Hagebau, Hornbach, Bauhaus). Each
     * chain is its own parallel worker that polls that chain's own site/API for its branches. Polled
     * deliberately low-frequency (~2-2.3 min) since these are per-branch lookups against the chains.
     */
    public record Chain(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("120000") long minIntervalMs,
            @DefaultValue("140000") long maxIntervalMs,
            @DefaultValue("20000") long initialDelayMs
    ) {}

    public record Telegram(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("") String botToken,
            @DefaultValue("") String chatId,
            @DefaultValue("https://api.telegram.org") String apiBase
    ) {}

    public record Notifications(
            /** Notify when a product is observed available for the very first time (no prior state). */
            @DefaultValue("true") boolean notifyOnFirstSeen
    ) {}

    public record Shops(
            /** Bundled default shop list (classpath resource). */
            @DefaultValue("classpath:shops.seed.json") String seedFile,
            /** Optional external override file; merged in addition to the seed file if it exists. */
            @DefaultValue("./shops.json") String configFile,
            /** Merge the seed/config files into the database on startup (existing rows are never overwritten). */
            @DefaultValue("true") boolean mergeOnStartup
    ) {}

    /**
     * Shared CloakBrowser stealth Chromium reached over the Chrome DevTools Protocol. Used by all
     * browser-scraped sources (Amazon, Lidl, …).
     */
    public record Cloakbrowser(
            /** CDP endpoint of the CloakBrowser container (cloakserve), e.g. http://localhost:9222. */
            @DefaultValue("http://localhost:9222") String cdpUrl,
            /** Per-page navigation/parse timeout. */
            @DefaultValue("45s") java.time.Duration timeout,
            @DefaultValue("Europe/Berlin") String cloakTimezone,
            @DefaultValue("de-DE") String cloakLocale,
            @DefaultValue("windows") String cloakPlatform
    ) {}

    /**
     * Amazon availability checking (via the shared {@link Cloakbrowser}). Disabled by default so the
     * app runs without a browser container.
     */
    public record Amazon(
            @DefaultValue("false") boolean enabled,
            /**
             * A product counts as "available" only if its earliest delivery date is within this many
             * days from now — guards against "in stock but ships in 1-2 months" listings.
             */
            @DefaultValue("5") int maxDeliveryDays,
            /** If true, only count it as available when Prime/fast shipping is offered. */
            @DefaultValue("false") boolean requirePrime,
            /** Randomized poll interval lower bound (ms). Amazon runs on its own parallel worker. */
            @DefaultValue("45000") long minIntervalMs,
            /** Randomized poll interval upper bound (ms). */
            @DefaultValue("75000") long maxIntervalMs,
            /** First Amazon poll this many ms after startup. */
            @DefaultValue("35000") long initialDelayMs,
            /** Product → Amazon product URL (dp/ASIN). Products without a URL are skipped. */
            @DefaultValue Map<Product, String> products
    ) {}

    /**
     * Lidl.de availability checking (via the shared {@link Cloakbrowser}). Only the PortaSplit Cool
     * is sold there. Disabled by default.
     */
    public record Lidl(
            @DefaultValue("false") boolean enabled,
            /** Lidl product page URL for the PortaSplit Cool. */
            @DefaultValue("https://www.lidl.de/p/midea-porta-split-cool/p100407988") String url,
            @DefaultValue("45000") long minIntervalMs,
            @DefaultValue("75000") long maxIntervalMs,
            @DefaultValue("45000") long initialDelayMs
    ) {}

    /**
     * kleinanzeigen.de search monitoring (via the shared {@link Cloakbrowser}). Unlike the other
     * sources this is not an availability state but a "new offer" watcher: a search-results URL is
     * scraped and every freshly-posted listing (younger than {@link #freshnessMinutes()}) triggers a
     * one-off Telegram notification. Each listing is de-duplicated by its ad id (a small bounded ring
     * of recently-notified ids) so it is reported at most once. Disabled by default.
     */
    public record Kleinanzeigen(
            @DefaultValue("false") boolean enabled,
            /** Search-results URL (already filtered by region/price); the newest offer on it is watched. */
            @DefaultValue("")
            String url,
            /**
             * A listing counts as a "new offer" only if it was posted within this many minutes of the
             * poll. Kept slightly above the poll interval (kleinanzeigen only exposes minute-precision
             * timestamps, and polling is jittered) so a just-posted ad is never missed; de-duplication
             * by ad id guarantees it is still notified at most once.
             */
            @DefaultValue("3") int freshnessMinutes,
            @DefaultValue("45000") long minIntervalMs,
            @DefaultValue("75000") long maxIntervalMs,
            @DefaultValue("25000") long initialDelayMs
    ) {}
}
