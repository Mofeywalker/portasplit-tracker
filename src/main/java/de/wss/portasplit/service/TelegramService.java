package de.wss.portasplit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** Sends notifications through the Telegram Bot API to the confirmed subscribers. */
@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private final TelegramBotClient client;
    private final TelegramSubscriberService subscribers;
    private final SettingsService settings;

    public TelegramService(TelegramBotClient client, TelegramSubscriberService subscribers,
                           SettingsService settings) {
        this.client = client;
        this.subscribers = subscribers;
        this.settings = settings;
    }

    /** Configured (bot on + token) and at least one confirmed recipient exists. */
    public boolean isConfigured() {
        return client.canOperate() && subscribers.confirmedCount() > 0;
    }

    /** Runtime master switch for automatic notifications (set on the settings page). */
    public boolean notificationsEnabled() {
        return settings.telegramNotifyEnabled();
    }

    /** Number of confirmed recipient chats (0 when none has opted in / been seeded). */
    public int recipientCount() {
        return (int) subscribers.confirmedCount();
    }

    /**
     * Sends an automatic notification, honouring the runtime "send notifications" switch. Returns
     * {@code false} (a no-op) when notifications are turned off — the explicit {@link #sendHtml} test
     * path stays unaffected so users can always verify their configuration.
     */
    public boolean sendNotification(String html) {
        if (!notificationsEnabled()) {
            log.debug("Telegram notifications disabled at runtime; skipping message");
            return false;
        }
        return sendHtml(html);
    }

    /**
     * Sends an HTML-formatted message to every confirmed recipient. Returns {@code true} if at least
     * one recipient accepted it. No-op (returns {@code false}) when Telegram is disabled or nobody is
     * confirmed.
     *
     * <p>"At least one" rather than "all" is deliberate: callers use the result to decide whether a
     * one-off event (e.g. a new listing) has been handled. Requiring every recipient to succeed would
     * re-send the whole batch on the next poll — spamming those who already received it — whenever a
     * single chat id is unreachable (bot blocked, chat deleted). A total failure (Telegram down) still
     * returns {@code false} and is retried.
     */
    public boolean sendHtml(String html) {
        if (!client.canOperate()) {
            log.debug("Telegram not configured/enabled; skipping message");
            return false;
        }
        List<String> chatIds = subscribers.confirmedChatIds();
        if (chatIds.isEmpty()) {
            log.debug("Telegram has no confirmed recipients; skipping message");
            return false;
        }
        int sent = 0;
        for (String chatId : chatIds) {
            if (client.sendHtml(chatId, html)) {
                sent++;
            }
        }
        if (sent == 0) {
            log.warn("Telegram message not delivered to any of {} recipient(s)", chatIds.size());
        } else if (sent < chatIds.size()) {
            log.warn("Telegram message delivered to {}/{} recipient(s)", sent, chatIds.size());
        } else {
            log.debug("Telegram message sent to {} recipient(s)", sent);
        }
        return sent > 0;
    }

    /** Escapes the three characters that are special in Telegram HTML mode. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
