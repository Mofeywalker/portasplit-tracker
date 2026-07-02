package de.wss.portasplit.service;

import de.wss.portasplit.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Sends messages through the Telegram Bot API. */
@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private final RestClient restClient;
    private final AppProperties props;
    private final SettingsService settings;

    public TelegramService(RestClient restClient, AppProperties props, SettingsService settings) {
        this.restClient = restClient;
        this.props = props;
        this.settings = settings;
    }

    public boolean isConfigured() {
        AppProperties.Telegram t = props.telegram();
        return t.enabled()
                && StringUtils.hasText(t.botToken())
                && StringUtils.hasText(t.chatId());
    }

    /** Runtime master switch for automatic notifications (set on the settings page). */
    public boolean notificationsEnabled() {
        return settings.telegramNotifyEnabled();
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
     * Sends an HTML-formatted message. Returns {@code true} if it was accepted by Telegram.
     * No-op (returns {@code false}) when Telegram is disabled or not configured.
     */
    public boolean sendHtml(String html) {
        AppProperties.Telegram t = props.telegram();
        if (!isConfigured()) {
            log.debug("Telegram not configured/enabled; skipping message");
            return false;
        }
        String url = t.apiBase() + "/bot" + t.botToken() + "/sendMessage";
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id", t.chatId(),
                            "text", html,
                            "parse_mode", "HTML",
                            "disable_web_page_preview", true
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Telegram message sent");
            return true;
        } catch (Exception e) {
            log.warn("Failed to send Telegram message: {}", e.getMessage());
            return false;
        }
    }

    /** Escapes the five characters that are special in Telegram HTML mode. */
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
