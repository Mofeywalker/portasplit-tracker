package de.wss.portasplit.chains;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the /api/purchasability parsing that tells a stocked Bauhaus store apart from a reservable
 * one. The availability-detail API marks a store "AVAILABLE" even for freight items that cannot be
 * reserved, so this STORE-kind {@code purchasable} flag is the ground truth. Response shapes below are
 * taken verbatim from live bauhaus.info responses.
 */
class BauhausStockClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void inStockButNotReservableIsFalse() {
        // Store has 3 units (code SANR = Store Available, Not Reservable) but cannot be reserved.
        String body = "{\"results\":["
                + "{\"amount\":0,\"code\":\"OOOS\",\"kind\":\"ONLINE\",\"product\":\"31934233\",\"purchasable\":false},"
                + "{\"amount\":3,\"code\":\"SANR\",\"kind\":\"STORE\",\"product\":\"31934233\",\"purchasable\":false}"
                + "]}";
        assertThat(BauhausStockClient.parsePurchasable(mapper, body)).isFalse();
    }

    @Test
    void reservableStoreIsTrue() {
        String body = "{\"results\":["
                + "{\"amount\":0,\"code\":\"OOOS\",\"kind\":\"ONLINE\",\"product\":\"1\",\"purchasable\":false},"
                + "{\"amount\":2,\"code\":\"SIS\",\"kind\":\"STORE\",\"product\":\"1\",\"purchasable\":true}"
                + "]}";
        assertThat(BauhausStockClient.parsePurchasable(mapper, body)).isTrue();
    }

    @Test
    void outOfStockStoreIsFalse() {
        String body = "{\"results\":["
                + "{\"amount\":0,\"code\":\"SOOS\",\"kind\":\"STORE\",\"product\":\"1\",\"purchasable\":false}"
                + "]}";
        assertThat(BauhausStockClient.parsePurchasable(mapper, body)).isFalse();
    }

    @Test
    void missingOrUnparseableBodyIsNull() {
        assertThat(BauhausStockClient.parsePurchasable(mapper, null)).isNull();
        assertThat(BauhausStockClient.parsePurchasable(mapper, "")).isNull();
        assertThat(BauhausStockClient.parsePurchasable(mapper, "not json")).isNull();
        // No STORE-kind result → cannot decide reservability.
        assertThat(BauhausStockClient.parsePurchasable(mapper,
                "{\"results\":[{\"kind\":\"ONLINE\",\"purchasable\":true}]}")).isNull();
    }
}
