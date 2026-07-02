package de.wss.portasplit.chains;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.wss.portasplit.service.AvailabilitySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the shared schema.org JSON-LD online-availability parser used by every chain's checkOnline. */
class ChainJsonLdTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static String pdp(String sku, String availability, String price) {
        return "<html><head>"
                + "<script type=\"application/ld+json\">{\"@type\":\"BreadcrumbList\"}</script>"
                + "<script type=\"application/ld+json\">"
                + "{\"@context\":\"https://schema.org\",\"@type\":\"Product\",\"sku\":\"" + sku + "\","
                + "\"offers\":{\"@type\":\"Offer\",\"price\":" + price + ",\"priceCurrency\":\"EUR\","
                + "\"availability\":\"" + availability + "\"}}"
                + "</script></head><body>x</body></html>";
    }

    @Test
    void inStockOfferIsAvailableWithPrice() {
        ChainJsonLd.Offer offer = ChainJsonLd.parseHtml(mapper, pdp("8620890", "http://schema.org/InStock", "799.99"), "8620890");
        assertThat(offer).isNotNull();
        assertThat(offer.available()).isTrue();
        assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("799.99"));
    }

    @Test
    void outOfStockIsNotAvailable() {
        ChainJsonLd.Offer offer = ChainJsonLd.parseHtml(mapper, pdp("1", "https://schema.org/OutOfStock", "99.0"), "1");
        assertThat(offer).isNotNull();
        assertThat(offer.available()).isFalse();
    }

    @Test
    void inStoreOnlyIsNotAvailableOnline() {
        // Regression: "InStoreOnly" must not be read as available just because it is not OutOfStock.
        ChainJsonLd.Offer offer = ChainJsonLd.parseHtml(mapper, pdp("1425543", "https://schema.org/InStoreOnly", "849.0"), "1425543");
        assertThat(offer).isNotNull();
        assertThat(offer.available()).isFalse();
    }

    @Test
    void prefersProductMatchingSkuOverOtherProducts() {
        String html = "<html>"
                + "<script type=\"application/ld+json\">{\"@type\":\"Product\",\"sku\":\"ACCESSORY\","
                + "\"offers\":{\"@type\":\"Offer\",\"availability\":\"https://schema.org/OutOfStock\",\"price\":5}}</script>"
                + "<script type=\"application/ld+json\">{\"@type\":\"Product\",\"sku\":\"MAIN\","
                + "\"offers\":{\"@type\":\"Offer\",\"availability\":\"https://schema.org/InStock\",\"price\":799.99}}</script>"
                + "</html>";
        ChainJsonLd.Offer offer = ChainJsonLd.parseHtml(mapper, html, "MAIN");
        assertThat(offer.available()).isTrue();
        assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("799.99"));
    }

    @Test
    void handlesGraphWrapperAndGermanPrice() {
        String html = "<script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@graph\":["
                + "{\"@type\":\"WebPage\"},"
                + "{\"@type\":\"Product\",\"sku\":\"X\",\"offers\":{\"@type\":\"Offer\","
                + "\"availability\":\"https://schema.org/InStock\",\"price\":\"1.299,00\"}}]}</script>";
        ChainJsonLd.Offer offer = ChainJsonLd.parseHtml(mapper, html, "X");
        assertThat(offer.available()).isTrue();
        assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("1299.00"));
    }

    @Test
    void noProductOfferYieldsNullOffer() {
        String html = "<html><script type=\"application/ld+json\">{\"@type\":\"BreadcrumbList\"}</script></html>";
        assertThat(ChainJsonLd.parseHtml(mapper, html, "1")).isNull();
        assertThat(ChainJsonLd.parseHtml(mapper, "", "1")).isNull();
        assertThat(ChainJsonLd.parseBlocks(mapper, List.of(), "1")).isNull();
    }

    @Test
    void parseBlocksMirrorsHtmlPath() {
        String block = "{\"@type\":\"Product\",\"sku\":\"B\",\"offers\":{\"@type\":\"Offer\","
                + "\"availability\":\"https://schema.org/LimitedAvailability\",\"price\":42}}";
        ChainJsonLd.Offer offer = ChainJsonLd.parseBlocks(mapper, List.of(block), "B");
        assertThat(offer.available()).isTrue();   // LimitedAvailability counts as orderable online
    }

    @Test
    void snapshotForNullOfferIsNotListedAndUnavailable() {
        AvailabilitySnapshot snap = ChainJsonLd.toSnapshot(null, "https://x/p/1");
        assertThat(snap.observed()).isTrue();
        assertThat(snap.available()).isFalse();
        assertThat(snap.note()).isEqualTo("online nicht gelistet");
    }

    @Test
    void snapshotForAvailableOfferCarriesPriceAndNote() {
        AvailabilitySnapshot snap = ChainJsonLd.toSnapshot(
                new ChainJsonLd.Offer(true, new BigDecimal("799.99"), "InStock"), "https://x/p/1");
        assertThat(snap.available()).isTrue();
        assertThat(snap.price()).isEqualByComparingTo(new BigDecimal("799.99"));
        assertThat(snap.note()).contains("online lieferbar").contains("799.99");
    }
}
