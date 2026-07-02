package de.wss.portasplit.amazon;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AmazonParsingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22); // Monday

    @Test
    void parsesExplicitGermanDate() {
        assertEquals(LocalDate.of(2026, 6, 26),
                AmazonDeliveryParser.parseEarliest("GRATIS Lieferung Donnerstag, 26. Juni", TODAY));
    }

    @Test
    void parsesAbbreviatedMonth() {
        assertEquals(LocalDate.of(2026, 9, 9),
                AmazonDeliveryParser.parseEarliest("Lieferung Mittwoch, 9. Sept.", TODAY));
    }

    @Test
    void picksEarliestFromRange() {
        assertEquals(LocalDate.of(2026, 6, 25),
                AmazonDeliveryParser.parseEarliest("Lieferung Mittwoch, 25. Juni - Freitag, 27. Juni", TODAY));
    }

    @Test
    void handlesRelativeTomorrow() {
        assertEquals(TODAY.plusDays(1),
                AmazonDeliveryParser.parseEarliest("Lieferung morgen, 6:00 - 11:00", TODAY));
    }

    @Test
    void handlesUebermorgenNotMatchingMorgen() {
        assertEquals(TODAY.plusDays(2),
                AmazonDeliveryParser.parseEarliest("Lieferung übermorgen", TODAY));
    }

    @Test
    void rollsPastDateIntoNextYear() {
        // 5. Januar already passed in 2026 -> next occurrence is 2027
        assertEquals(LocalDate.of(2027, 1, 5),
                AmazonDeliveryParser.parseEarliest("Lieferung Montag, 5. Januar", TODAY));
    }

    @Test
    void parsesEnglishDates() {
        // English "Month day" order
        assertEquals(LocalDate.of(2026, 6, 26),
                AmazonDeliveryParser.parseEarliest("FREE delivery Thursday, June 26", TODAY));
        // English relative
        assertEquals(TODAY.plusDays(1),
                AmazonDeliveryParser.parseEarliest("Delivery tomorrow", TODAY));
        assertEquals(TODAY.plusDays(2),
                AmazonDeliveryParser.parseEarliest("Get it the day after tomorrow", TODAY));
    }

    @Test
    void returnsNullForVagueShippingText() {
        assertNull(AmazonDeliveryParser.parseEarliest("Gewöhnlich versandfertig in 1 bis 2 Monaten", TODAY));
        assertNull(AmazonDeliveryParser.parseEarliest("Usually ships within 1 to 2 months", TODAY));
        assertNull(AmazonDeliveryParser.parseEarliest(null, TODAY));
        assertNull(AmazonDeliveryParser.parseEarliest("", TODAY));
    }

    @Test
    void parsesPricesInBothLocales() {
        // German format
        assertEquals(new BigDecimal("749.00"), CloakBrowserClient.parsePrice("749,00 €"));
        assertEquals(new BigDecimal("1299.00"), CloakBrowserClient.parsePrice("1.299,00 €"));
        assertEquals(new BigDecimal("89.99"), CloakBrowserClient.parsePrice("€ 89,99"));
        // English/dot-decimal format (CloakBrowser sometimes renders Amazon in en-US)
        assertEquals(new BigDecimal("489.00"), CloakBrowserClient.parsePrice("€489.00"));
        assertEquals(new BigDecimal("1299.00"), CloakBrowserClient.parsePrice("€1,299.00"));
        assertNull(CloakBrowserClient.parsePrice(null));
        assertNull(CloakBrowserClient.parsePrice("kostenlos"));
    }
}
