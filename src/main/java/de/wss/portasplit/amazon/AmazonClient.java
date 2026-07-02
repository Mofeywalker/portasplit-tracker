package de.wss.portasplit.amazon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * Scrapes Amazon.de product pages via the shared {@link CloakBrowserClient}: extracts price, stock
 * status, delivery date and Prime badge from the rendered buy box.
 */
@Component
public class AmazonClient {

    private static final Logger log = LoggerFactory.getLogger(AmazonClient.class);

    /**
     * Dedicated stealth-context seed for Amazon, so it drives its own persistent tab and can scrape
     * concurrently with the other sources instead of contending on a shared tab.
     */
    private static final int FINGERPRINT_SEED = 5151;

    /** Reads the buy box in one DOM pass and returns it as a JSON string (incl. a {@code ready} flag). */
    private static final String EXTRACT_JS = """
            (() => {
              const txt = (s) => { const e = document.querySelector(s); return e ? e.textContent.replace(/\\s+/g,' ').trim() : null; };
              const has = (s) => !!document.querySelector(s);
              let price = null;
              for (const s of ['#corePriceDisplay_desktop_feature_div .a-price .a-offscreen',
                               '#corePrice_feature_div .a-price .a-offscreen',
                               '#price_inside_buybox', '#priceblock_ourprice', '#priceblock_dealprice',
                               '#sns-base-price', '.a-price .a-offscreen']) {
                const e = document.querySelector(s);
                if (e && e.textContent.trim()) { price = e.textContent.trim(); break; }
              }
              let availability = null;
              const ae = document.querySelector('#availability');
              if (ae) {
                for (const sp of ae.querySelectorAll('span')) {
                  const t = (sp.textContent || '').replace(/\\s+/g,' ').trim();
                  if (t && t.length < 140 && !/function|=>|\\{|\\}/.test(t)) { availability = t; break; }
                }
              }
              let delivery = null;
              for (const s of ['#mir-layout-DELIVERY_BLOCK-deliveryMessage', '#deliveryBlockMessage',
                               '#mir-layout-DELIVERY_BLOCK', '#ddmDeliveryMessage',
                               '#fulfillerInfoFeature_feature_div']) {
                const e = document.querySelector(s);
                if (e && e.textContent.trim()) { delivery = e.textContent.replace(/\\s+/g,' ').trim(); break; }
              }
              const addToCart = has('#add-to-cart-button');
              const buyNow = has('#buy-now-button');
              const title = txt('#productTitle');
              return JSON.stringify({
                ready: !!(title || addToCart || buyNow || availability),
                title: title,
                availability: availability,
                price: price,
                delivery: delivery,
                addToCart: addToCart,
                buyNow: buyNow,
                prime: has('.a-icon-prime') || has('i.a-icon-prime') || has("[aria-label*='Prime']"),
                captcha: has("form[action*='validateCaptcha']") || /captcha|geben sie die zeichen/i.test(document.title || '')
              });
            })()
            """;

    private final CloakBrowserClient cloak;

    public AmazonClient(CloakBrowserClient cloak) {
        this.cloak = cloak;
    }

    /** Scrapes a single product page. Never throws; failures are returned as a non-ok result. */
    public AmazonProductResult scrape(String url) {
        Map<String, Object> data = cloak.fetchFingerprinted(FINGERPRINT_SEED, url, EXTRACT_JS);
        if (data == null) {
            return AmazonProductResult.error(url, "Produktseite nicht geladen (evtl. Bot-Schutz)");
        }
        return interpret(url, data);
    }

    private AmazonProductResult interpret(String url, Map<String, Object> data) {
        boolean captcha = Boolean.TRUE.equals(data.get("captcha"));
        String title = str(data.get("title"));
        String availability = str(data.get("availability"));
        String delivery = str(data.get("delivery"));
        boolean addToCart = Boolean.TRUE.equals(data.get("addToCart"));
        boolean buyNow = Boolean.TRUE.equals(data.get("buyNow"));
        boolean prime = Boolean.TRUE.equals(data.get("prime"));

        if (captcha) {
            return AmazonProductResult.error(url, "Amazon-Bot-Schutz (CAPTCHA) ausgelöst");
        }
        if (title == null && !addToCart && !buyNow && delivery == null) {
            return AmazonProductResult.error(url, "Produktseite nicht geladen (evtl. Bot-Schutz)");
        }

        boolean availabilitySaysNo = availability != null
                && availability.toLowerCase(Locale.GERMAN)
                    .matches(".*(derzeit nicht verf|nicht verf\\u00fcgbar|currently unavailable|out of stock|nicht auf lager).*");
        boolean inStock = (addToCart || buyNow) && !availabilitySaysNo;

        BigDecimal price = CloakBrowserClient.parsePrice(str(data.get("price")));
        LocalDate earliest = AmazonDeliveryParser.parseEarliest(delivery, LocalDate.now());

        log.debug("Amazon {} -> inStock={} price={} earliest={} prime={}", url, inStock, price, earliest, prime);
        return AmazonProductResult.of(url, inStock, price, prime, earliest, delivery);
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
