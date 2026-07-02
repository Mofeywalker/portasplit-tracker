package de.wss.portasplit.chains;

import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.domain.ShopSource;
import de.wss.portasplit.jobs.JobType;

import java.util.List;

/**
 * One chain's direct per-branch availability checker (OBI, toom, …). Goes straight to the chain's own
 * site/API. Implementations map our branch list to the chain's internal store ids and return an
 * availability reading per (shop, product). Never throws — recoverable problems are appended to
 * {@code errors} and the affected reading is omitted or marked not-observed.
 */
public interface ChainStockClient {

    String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    JobType jobType();

    ShopSource source();

    AppProperties.Chain config();

    List<ChainReading> check(List<Shop> branches, List<String> errors);

    /**
     * Checks the chain's own online shop (nationwide delivery), called once per enabled
     * {@code onlineOnly} shop routed to this source. Separate from {@link #check} because the online
     * shop has no PLZ and uses the chain's delivery/online signal rather than per-branch pickup stock.
     * Default: not implemented — returns no reading, so the online shop keeps its last known state.
     * Never throws — recoverable problems go into {@code errors} and the affected reading is omitted
     * or marked not-observed.
     */
    default List<ChainReading> checkOnline(Shop onlineShop, List<String> errors) {
        return List.of();
    }

    /** Non-null = this chain is registered but not yet operational; the reason is shown as SKIPPED. */
    default String unsupportedReason() {
        return null;
    }
}
