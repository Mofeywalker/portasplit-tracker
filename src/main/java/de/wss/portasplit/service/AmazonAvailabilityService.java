package de.wss.portasplit.service;

import de.wss.portasplit.amazon.AmazonClient;
import de.wss.portasplit.amazon.AmazonProductResult;
import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.jobs.JobLogger;
import de.wss.portasplit.domain.Product;
import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.domain.ShopSource;
import de.wss.portasplit.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Checks Amazon availability for the configured products by scraping their product pages via a
 * CloakBrowser stealth browser. A product only counts as "available" when it is in stock <em>and</em>
 * actually shippable within {@code app.amazon.max-delivery-days} (optionally Prime), so a listing
 * that is technically buyable but ships in weeks does not trigger a false alarm.
 *
 * <p>Scraping happens outside any transaction; each product result is persisted independently via
 * {@link AvailabilityReconciler#reconcileInTx}.
 */
@Service
public class AmazonAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AmazonAvailabilityService.class);
    private static final DateTimeFormatter DELIVERY_FMT =
            DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN);

    private final AmazonClient client;
    private final ShopRepository shopRepository;
    private final AvailabilityReconciler reconciler;
    private final AppProperties props;
    private final JobLogger jobLog;

    public AmazonAvailabilityService(AmazonClient client,
                                     ShopRepository shopRepository,
                                     AvailabilityReconciler reconciler,
                                     AppProperties props,
                                     JobLogger jobLog) {
        this.client = client;
        this.shopRepository = shopRepository;
        this.reconciler = reconciler;
        this.props = props;
        this.jobLog = jobLog;
    }

    /** Config default for this source; the effective on/off (incl. the runtime override) is owned by
     *  {@code CheckJobService.enabled(AMAZON)}, which gates enqueuing. */
    public boolean enabled() {
        return props.amazon().enabled();
    }

    /**
     * Runs one Amazon poll over all configured product URLs. Synchronized to never overlap. Whether
     * this runs at all is decided upstream by the job queue (a disabled source is never enqueued), so
     * no enabled-check is needed here.
     */
    public synchronized ScrapeCheckResult runCheck() {
        Instant runAt = Instant.now();
        Map<Product, String> urls = props.amazon().products();
        if (urls == null || urls.isEmpty()) {
            return ScrapeCheckResult.skipped(runAt, "Keine Amazon-Produkt-URLs konfiguriert");
        }
        Long shopId = amazonShopId();
        if (shopId == null) {
            log.warn("Amazon check enabled but no enabled shop with source=AMAZON found");
            return ScrapeCheckResult.skipped(runAt, "Kein Amazon-Shop konfiguriert");
        }

        long start = System.currentTimeMillis();
        int scanned = 0;
        int available = 0;
        List<String> errors = new ArrayList<>();

        for (Map.Entry<Product, String> e : urls.entrySet()) {
            Product product = e.getKey();
            String url = e.getValue();
            if (url == null || url.isBlank()) {
                continue;
            }
            scanned++;
            jobLog.debug("Amazon · prüfe {} ({})", product.displayName(), url.trim());
            AmazonProductResult result = client.scrape(url.trim());
            if (!result.ok()) {
                errors.add(product.displayName() + ": " + result.error());
                jobLog.warn("Amazon · {}: {}", product.displayName(), result.error());
            } else {
                jobLog.info("Amazon · {}: auf Lager={}, Preis={}, früheste Lieferung={}, Prime={}",
                        product.displayName(), result.inStock(), result.price(),
                        result.earliestDelivery(), result.prime());
            }
            AvailabilitySnapshot snap = toSnapshot(result);
            try {
                if (reconciler.reconcileInTx(shopId, product, snap, runAt)) {
                    available++;
                }
            } catch (Exception ex) {
                log.error("Persisting Amazon result for {} failed: {}", product.displayName(), ex.getMessage());
                errors.add(product.displayName() + ": " + ex.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Amazon check done in {} ms: {} product(s) scanned, {} available, {} error(s)",
                duration, scanned, available, errors.size());
        return new ScrapeCheckResult(runAt, true, scanned, available, duration, errors);
    }

    private Long amazonShopId() {
        return shopRepository.findByEnabledTrueAndSource(ShopSource.AMAZON).stream()
                .map(Shop::getId)
                .findFirst()
                .orElse(null);
    }

    /** Maps an Amazon scrape result to a source-neutral snapshot with the strict availability rule. */
    private AvailabilitySnapshot toSnapshot(AmazonProductResult r) {
        if (!r.ok()) {
            return AvailabilitySnapshot.notObserved();
        }
        LocalDate today = LocalDate.now();
        int maxDays = props.amazon().maxDeliveryDays();
        boolean requirePrime = props.amazon().requirePrime();

        boolean shippableSoon = r.earliestDelivery() != null
                && !r.earliestDelivery().isAfter(today.plusDays(maxDays));
        boolean available = r.inStock() && shippableSoon && (!requirePrime || r.prime());

        String note = buildNote(r, available);
        return new AvailabilitySnapshot(true, true, available, null, r.price(), r.url(),
                System.currentTimeMillis(), note);
    }

    private static String buildNote(AmazonProductResult r, boolean available) {
        String date = r.earliestDelivery() != null ? DELIVERY_FMT.format(r.earliestDelivery()) : null;
        if (!r.inStock()) {
            return "Nicht auf Lager";
        }
        if (available) {
            return (r.prime() ? "Prime · " : "") + "Lieferung bis " + date;
        }
        if (date != null) {
            return "Auf Lager · Lieferung erst " + date;
        }
        return "Auf Lager · Lieferzeit unklar";
    }
}
