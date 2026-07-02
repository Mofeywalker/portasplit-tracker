package de.wss.portasplit.kleinanzeigen;

import de.wss.portasplit.amazon.CloakBrowserClient;
import de.wss.portasplit.jobs.JobLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes a kleinanzeigen.de search-results page via the shared {@link CloakBrowserClient} and
 * returns the offers found on it, newest first. kleinanzeigen renders the results server-side, so a
 * single navigation is enough; the stealth browser is what gets us past kleinanzeigen's bot
 * detection.
 *
 * <p>Date handling is the interesting part: kleinanzeigen only exposes minute-precision, relative
 * timestamps ("Heute, 14:23" / "Gestern, 09:05") or a plain date for older ads, all in
 * {@code Europe/Berlin}. {@link #parsePostedEpochMillis} turns those into absolute epoch millis so
 * the caller can decide whether an offer is "fresh".
 */
@Component
public class KleinanzeigenClient {

    private static final Logger log = LoggerFactory.getLogger(KleinanzeigenClient.class);

    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String BASE_URL = "https://www.kleinanzeigen.de";
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern DIGITS = Pattern.compile("(\\d{3,})");

    /**
     * Dedicated stealth-context seed for kleinanzeigen. Its own persistent tab keeps the
     * cookie-consent acceptance warm across polls (instead of being clobbered by the shared tab the
     * other scrapers reuse), which is what kleinanzeigen's bot-mitigation reacts to.
     */
    private static final int FINGERPRINT_SEED = 7777;

    /**
     * Extracts the real search-result offers, layout-agnostically. kleinanzeigen serves two very
     * different markups for the same {@code #srchrslt-adtable} list (a classic {@code article.aditem}
     * one and a newer Tailwind one with no shared inner class names), so ids/links come from the DOM
     * but title/price/location/date are pulled by regex from each card's text (and the title falls
     * back to the {@code /s-anzeige/} URL slug). The appended "Alternative Anzeigen in der Umgebung"
     * block ({@code #srchrslt-adtable-altads}, ads outside the configured PLZ/radius/price) is
     * excluded. Accepts the cookie-consent banner and only reports {@code ready} once listings have
     * rendered (or an explicit "no results" box shows) — not behind the consent wall and not for a
     * bot-throttled shell, so those surface as a read failure instead of a bogus "0 offers".
     */
    private static final String EXTRACT_JS = """
            (() => {
              try { const c = document.querySelector('#gdpr-banner-accept'); if (c) c.click(); } catch (e) {}
              const clean = (s) => s ? s.replace(/\\s+/g, ' ').trim() : null;
              const ALT = '#srchrslt-adtable-altads, [id*="altads"], [class*="similar"], [class*="alternative-"]';
              const isAlt = (el) => !!(el.closest && el.closest(ALT));

              // Card containers — real results only. #srchrslt-adtable holds them in both layouts as
              // direct <li> children. Fall back to article.aditem, then to /s-anzeige links.
              const table = document.querySelector('#srchrslt-adtable');
              let cards = table ? [...table.children].filter(el => el.tagName === 'LI' && !isAlt(el)) : [];
              if (cards.length === 0) cards = [...document.querySelectorAll('article.aditem')].filter(el => !isAlt(el));
              if (cards.length === 0) {
                const seen = new Set();
                document.querySelectorAll('a[href*="/s-anzeige/"]').forEach(a => {
                  if (isAlt(a)) return;
                  let c = a;
                  for (let i = 0; i < 7 && c.parentElement; i++) { c = c.parentElement; if (c.tagName === 'ARTICLE' || c.tagName === 'LI') break; }
                  if (!isAlt(c) && !seen.has(c)) { seen.add(c); cards.push(c); }
                });
              }

              const titleFromSlug = (href) => {
                const m = (href || '').match(/\\/s-anzeige\\/([^\\/]+)\\//);
                return m ? m[1].replace(/-/g, ' ').replace(/\\b\\w/g, ch => ch.toUpperCase()) : null;
              };
              const items = cards.map(el => {
                const text = (el.innerText || '').replace(/\\s+/g, ' ').trim();
                const adLinks = [...el.querySelectorAll('a[href*="/s-anzeige/"]')];
                const href = (el.getAttribute && el.getAttribute('data-href')) || (adLinks[0] ? adLinks[0].getAttribute('href') : null);
                let adId = (el.getAttribute && el.getAttribute('data-adid')) || null;
                if (!adId && href) { const m = href.match(/(\\d{6,})/); adId = m ? m[1] : null; }
                let title = adLinks.map(a => clean(a.textContent) || '').filter(t => t.length >= 4 && !/^\\d+$/.test(t)).sort((a, b) => b.length - a.length)[0] || null;
                if (!title) title = titleFromSlug(href);
                const dm = text.match(/(Heute|Gestern),?\\s*\\d{1,2}:\\d{2}/) || text.match(/\\b\\d{1,2}\\.\\d{1,2}\\.\\d{4}\\b/);
                const pm = text.match(/\\d{1,3}(?:\\.\\d{3})*(?:,\\d{2})?\\s*€(?:\\s*VB)?/) || text.match(/Zu verschenken/i);
                const lm = text.match(/\\b\\d{5}\\s+[A-Za-zÄÖÜäöüß.-]+(?:\\s+[A-Za-zÄÖÜäöüß.-]+){0,2}/);
                return {
                  adId: adId,
                  href: href,
                  title: title,
                  price: pm ? pm[0] : null,
                  location: lm ? clean(lm[0]) : null,
                  date: dm ? dm[0] : null,
                  top: /is-topad|is-highlight|topad/i.test(el.className || '')
                };
              }).filter(it => it.adId);

              const consentUp = !!document.querySelector('#gdpr-banner-accept, #gdpr-banner');
              const bodyTxt = (document.body ? document.body.innerText : '').slice(0, 2500);
              const noResults = !!document.querySelector('.srp-no-results, [class*="no-results"]')
                  || /keine\\s+(passenden\\s+)?(ergebnisse|anzeigen)/i.test(bodyTxt);
              return JSON.stringify({
                ready: !!(items.length > 0 || (!consentUp && noResults)),
                count: items.length,
                items: items
              });
            })()
            """;

    private final CloakBrowserClient cloak;
    private final JobLogger jobLog;

    public KleinanzeigenClient(CloakBrowserClient cloak, JobLogger jobLog) {
        this.cloak = cloak;
        this.jobLog = jobLog;
    }

    /**
     * Scrapes the search-results page.
     *
     * @return the offers found, sorted newest first; an empty list if the page rendered but listed
     *         nothing; or {@code null} if the page could not be scraped (so the caller leaves state
     *         untouched and never emits a spurious notification).
     */
    public List<KleinanzeigenListing> scrape(String url) {
        Map<String, Object> data = cloak.fetchFingerprinted(FINGERPRINT_SEED, url, EXTRACT_JS);
        if (data == null || !Boolean.TRUE.equals(data.get("ready"))) {
            return null;
        }
        List<KleinanzeigenListing> listings = parseListings(data.get("items"), ZonedDateTime.now(BERLIN));
        log.debug("kleinanzeigen {} -> {} listing(s)", url, listings.size());
        jobLog.debug("Kleinanzeigen: {} Karte(n) auf der Suchseite geparst", listings.size());
        return listings;
    }

    /** Maps the raw JS extraction items into typed, date-resolved listings, newest first. */
    static List<KleinanzeigenListing> parseListings(Object itemsObj, ZonedDateTime now) {
        List<KleinanzeigenListing> result = new ArrayList<>();
        if (!(itemsObj instanceof List<?> items)) {
            return result;
        }
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String href = str(m.get("href"));
            String adId = firstNonBlank(str(m.get("adId")), digits(href), href);
            if (adId == null) {
                continue;
            }
            String dateText = str(m.get("date"));
            String priceText = str(m.get("price"));
            result.add(new KleinanzeigenListing(
                    adId,
                    str(m.get("title")),
                    absoluteUrl(href),
                    CloakBrowserClient.parsePrice(priceText),
                    priceText,
                    str(m.get("location")),
                    dateText,
                    parsePostedEpochMillis(dateText, now),
                    Boolean.TRUE.equals(m.get("top"))
            ));
        }
        // Newest first; ads with an unparseable/missing date sort last. kleinanzeigen timestamps are
        // only minute-precise, so a same-minute tie is broken deterministically by ad id (otherwise
        // two ads of the same minute would order non-deterministically across polls).
        result.sort(Comparator.comparingLong(
                (KleinanzeigenListing l) -> l.postedEpochMillis() == null ? Long.MIN_VALUE : l.postedEpochMillis())
                .reversed()
                .thenComparing(KleinanzeigenListing::adId));
        return result;
    }

    /**
     * Resolves a kleinanzeigen posting label to absolute epoch millis in {@code Europe/Berlin}.
     * Handles "Heute, HH:MM", "Gestern, HH:MM" and "DD.MM.YYYY"; returns {@code null} when nothing
     * recognizable is found (caller treats that as "old").
     */
    static Long parsePostedEpochMillis(String dateText, ZonedDateTime now) {
        if (dateText == null || dateText.isBlank()) {
            return null;
        }
        String lower = dateText.toLowerCase(Locale.GERMAN);
        Matcher tm = TIME.matcher(dateText);
        LocalTime time = tm.find() ? safeTime(tm.group(1), tm.group(2)) : null;

        if (lower.contains("heute")) {
            LocalDate day = now.toLocalDate();
            return atBerlin(day, time != null ? time : LocalTime.MIDNIGHT);
        }
        if (lower.contains("gestern")) {
            LocalDate day = now.toLocalDate().minusDays(1);
            return atBerlin(day, time != null ? time : LocalTime.MIDNIGHT);
        }
        Matcher dm = DATE.matcher(dateText);
        if (dm.find()) {
            try {
                LocalDate day = LocalDate.of(
                        Integer.parseInt(dm.group(3)),
                        Integer.parseInt(dm.group(2)),
                        Integer.parseInt(dm.group(1)));
                return atBerlin(day, time != null ? time : LocalTime.MIDNIGHT);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static Long atBerlin(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(BERLIN).toInstant().toEpochMilli();
    }

    private static LocalTime safeTime(String hh, String mm) {
        try {
            int h = Integer.parseInt(hh);
            int m = Integer.parseInt(mm);
            if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                return LocalTime.of(h, m);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }

    static String absoluteUrl(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String h = href.trim();
        if (h.startsWith("http://") || h.startsWith("https://")) {
            return h;
        }
        return BASE_URL + (h.startsWith("/") ? h : "/" + h);
    }

    private static String digits(String href) {
        if (href == null) {
            return null;
        }
        Matcher m = DIGITS.matcher(href);
        return m.find() ? m.group(1) : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
