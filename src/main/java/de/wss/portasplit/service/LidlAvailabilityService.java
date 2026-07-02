package de.wss.portasplit.service;

import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.domain.Product;
import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.domain.ShopSource;
import de.wss.portasplit.jobs.JobLogger;
import de.wss.portasplit.lidl.LidlClient;
import de.wss.portasplit.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks Lidl.de availability for the PortaSplit Cool via the CloakBrowser. Lidl only sells the Cool
 * variant, so a single product URL is scraped. Scraping happens outside any transaction; the result
 * is persisted via {@link AvailabilityReconciler#reconcileInTx}.
 */
@Service
public class LidlAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(LidlAvailabilityService.class);

    private final LidlClient client;
    private final ShopRepository shopRepository;
    private final AvailabilityReconciler reconciler;
    private final AppProperties props;
    private final JobLogger jobLog;

    public LidlAvailabilityService(LidlClient client,
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
     *  {@code CheckJobService.enabled(LIDL)}, which gates enqueuing. */
    public boolean enabled() {
        return props.lidl().enabled();
    }

    /**
     * Runs one Lidl poll for the PortaSplit Cool. Synchronized to never overlap. Whether this runs at
     * all is decided upstream by the job queue (a disabled source is never enqueued).
     */
    public synchronized ScrapeCheckResult runCheck() {
        Instant runAt = Instant.now();
        String url = props.lidl().url();
        if (url == null || url.isBlank()) {
            return ScrapeCheckResult.skipped(runAt, "Keine Lidl-Produkt-URL konfiguriert");
        }
        Long shopId = lidlShopId();
        if (shopId == null) {
            log.warn("Lidl check enabled but no enabled shop with source=LIDL found");
            return ScrapeCheckResult.skipped(runAt, "Kein Lidl-Shop konfiguriert");
        }

        long start = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        int available = 0;

        AvailabilitySnapshot snap = client.scrape(url.trim());
        if (!snap.observed()) {
            errors.add("PortaSplit Cool: Seite nicht ausgelesen");
            jobLog.warn("Lidl · PortaSplit Cool: kein eindeutiges Signal - Seite nicht ausgelesen");
        } else {
            jobLog.info("Lidl · PortaSplit Cool: {} ({})",
                    snap.available() ? "verfügbar" : "nicht verfügbar", snap.note());
        }
        try {
            if (reconciler.reconcileInTx(shopId, Product.PORTASPLIT_COOL, snap, runAt)) {
                available++;
            }
        } catch (Exception ex) {
            log.error("Persisting Lidl result failed: {}", ex.getMessage());
            errors.add("PortaSplit Cool: " + ex.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Lidl check done in {} ms: available={}, {} error(s)", duration, available, errors.size());
        return new ScrapeCheckResult(runAt, true, 1, available, duration, errors);
    }

    private Long lidlShopId() {
        return shopRepository.findByEnabledTrueAndSource(ShopSource.LIDL).stream()
                .map(Shop::getId)
                .findFirst()
                .orElse(null);
    }
}
