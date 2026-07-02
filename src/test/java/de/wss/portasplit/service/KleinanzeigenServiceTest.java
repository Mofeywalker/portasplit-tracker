package de.wss.portasplit.service;

import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.jobs.JobLogger;
import de.wss.portasplit.kleinanzeigen.KleinanzeigenClient;
import de.wss.portasplit.kleinanzeigen.KleinanzeigenListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioural tests for the kleinanzeigen new-offer watcher: freshness filtering, de-duplication via
 * the bounded id ring, notifying every fresh offer oldest-first, and not burning the de-dup entry on
 * a failed Telegram send.
 */
class KleinanzeigenServiceTest {

    private static final String KEY = SettingsService.KLEINANZEIGEN_NOTIFIED_IDS;

    private KleinanzeigenClient client;
    private NotificationService notifications;
    private SettingsService settings;
    private KleinanzeigenStatusHolder statusHolder;
    private AppProperties props;
    private KleinanzeigenService service;

    @BeforeEach
    void setUp() {
        client = mock(KleinanzeigenClient.class);
        notifications = mock(NotificationService.class);
        settings = mock(SettingsService.class);
        statusHolder = mock(KleinanzeigenStatusHolder.class);
        props = mock(AppProperties.class);
        when(props.kleinanzeigen()).thenReturn(new AppProperties.Kleinanzeigen(
                true, "http://example/search", 3, 45000, 75000, 25000));
        when(settings.get(KEY)).thenReturn(Optional.empty());
        service = new KleinanzeigenService(client, notifications, settings, statusHolder, props, new JobLogger());
    }

    private static KleinanzeigenListing listing(String adId, long ageSeconds) {
        long posted = System.currentTimeMillis() - ageSeconds * 1000L;
        return new KleinanzeigenListing(adId, "Midea PortaSplit " + adId,
                "https://www.kleinanzeigen.de/s-anzeige/" + adId, null, "950 €",
                "56410 Montabaur", "Heute, 14:00", posted, false);
    }

    @Test
    void notifiesFreshUnseenOfferAndRemembersIt() {
        when(client.scrape(any())).thenReturn(List.of(listing("A", 30)));
        when(notifications.notifyNewListing(any())).thenReturn(true);

        ScrapeCheckResult r = service.runCheck();

        assertEquals(1, r.available());
        verify(notifications).notifyNewListing(any());
        verify(settings).set(eq(KEY), eq("A"));
    }

    @Test
    void ignoresStaleOffersOutsideTheFreshnessWindow() {
        when(client.scrape(any())).thenReturn(List.of(listing("OLD", 600))); // 10 min old, window is 3 min

        ScrapeCheckResult r = service.runCheck();

        assertEquals(0, r.available());
        verify(notifications, never()).notifyNewListing(any());
        verify(settings, never()).set(eq(KEY), any());
    }

    @Test
    void doesNotReNotifyAnAlreadySeenOffer() {
        when(settings.get(KEY)).thenReturn(Optional.of("A,B"));
        // "A" reappears (and is still fresh) but was already notified earlier.
        when(client.scrape(any())).thenReturn(List.of(listing("A", 20)));

        ScrapeCheckResult r = service.runCheck();

        assertEquals(0, r.available());
        verify(notifications, never()).notifyNewListing(any());
        verify(settings, never()).set(eq(KEY), any());
    }

    @Test
    void notifiesEveryFreshOfferOldestFirstAndRemembersAll() {
        // scrape returns newest-first; both fresh and unseen.
        when(client.scrape(any())).thenReturn(List.of(listing("B", 20), listing("A", 40)));
        when(notifications.notifyNewListing(any())).thenReturn(true);

        ScrapeCheckResult r = service.runCheck();

        assertEquals(2, r.available());
        ArgumentCaptor<KleinanzeigenListing> sent = ArgumentCaptor.forClass(KleinanzeigenListing.class);
        verify(notifications, times(2)).notifyNewListing(sent.capture());
        // oldest (A) first, then newest (B)
        assertEquals(List.of("A", "B"), sent.getAllValues().stream().map(KleinanzeigenListing::adId).toList());
        // newest ends up last in the remembered ring
        verify(settings).set(eq(KEY), eq("A,B"));
    }

    @Test
    void failedTelegramSendDoesNotBurnTheDedupEntry() {
        when(client.scrape(any())).thenReturn(List.of(listing("A", 30)));
        when(notifications.notifyNewListing(any())).thenReturn(false); // Telegram hiccup

        ScrapeCheckResult r = service.runCheck();

        assertEquals(0, r.available());
        verify(notifications).notifyNewListing(any());
        // crucial: the id is NOT remembered, so the next poll retries while still fresh.
        verify(settings, never()).set(eq(KEY), any());
    }

    @Test
    void unreadablePageNeverNotifies() {
        when(client.scrape(any())).thenReturn(null);

        ScrapeCheckResult r = service.runCheck();

        assertEquals(0, r.available());
        verify(notifications, never()).notifyNewListing(any());
        verify(settings, never()).set(eq(KEY), any());
    }
}
