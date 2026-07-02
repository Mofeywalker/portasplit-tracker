package de.wss.portasplit.kleinanzeigen;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KleinanzeigenParsingTest {

    // Tuesday, 23.06.2026, 14:30 local Berlin time.
    private static final ZonedDateTime NOW =
            ZonedDateTime.of(2026, 6, 23, 14, 30, 0, 0, KleinanzeigenClient.BERLIN);

    private static long epoch(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, KleinanzeigenClient.BERLIN).toInstant().toEpochMilli();
    }

    @Test
    void parsesHeuteWithTime() {
        assertEquals(epoch(2026, 6, 23, 14, 23),
                KleinanzeigenClient.parsePostedEpochMillis("Heute, 14:23", NOW));
    }

    @Test
    void parsesGesternWithTime() {
        assertEquals(epoch(2026, 6, 22, 9, 5),
                KleinanzeigenClient.parsePostedEpochMillis("Gestern, 09:05", NOW));
    }

    @Test
    void parsesExplicitDate() {
        assertEquals(epoch(2025, 6, 22, 0, 0),
                KleinanzeigenClient.parsePostedEpochMillis("22.06.2025", NOW));
    }

    @Test
    void heuteWithoutTimeFallsBackToMidnight() {
        assertEquals(epoch(2026, 6, 23, 0, 0),
                KleinanzeigenClient.parsePostedEpochMillis("Heute", NOW));
    }

    @Test
    void returnsNullForUnparseable() {
        assertNull(KleinanzeigenClient.parsePostedEpochMillis(null, NOW));
        assertNull(KleinanzeigenClient.parsePostedEpochMillis("", NOW));
        assertNull(KleinanzeigenClient.parsePostedEpochMillis("vor kurzem", NOW));
    }

    @Test
    void ignoresInvalidClockValues() {
        // 99:99 is not a valid time -> falls back to midnight of that day, not a crash.
        assertEquals(epoch(2026, 6, 23, 0, 0),
                KleinanzeigenClient.parsePostedEpochMillis("Heute, 99:99", NOW));
    }

    @Test
    void ageIsComputedAndNeverNegative() {
        // A "Heute, 14:23" ad is 7 minutes old at 14:30.
        Long posted = KleinanzeigenClient.parsePostedEpochMillis("Heute, 14:23", NOW);
        KleinanzeigenListing l = new KleinanzeigenListing(
                "1", "t", "u", null, null, null, "Heute, 14:23", posted, false);
        assertEquals(7 * 60, l.ageSeconds(NOW.toInstant()));

        // A listing posted "in the future" (clock skew) clamps to 0, never negative.
        Long future = KleinanzeigenClient.parsePostedEpochMillis("Heute, 14:31", NOW);
        KleinanzeigenListing skew = new KleinanzeigenListing(
                "2", "t", "u", null, null, null, "Heute, 14:31", future, false);
        assertEquals(0, skew.ageSeconds(NOW.toInstant()));

        // No timestamp -> effectively infinitely old (never "fresh").
        KleinanzeigenListing undated = new KleinanzeigenListing(
                "3", "t", "u", null, null, null, null, null, false);
        assertEquals(Long.MAX_VALUE, undated.ageSeconds(NOW.toInstant()));
    }

    @Test
    void parsesAndSortsListingsNewestFirst() {
        List<Map<String, Object>> items = List.of(
                item("100", "/s-anzeige/alt/100", "Alte Anzeige", "900 € VB", "56410 Montabaur", "22.06.2025", false),
                item("200", "/s-anzeige/neu/200", "Frische Anzeige", "950 €", "56412 Heiligenroth", "Heute, 14:28", false),
                item("300", "/s-anzeige/mittel/300", "Gestern", "800 € VB", "56424 Mogendorf", "Gestern, 18:00", false));

        List<KleinanzeigenListing> parsed = KleinanzeigenClient.parseListings(items, NOW);

        assertEquals(3, parsed.size());
        // Newest (Heute) first.
        assertEquals("200", parsed.get(0).adId());
        assertEquals("300", parsed.get(1).adId());
        assertEquals("100", parsed.get(2).adId());

        KleinanzeigenListing newest = parsed.get(0);
        assertEquals("Frische Anzeige", newest.title());
        assertEquals("https://www.kleinanzeigen.de/s-anzeige/neu/200", newest.url());
        assertEquals(0, new java.math.BigDecimal("950").compareTo(newest.price()));
        assertEquals(2 * 60, newest.ageSeconds(NOW.toInstant())); // 14:28 vs 14:30
    }

    @Test
    void derivesAdIdFromHrefWhenMissing() {
        List<Map<String, Object>> items = List.of(
                item(null, "/s-anzeige/midea-portasplit/2998877665-242-1212", "X", "950 €", "Montabaur", "Heute, 14:00", false));
        List<KleinanzeigenListing> parsed = KleinanzeigenClient.parseListings(items, NOW);
        assertEquals(1, parsed.size());
        assertEquals("2998877665", parsed.get(0).adId());
    }

    @Test
    void skipsItemsWithoutAnyIdentity() {
        List<Map<String, Object>> items = List.of(item(null, null, "no id no href", "1 €", "x", "Heute, 14:00", false));
        assertTrue(KleinanzeigenClient.parseListings(items, NOW).isEmpty());
    }

    @Test
    void handlesNonListInputGracefully() {
        assertNotNull(KleinanzeigenClient.parseListings(null, NOW));
        assertTrue(KleinanzeigenClient.parseListings(null, NOW).isEmpty());
        assertTrue(KleinanzeigenClient.parseListings("not a list", NOW).isEmpty());
    }

    private static Map<String, Object> item(String adId, String href, String title, String price,
                                            String location, String date, boolean top) {
        Map<String, Object> m = new HashMap<>();
        m.put("adId", adId);
        m.put("href", href);
        m.put("title", title);
        m.put("price", price);
        m.put("location", location);
        m.put("date", date);
        m.put("top", top);
        return m;
    }
}
