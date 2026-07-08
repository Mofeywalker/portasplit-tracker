package de.wss.portasplit.chains;

import de.wss.portasplit.amazon.CloakBrowserClient;
import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.domain.Product;
import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.domain.ShopSource;
import de.wss.portasplit.jobs.JobLogger;
import de.wss.portasplit.jobs.JobType;
import de.wss.portasplit.service.AvailabilitySnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hornbach availability. Hornbach sits behind a Fastly bot challenge (CloakBrowser passes it) and serves
 * a React/Apollo storefront whose data is in an embedded Apollo state. The Midea PortaSplit is currently
 * <em>not listed</em> on hornbach.de (seasonal - live-confirmed "0-Treffer"), so there is no article /
 * PDP to query per market yet. This worker therefore watches the search listing: while the product is
 * unlisted it reports every branch as not available ("nicht gelistet"); once Hornbach relists it
 * ({@code articleCount > 0}) it flags that prominently so the per-market GraphQL check can be added.
 */
@Component
public class HornbachStockClient implements ChainStockClient {

    private static final int SEED = 8282;
    private static final String SEARCH_PATH = "/s/Midea%20PortaSplit";
    private static final String SEARCH_URL = "https://www.hornbach.de" + SEARCH_PATH;
    private static final Pattern ARTICLE_COUNT = Pattern.compile("\"articleCount\":\\s*(\\d+)");

    /** A single CloakBrowser search fetch is reused by check() + checkOnline() within one run. */
    private static final long LISTING_TTL_MS = 60_000;

    private final AppProperties props;
    private final CloakBrowserClient cloak;
    private final JobLogger jobLog;

    private volatile Listing cachedListing;
    private volatile long cachedListingAt;

    public HornbachStockClient(AppProperties props, CloakBrowserClient cloak, JobLogger jobLog) {
        this.props = props;
        this.cloak = cloak;
        this.jobLog = jobLog;
    }

    @Override
    public JobType jobType() {
        return JobType.HORNBACH;
    }

    @Override
    public ShopSource source() {
        return ShopSource.HORNBACH;
    }

    @Override
    public AppProperties.Chain config() {
        return props.hornbach();
    }

    @Override
    public List<ChainReading> check(List<Shop> branches, List<String> errors) {
        Listing listing = listing(errors);
        if (listing == null) {
            return List.of();
        }
        List<ChainReading> readings = new ArrayList<>();
        for (Shop branch : branches) {
            for (Product product : Product.values()) {
                readings.add(new ChainReading(branch.getId(), product, listing.toSnapshot()));
            }
        }
        return readings;
    }

    /**
     * Online shop. Hornbach has no per-article online stock to query while the product is unlisted, so
     * the online shop tracks the same listing signal as the branches (available stays false; a relisting
     * surfaces via the note/warning so a real per-market online check can be added).
     */
    @Override
    public List<ChainReading> checkOnline(Shop onlineShop, List<String> errors) {
        Listing listing = listing(errors);
        if (listing == null) {
            return List.of();
        }
        List<ChainReading> readings = new ArrayList<>();
        for (Product product : Product.values()) {
            readings.add(new ChainReading(onlineShop.getId(), product, listing.toSnapshot()));
        }
        return readings;
    }

    /** Fetches (and briefly caches) the Hornbach search-listing state; null if the page is unreachable. */
    private Listing listing(List<String> errors) {
        Listing cached = cachedListing;
        if (cached != null && System.currentTimeMillis() - cachedListingAt < LISTING_TTL_MS) {
            return cached;
        }
        List<CloakBrowserClient.InPageResponse> res = cloak.behindCloudflare(SEED, SEARCH_URL,
                List.of(new CloakBrowserClient.InPageReq("GET", SEARCH_PATH, Map.of("Accept", "text/html"))));
        String html = res.isEmpty() ? null : res.get(0).body();
        if (html == null || html.isBlank()) {
            errors.add("Hornbach: Suchseite nicht erreichbar");
            return null;
        }

        int articleCount = -1;
        Matcher m = ARTICLE_COUNT.matcher(html);
        if (m.find()) {
            articleCount = Integer.parseInt(m.group(1));
        }
        boolean listed = articleCount > 0;
        String note;
        if (listed) {
            note = "bei Hornbach wieder gelistet (" + articleCount + " Treffer) - Markt-Bestand prüfen";
            jobLog.warn("Hornbach: Midea PortaSplit ist WIEDER GELISTET ({} Treffer) - per-Markt-Worker ergänzen!",
                    articleCount);
        } else {
            note = "bei Hornbach nicht gelistet";
            jobLog.info("Hornbach: Midea PortaSplit aktuell nicht gelistet (0 Treffer)");
        }
        Listing listing = new Listing(note);
        cachedListing = listing;
        cachedListingAt = System.currentTimeMillis();
        return listing;
    }

    /** Listing watcher result. Availability stays false (listed ≠ in stock); the note carries the state. */
    private record Listing(String note) {
        AvailabilitySnapshot toSnapshot() {
            return new AvailabilitySnapshot(true, true, false, 0, null, SEARCH_URL,
                    System.currentTimeMillis(), note);
        }
    }
}
