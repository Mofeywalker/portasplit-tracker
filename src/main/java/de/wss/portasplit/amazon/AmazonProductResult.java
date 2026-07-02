package de.wss.portasplit.amazon;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outcome of scraping one Amazon product page.
 *
 * @param ok               whether the page was loaded and parsed (false on navigation/scrape error
 *                         or a bot wall — the caller then leaves the known state untouched).
 * @param error            error detail when {@code ok} is false.
 * @param inStock          buy box present / not "currently unavailable".
 * @param price            parsed price, or {@code null}.
 * @param prime            Prime / fast-shipping badge detected.
 * @param earliestDelivery earliest delivery date parsed from the buy box, or {@code null} if none.
 * @param deliveryText     raw delivery text shown to the user (for transparency / debugging).
 * @param url              the product url that was scraped.
 */
public record AmazonProductResult(
        boolean ok,
        String error,
        boolean inStock,
        BigDecimal price,
        boolean prime,
        LocalDate earliestDelivery,
        String deliveryText,
        String url
) {

    public static AmazonProductResult error(String url, String message) {
        return new AmazonProductResult(false, message, false, null, false, null, null, url);
    }

    public static AmazonProductResult of(String url, boolean inStock, BigDecimal price, boolean prime,
                                         LocalDate earliestDelivery, String deliveryText) {
        return new AmazonProductResult(true, null, inStock, price, prime, earliestDelivery, deliveryText, url);
    }
}
