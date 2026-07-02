package de.wss.portasplit.chains;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.wss.portasplit.amazon.CloakBrowserClient;
import de.wss.portasplit.amazon.CloakBrowserClient.InPageResponse;
import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.domain.Product;
import de.wss.portasplit.jobs.JobLogger;
import de.wss.portasplit.repository.ProductAvailabilityRepository;
import de.wss.portasplit.service.SettingsService;
import de.wss.portasplit.service.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * toom auto-reserve action (v1: add to the logged-in cart + notify; the user does the final
 * "reservieren" click). Fires when a toom branch flips to available in the configured radius
 * ({@link ToomBecameAvailableEvent}) AND the feature is on. Because a cart is only secured for a
 * logged-in customer, this requires a live {@link ToomAuthService} session; it selects the branch's
 * market, adds the article to the (logged-in, account-bound) cart via toom's reserve gate
 * ({@code POST /shop/rest/V1/toom/basket/items/limited}) on the same warm seed as the login, and
 * alerts on success. The reservation itself is completed by the user in their own browser (same
 * account → the cart syncs), which keeps the final, binding step in the user's hands.
 */
@Component
public class ToomReserveVerifier {

    private static final Logger log = LoggerFactory.getLogger(ToomReserveVerifier.class);

    private static final String MARKETS_URL = "https://api.toom.de/public/api/markets";
    private static final String PDP_URL = "https://toom.de/p/_/%s";
    private static final String RESERVE_ADD_PATH = "/shop/rest/V1/toom/basket/items/limited";
    private static final long MARKETS_TTL_MS = 6 * 60 * 60 * 1000L;
    /** Hard cap on reserve attempts per (product, PLZ) availability episode. */
    private static final int MAX_ATTEMPTS = 2;

    /** SAP article ids (kept in sync with {@link ToomStockClient}). */
    private static final Map<Product, String> SAP_IDS = Map.of(
            Product.PORTASPLIT, "10272593",
            Product.PORTASPLIT_COOL, "10515238");

    private final CloakBrowserClient cloak;
    private final ToomAuthService auth;
    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final AppProperties props;
    private final SettingsService settings;
    private final TelegramService telegram;
    private final JobLogger jobLog;
    private final ProductAvailabilityRepository availabilityRepository;

    private volatile Map<String, String> plzToMarket = Map.of();
    private volatile long marketsFetchedAt = 0L;

    /** Reserve-attempt count per (product, PLZ) episode; reset when the branch sells out again. */
    private final ConcurrentHashMap<String, Integer> attempts = new ConcurrentHashMap<>();

    public ToomReserveVerifier(CloakBrowserClient cloak, ToomAuthService auth, RestClient restClient,
                               ObjectMapper mapper, AppProperties props, SettingsService settings,
                               TelegramService telegram, JobLogger jobLog,
                               ProductAvailabilityRepository availabilityRepository) {
        this.cloak = cloak;
        this.auth = auth;
        this.restClient = restClient;
        this.mapper = mapper;
        this.props = props;
        this.settings = settings;
        this.telegram = telegram;
        this.jobLog = jobLog;
        this.availabilityRepository = availabilityRepository;
    }

    /** Fires after the reconciliation transaction commits, once per unavailable→available flip. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onToomBecameAvailable(ToomBecameAvailableEvent e) {
        if (!settings.toomAutoReserveEnabled()) {
            return;
        }
        String sap = SAP_IDS.get(e.product());
        if (sap == null) {
            return;
        }
        String marketId = marketIdForPlz(e.plz());
        if (marketId == null) {
            jobLog.warn("toom-reserve · kein Markt zur PLZ {} - kann {} nicht reservieren",
                    e.plz(), e.product().displayName());
            return;
        }

        // Hard cap: at most MAX_ATTEMPTS reserve attempts per (product, PLZ) availability episode.
        // The counter is reset when the branch sells out again (onToomBecameUnavailable).
        String attemptKey = e.product().name() + "|" + e.plz();
        int prior = attempts.getOrDefault(attemptKey, 0);
        if (prior >= MAX_ATTEMPTS) {
            jobLog.info("toom-reserve · Limit von {} Reservierungs-Versuchen für {} @ {} erreicht - übersprungen",
                    MAX_ATTEMPTS, e.product().displayName(), e.marketName());
            return;
        }
        attempts.put(attemptKey, prior + 1);
        jobLog.info("toom-reserve · Reservierungs-Versuch {}/{} für {} @ {}",
                prior + 1, MAX_ATTEMPTS, e.product().displayName(), e.marketName());

        // A cart only secures the article for a logged-in customer. Without a session, fall back to a
        // plain availability alert so the owner can still act manually.
        if (!auth.ensureLoggedIn()) {
            jobLog.warn("toom-reserve · nicht eingeloggt - sende unbestätigten Verfügbarkeits-Hinweis für {} @ {}",
                    e.product().displayName(), e.marketName());
            recordReserveIssue(e, "Konnte nicht geprüft werden (nicht eingeloggt)");
            telegram.sendNotification(notLoggedInMessage(e));
            return;
        }

        String body;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("articleId", sap);
            payload.put("deliveryType", "marketPickup_marketPickup");
            payload.put("quantity", 1);
            body = mapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("toom-reserve payload build failed: {}", ex.getMessage());
            return;
        }

        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("market_id", marketId);
        if (StringUtils.hasText(e.marketName())) {
            cookies.put("market_name", e.marketName());
        }

        jobLog.info("toom-reserve · lege {} @ {} (Markt {}) in den eingeloggten Warenkorb",
                e.product().displayName(), e.marketName(), marketId);
        InPageResponse resp = cloak.inPagePost(props.toomReserve().fingerprintSeed(),
                String.format(PDP_URL, sap), cookies, RESERVE_ADD_PATH, body);

        int status = resp == null ? -1 : resp.status();
        if (status == 200) {
            clearReserveIssue(e);
            telegram.sendNotification(addedToCartMessage(e));
            jobLog.info("toom-reserve · {} in den Warenkorb gelegt @ {} (HTTP 200) - Telegram raus",
                    e.product().displayName(), e.marketName());
        } else if (status == 404) {
            jobLog.warn("toom-reserve · {} in {} laut buyboxcases verfügbar, aber Warenkorb lehnt ab "
                    + "(HTTP 404) - kein Alert", e.product().displayName(), e.marketName());
            recordReserveIssue(e, "Warenkorb abgelehnt (HTTP 404) - laut Lagerbestand verfügbar, aber nicht reservierbar");
        } else if (status == 401 || status == 403) {
            // Session likely expired mid-flight; drop the logged-in flag so the next flip re-logs in.
            auth.markLoggedOut();
            jobLog.warn("toom-reserve · Warenkorb abgelehnt (HTTP {}) - Session evtl. abgelaufen, "
                    + "sende unbestätigten Hinweis", status);
            recordReserveIssue(e, "Warenkorb abgelehnt (HTTP " + status + ") - Session evtl. abgelaufen");
            telegram.sendNotification(notLoggedInMessage(e));
        } else {
            jobLog.warn("toom-reserve · unklarer Warenkorb-Status (HTTP {}) für {} @ {} - unbestätigter Hinweis",
                    status, e.product().displayName(), e.marketName());
            recordReserveIssue(e, "Unklarer Warenkorb-Status (HTTP " + status + ")");
            telegram.sendNotification(notLoggedInMessage(e));
        }
    }

    /** Resets the reserve-attempt counter for a branch/product when it sells out again. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onToomBecameUnavailable(ToomBecameUnavailableEvent e) {
        String key = e.product().name() + "|" + e.plz();
        if (attempts.remove(key) != null) {
            jobLog.info("toom-reserve · {} (PLZ {}) wieder ausverkauft - Reserve-Zähler zurückgesetzt",
                    e.product().displayName(), e.plz());
        }
        clearReserveIssue(e.shopId(), e.product());
    }

    /**
     * Records that a real reservation attempt for this row was rejected, so the shop table can flag it
     * as "shows available, but reservation failed" independent of the regular availability poll (which
     * would otherwise keep reporting it as plainly available). Not itself {@code @Transactional} — the
     * lookup returns a detached entity once its own read finishes, so the change is persisted explicitly
     * via {@code save()} rather than relying on dirty-checking (which self-invocation from an
     * {@code @TransactionalEventListener} method would silently bypass anyway).
     */
    private void recordReserveIssue(ToomBecameAvailableEvent e, String note) {
        availabilityRepository.findByShopIdAndProduct(e.shopId(), e.product()).ifPresent(a -> {
            a.setReserveIssueNote(note);
            availabilityRepository.save(a);
        });
    }

    /** Clears a previously recorded reserve issue once a reservation succeeds or the branch sells out. */
    private void clearReserveIssue(ToomBecameAvailableEvent e) {
        clearReserveIssue(e.shopId(), e.product());
    }

    private void clearReserveIssue(Long shopId, Product product) {
        availabilityRepository.findByShopIdAndProduct(shopId, product)
                .filter(a -> a.getReserveIssueNote() != null)
                .ifPresent(a -> {
                    a.setReserveIssueNote(null);
                    availabilityRepository.save(a);
                });
    }

    private String addedToCartMessage(ToomBecameAvailableEvent e) {
        StringBuilder sb = new StringBuilder();
        sb.append("🟢 <b>").append(TelegramService.escape(e.product().displayName()))
                .append(" - im toom-Warenkorb reserviert-bereit!</b>\n\n");
        sb.append("🏬 ").append(TelegramService.escape(nz(e.marketName()))).append('\n');
        sb.append("🛒 In deinen (eingeloggten) Warenkorb gelegt.\n");
        sb.append("⏳ Jetzt im Browser abschließen: Warenkorb → Abholung im Markt → <b>reservieren</b>.\n");
        if (StringUtils.hasText(e.productUrl())) {
            sb.append("🔗 <a href=\"").append(TelegramService.escape(e.productUrl())).append("\">Zum Artikel</a>");
        }
        return sb.toString();
    }

    private String notLoggedInMessage(ToomBecameAvailableEvent e) {
        StringBuilder sb = new StringBuilder();
        sb.append("🟡 <b>").append(TelegramService.escape(e.product().displayName()))
                .append(" bei toom verfügbar</b>\n\n");
        sb.append("🏬 ").append(TelegramService.escape(nz(e.marketName()))).append('\n');
        sb.append("⚠️ Auto-Reserve konnte es nicht in den Warenkorb legen (nicht eingeloggt / abgelehnt). "
                + "Bitte selbst prüfen.\n");
        if (StringUtils.hasText(e.productUrl())) {
            sb.append("🔗 <a href=\"").append(TelegramService.escape(e.productUrl())).append("\">Zum Artikel</a>");
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Resolves a branch PLZ to its toom {@code market_id}, caching the market list for 6h. */
    private synchronized String marketIdForPlz(String plz) {
        if (!StringUtils.hasText(plz)) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (plzToMarket.isEmpty() || now - marketsFetchedAt > MARKETS_TTL_MS) {
            try {
                JsonNode markets = mapper.readTree(ChainHttp.get(restClient, MARKETS_URL)).path("markets");
                Map<String, String> map = new HashMap<>();
                if (markets.isArray()) {
                    for (JsonNode m : markets) {
                        String zip = m.path("address").path("zip").asText("");
                        String id = m.path("id").asText("");
                        if (!zip.isBlank() && !id.isBlank()) {
                            map.putIfAbsent(zip, id);
                        }
                    }
                }
                if (!map.isEmpty()) {
                    plzToMarket = map;
                    marketsFetchedAt = now;
                }
            } catch (Exception ex) {
                jobLog.warn("toom-reserve · Marktliste nicht ladbar: {}", ex.getMessage());
            }
        }
        return plzToMarket.get(plz.trim());
    }
}
