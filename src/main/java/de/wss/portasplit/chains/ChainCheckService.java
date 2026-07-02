package de.wss.portasplit.chains;

import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.jobs.JobLogger;
import de.wss.portasplit.jobs.JobType;
import de.wss.portasplit.repository.ShopRepository;
import de.wss.portasplit.service.AvailabilityReconciler;
import de.wss.portasplit.service.ScrapeCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the per-chain branch checkers ({@link ChainStockClient}s). Each chain is its own
 * {@link JobType}/worker; this service resolves the chain's enabled branches, delegates the actual
 * lookup to the chain client, then persists each reading via {@link AvailabilityReconciler}.
 */
@Service
public class ChainCheckService {

    private static final Logger log = LoggerFactory.getLogger(ChainCheckService.class);

    private final Map<JobType, ChainStockClient> clients = new EnumMap<>(JobType.class);
    private final ShopRepository shopRepository;
    private final AvailabilityReconciler reconciler;
    private final JobLogger jobLog;

    public ChainCheckService(List<ChainStockClient> chainClients,
                             ShopRepository shopRepository,
                             AvailabilityReconciler reconciler,
                             JobLogger jobLog) {
        for (ChainStockClient c : chainClients) {
            clients.put(c.jobType(), c);
        }
        this.shopRepository = shopRepository;
        this.reconciler = reconciler;
        this.jobLog = jobLog;
    }

    public Set<JobType> jobTypes() {
        return clients.keySet();
    }

    public boolean handles(JobType type) {
        return clients.containsKey(type);
    }

    public boolean enabled(JobType type) {
        ChainStockClient c = clients.get(type);
        return c != null && c.config().enabled();
    }

    public AppProperties.Chain config(JobType type) {
        return clients.get(type).config();
    }

    /** Runs one poll for a chain: map branches → store ids, query the chain, reconcile each reading. */
    public ScrapeCheckResult runCheck(JobType type) {
        Instant runAt = Instant.now();
        long start = System.currentTimeMillis();
        ChainStockClient client = clients.get(type);

        String unsupported = client.unsupportedReason();
        if (unsupported != null) {
            return ScrapeCheckResult.skipped(runAt, unsupported);
        }

        List<Shop> shops = shopRepository.findByEnabledTrueAndSource(client.source());
        if (shops.isEmpty()) {
            return ScrapeCheckResult.skipped(runAt, "Keine aktiven Filialen für " + type.label());
        }
        // Physical branches are looked up per PLZ; the chain's own online shop (onlineOnly, no PLZ) is
        // checked via the chain's delivery/online signal instead.
        List<Shop> branches = shops.stream().filter(s -> !s.isOnlineOnly()).toList();
        List<Shop> onlineShops = shops.stream().filter(Shop::isOnlineOnly).toList();

        jobLog.info("{} · {} Filiale(n){}, prüfe Bestand direkt bei der Kette…", type.label(), branches.size(),
                onlineShops.isEmpty() ? "" : " + " + onlineShops.size() + " Online-Shop");
        List<String> errors = new ArrayList<>();
        List<ChainReading> readings;
        try {
            readings = new ArrayList<>(client.check(branches, errors));
            for (Shop online : onlineShops) {
                readings.addAll(client.checkOnline(online, errors));
            }
        } catch (Exception e) {
            log.warn("{} check threw: {}", type, e.getMessage());
            return new ScrapeCheckResult(runAt, true, 0, 0,
                    System.currentTimeMillis() - start, List.of(e.getMessage() != null ? e.getMessage() : e.toString()));
        }

        int available = 0;
        for (ChainReading r : readings) {
            try {
                if (reconciler.reconcileInTx(r.shopId(), r.product(), r.snapshot(), runAt)) {
                    available++;
                }
            } catch (Exception e) {
                log.error("Persisting {} reading for shop {} failed: {}", type, r.shopId(), e.getMessage());
                errors.add("Shop " + r.shopId() + ": " + e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("{} check done in {} ms: {} reading(s), {} available, {} error(s)",
                type, duration, readings.size(), available, errors.size());
        return new ScrapeCheckResult(runAt, true, readings.size(), available, duration, errors);
    }
}
