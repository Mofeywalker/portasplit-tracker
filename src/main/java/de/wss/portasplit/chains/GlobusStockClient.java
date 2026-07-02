package de.wss.portasplit.chains;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.domain.Product;
import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.domain.ShopSource;
import de.wss.portasplit.jobs.JobLogger;
import de.wss.portasplit.jobs.JobType;
import de.wss.portasplit.service.AvailabilitySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Globus Baumarkt per-branch availability via the public {@code /ianeo/} JSON routes (Shopware 6).
 * For each (product, branch PLZ) it asks {@code /ianeo/get-closest-stores-and-availability/{art}/{plz}}
 * and matches the branch among the nearest stores by PLZ. In-stock ⇔ {@code stock > 0} (the clamped,
 * reservable count; the raw {@code storeStock} can be negative and must not be used directly).
 */
@Component
public class GlobusStockClient implements ChainStockClient {

    private static final Logger log = LoggerFactory.getLogger(GlobusStockClient.class);

    private static final String AVAIL_URL =
            "https://www.globus-baumarkt.de/ianeo/get-closest-stores-and-availability/%s/%s";

    private static final Map<Product, String> ARTICLES = Map.of(
            Product.PORTASPLIT, "0694600235",
            Product.PORTASPLIT_COOL, "0694600251");
    private static final Map<Product, String> PRODUCT_URLS = Map.of(
            Product.PORTASPLIT,
            "https://www.globus-baumarkt.de/p/midea-portasplit-mobile-split-klimaanlage-12000-btu-heiz-kuehlfunktion-0694600235/",
            Product.PORTASPLIT_COOL,
            "https://www.globus-baumarkt.de/p/midea-portasplit-mobile-split-klimaanlage-cool-8000-btu-kuehlfunktion-0694600251/");

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final AppProperties props;
    private final JobLogger jobLog;

    public GlobusStockClient(RestClient restClient, ObjectMapper mapper, AppProperties props, JobLogger jobLog) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.props = props;
        this.jobLog = jobLog;
    }

    @Override
    public JobType jobType() {
        return JobType.GLOBUS;
    }

    @Override
    public ShopSource source() {
        return ShopSource.GLOBUS;
    }

    @Override
    public AppProperties.Chain config() {
        return props.globus();
    }

    @Override
    public List<ChainReading> check(List<Shop> branches, List<String> errors) {
        List<ChainReading> readings = new ArrayList<>();
        for (Map.Entry<Product, String> e : ARTICLES.entrySet()) {
            Product product = e.getKey();
            String article = e.getValue();
            String url = PRODUCT_URLS.get(product);
            for (Shop branch : branches) {
                String plz = branch.getPlz();
                if (plz == null || plz.isBlank()) {
                    continue;
                }
                try {
                    JsonNode resp = mapper.readTree(
                            ChainHttp.get(restClient, String.format(AVAIL_URL, article, plz.trim())));
                    JsonNode store = matchStore(resp.path("data"), branch);
                    if (store == null) {
                        readings.add(new ChainReading(branch.getId(), product, new AvailabilitySnapshot(
                                true, false, false, 0, null, url, System.currentTimeMillis(), "nicht im Markt")));
                    } else {
                        int stock = store.path("stock").asInt(0);
                        boolean available = stock > 0;
                        readings.add(new ChainReading(branch.getId(), product, new AvailabilitySnapshot(
                                true, true, available, stock, null, url, System.currentTimeMillis(),
                                available ? stock + " Stück im Markt" : "nicht im Markt")));
                    }
                } catch (Exception ex) {
                    log.debug("Globus availability failed for {} @ {}: {}", article, plz, ex.getMessage());
                    errors.add(branch.getName() + " / " + product.displayName() + ": " + ex.getMessage());
                    readings.add(new ChainReading(branch.getId(), product, AvailabilitySnapshot.notObserved()));
                }
            }
            jobLog.debug("Globus · {} über {} Filialen geprüft", product.displayName(), branches.size());
        }
        return readings;
    }

    /**
     * Online availability of Globus Baumarkt's own shop, from the product page's schema.org JSON-LD
     * offer (the {@code /ianeo/} store API only reports per-market pickup stock, not online delivery).
     * A delisted article answers 404 with a page that carries no offer → reported as "nicht gelistet".
     */
    @Override
    public List<ChainReading> checkOnline(Shop onlineShop, List<String> errors) {
        List<ChainReading> readings = new ArrayList<>();
        for (Map.Entry<Product, String> e : ARTICLES.entrySet()) {
            Product product = e.getKey();
            String url = PRODUCT_URLS.get(product);
            try {
                String html = ChainHttp.getHtml(restClient, url);
                ChainJsonLd.Offer offer = ChainJsonLd.parseHtml(mapper, html, e.getValue());
                readings.add(ChainJsonLd.onlineReading(onlineShop.getId(), product, offer, url));
            } catch (Exception ex) {
                log.debug("Globus online availability failed for {}: {}", e.getValue(), ex.getMessage());
                errors.add(onlineShop.getName() + " / " + product.displayName() + ": " + ex.getMessage());
                readings.add(new ChainReading(onlineShop.getId(), product, AvailabilitySnapshot.notObserved()));
            }
        }
        jobLog.debug("Globus · Online-Verfügbarkeit (PDP) für {} Artikel geprüft", ARTICLES.size());
        return readings;
    }

    private JsonNode matchStore(JsonNode data, Shop branch) {
        if (!data.isArray()) {
            return null;
        }
        for (JsonNode s : data) {
            if (s.path("zip").asText("").equals(branch.getPlz())) {
                return s;
            }
        }
        return null;
    }
}
