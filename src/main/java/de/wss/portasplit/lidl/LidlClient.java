package de.wss.portasplit.lidl;

import de.wss.portasplit.amazon.CloakBrowserClient;
import de.wss.portasplit.service.AvailabilitySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Scrapes the Lidl.de product page (a client-rendered SPA) via the shared {@link CloakBrowserClient}.
 * Lidl only sells the PortaSplit Cool. Availability is a simple in-stock / sold-out signal: a
 * "In den Warenkorb" button means available; an "online ausverkauft" / "Benachrichtige mich" state
 * means sold out.
 */
@Component
public class LidlClient {

    private static final Logger log = LoggerFactory.getLogger(LidlClient.class);

    /**
     * Dedicated stealth-context seed for Lidl, so it drives its own persistent tab and can scrape
     * concurrently with the other sources instead of contending on a shared tab.
     */
    private static final int FINGERPRINT_SEED = 6262;

    private static final String EXTRACT_JS = """
            (() => {
              const txt = (s) => { const e = document.querySelector(s); return e ? e.textContent.replace(/\\s+/g,' ').trim() : null; };
              const has = (s) => !!document.querySelector(s);
              const btns = [...document.querySelectorAll('button,a')].map(e => (e.textContent||'').replace(/\\s+/g,' ').trim().toLowerCase());
              const addToCart = btns.some(t => t.includes('in den warenkorb'));
              const notify = btns.some(t => t.includes('benachrichtige'));
              const priceEl = document.querySelector('.ods-price__main-wrapper, .ods-price__value, [class*="price__main"]');
              const price = priceEl ? priceEl.textContent.replace(/-?\\d+\\s*%/g,'').replace(/\\s+/g,' ').trim() : null;
              const availText = txt('[class*="online-availability"]') || txt('[class*="availability__online"]') || txt('.availability');
              const soldOut = has('.online-availability--not-available') || has('[class*="back-in-stock"]') || notify
                  || (availText ? /ausverkauft|nicht verf|derzeit nicht|nicht mehr verf|vergriffen/i.test(availText) : false);
              return JSON.stringify({
                ready: !!(price || availText || addToCart || soldOut),
                title: txt('h1') || document.title,
                price: price,
                availText: availText,
                addToCart: addToCart,
                soldOut: soldOut
              });
            })()
            """;

    private final CloakBrowserClient cloak;

    public LidlClient(CloakBrowserClient cloak) {
        this.cloak = cloak;
    }

    /**
     * Scrapes the Lidl product page and returns a source-neutral snapshot. When no definitive
     * in-stock/sold-out signal is found (e.g. a render hiccup), returns a "not observed" snapshot so
     * the known state is left untouched.
     */
    public AvailabilitySnapshot scrape(String url) {
        Map<String, Object> data = cloak.fetchFingerprinted(FINGERPRINT_SEED, url, EXTRACT_JS);
        if (data == null) {
            return AvailabilitySnapshot.notObserved();
        }
        boolean addToCart = Boolean.TRUE.equals(data.get("addToCart"));
        boolean soldOut = Boolean.TRUE.equals(data.get("soldOut"));
        if (!addToCart && !soldOut) {
            // No definitive signal this run - don't flip the known state.
            return AvailabilitySnapshot.notObserved();
        }
        boolean available = addToCart && !soldOut;
        BigDecimal price = CloakBrowserClient.parsePrice(str(data.get("price")));
        String availText = str(data.get("availText"));
        String note = available ? "Verfügbar" : (soldOut ? "Online ausverkauft"
                : (availText != null ? availText : "Nicht verfügbar"));

        log.debug("Lidl {} -> available={} price={} note={}", url, available, price, note);
        return new AvailabilitySnapshot(true, true, available, null, price, url,
                System.currentTimeMillis(), note);
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
