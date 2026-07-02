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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OBI per-branch availability via OBI's public JSON API (CloudFront; only needs a browser User-Agent).
 * For each (product, branch) it asks {@code /api/pdp/v1/availability/{article}?postalCode=}, then
 * matches the branch among the returned nearest reserve-and-collect stores by PLZ. Verified live.
 */
@Component
public class ObiStockClient implements ChainStockClient {

    private static final Logger log = LoggerFactory.getLogger(ObiStockClient.class);

    private static final String AVAIL_URL =
            "https://www.obi.de/api/pdp/v1/availability/%s?postalCode=%s&quantity=1&lang=de-DE";
    private static final String PRODUCT_URL = "https://www.obi.de/p/%s/";

    private static final Map<Product, String> ARTICLES = Map.of(
            Product.PORTASPLIT, "8620890",
            Product.PORTASPLIT_COOL, "2191158911022");

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final AppProperties props;
    private final JobLogger jobLog;

    public ObiStockClient(RestClient restClient, ObjectMapper mapper, AppProperties props, JobLogger jobLog) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.props = props;
        this.jobLog = jobLog;
    }

    @Override
    public JobType jobType() {
        return JobType.OBI;
    }

    @Override
    public ShopSource source() {
        return ShopSource.OBI;
    }

    @Override
    public AppProperties.Chain config() {
        return props.obi();
    }

    @Override
    public List<ChainReading> check(List<Shop> branches, List<String> errors) {
        List<ChainReading> readings = new ArrayList<>();
        for (Map.Entry<Product, String> e : ARTICLES.entrySet()) {
            Product product = e.getKey();
            String article = e.getValue();
            String productUrl = String.format(PRODUCT_URL, article);
            for (Shop branch : branches) {
                String plz = branch.getPlz();
                if (plz == null || plz.isBlank()) {
                    continue;
                }
                try {
                    String json = ChainHttp.get(restClient, String.format(AVAIL_URL, article, plz.trim()));
                    JsonNode stores = mapper.readTree(json).path("pickupStores");
                    JsonNode store = matchStore(stores, branch);
                    if (store == null) {
                        readings.add(new ChainReading(branch.getId(), product, new AvailabilitySnapshot(
                                true, false, false, 0, null, productUrl, System.currentTimeMillis(),
                                "nicht im Markt")));
                    } else {
                        int qty = store.path("availableQuantity").asInt(0);
                        BigDecimal price = store.has("price") && !store.get("price").isNull()
                                ? new BigDecimal(store.get("price").asText()) : null;
                        boolean available = qty > 0;
                        readings.add(new ChainReading(branch.getId(), product, new AvailabilitySnapshot(
                                true, true, available, qty, price, productUrl, System.currentTimeMillis(),
                                available ? qty + " Stück im Markt" : "nicht im Markt")));
                    }
                } catch (Exception ex) {
                    log.debug("OBI availability failed for {} @ {}: {}", article, plz, ex.getMessage());
                    errors.add(branch.getName() + " / " + product.displayName() + ": " + ex.getMessage());
                    readings.add(new ChainReading(branch.getId(), product, AvailabilitySnapshot.notObserved()));
                }
            }
            jobLog.debug("OBI · {} über {} Filialen geprüft", product.displayName(), branches.size());
        }
        return readings;
    }

    /**
     * Online (nationwide) availability of OBI's own shop, read from the product page's schema.org
     * JSON-LD {@code Offer.availability}. This is the only reliable signal for large items: the JSON
     * availability API's {@code deliveryDataPerSeller} only lists <em>parcel</em> delivery, so a freight
     * ("Spedition") item like the PortaSplit shows up there as empty even while it is orderable online.
     * The PDP offer reflects the real "In den Warenkorb"/home-delivery state and carries the price.
     */
    @Override
    public List<ChainReading> checkOnline(Shop onlineShop, List<String> errors) {
        List<ChainReading> readings = new ArrayList<>();
        for (Map.Entry<Product, String> e : ARTICLES.entrySet()) {
            Product product = e.getKey();
            String article = e.getValue();
            String productUrl = String.format(PRODUCT_URL, article);
            try {
                String html = ChainHttp.getHtml(restClient, productUrl);
                ChainJsonLd.Offer offer = ChainJsonLd.parseHtml(mapper, html, article);
                readings.add(ChainJsonLd.onlineReading(onlineShop.getId(), product, offer, productUrl));
            } catch (Exception ex) {
                log.debug("OBI online availability failed for {}: {}", article, ex.getMessage());
                errors.add(onlineShop.getName() + " / " + product.displayName() + ": " + ex.getMessage());
                readings.add(new ChainReading(onlineShop.getId(), product, AvailabilitySnapshot.notObserved()));
            }
        }
        jobLog.debug("OBI · Online-Verfügbarkeit (PDP) für {} Artikel geprüft", ARTICLES.size());
        return readings;
    }

    /** Finds the branch in OBI's nearest-stores response by PLZ (then narrowed by city/street). */
    private JsonNode matchStore(JsonNode stores, Shop branch) {
        if (!stores.isArray()) {
            return null;
        }
        List<JsonNode> plzMatches = new ArrayList<>();
        for (JsonNode s : stores) {
            if (s.path("pickupStoreInfo").path("postalCode").asText("").equals(branch.getPlz())) {
                plzMatches.add(s);
            }
        }
        if (plzMatches.isEmpty()) {
            return null;
        }
        if (plzMatches.size() == 1) {
            return plzMatches.get(0);
        }
        String street = branch.getStreet() == null ? "" : branch.getStreet().toLowerCase();
        for (JsonNode s : plzMatches) {
            String sStreet = s.path("pickupStoreInfo").path("street").asText("").toLowerCase();
            if (!sStreet.isBlank() && (street.contains(sStreet) || sStreet.contains(street.split(" ")[0]))) {
                return s;
            }
        }
        return plzMatches.get(0);
    }
}
