package de.wss.portasplit.web.dto;

/**
 * Telegram notification settings shown on the settings page.
 *
 * @param telegramConfigured whether a bot token + chat id are present (the integration is usable)
 * @param telegramNotify     runtime master switch: are automatic alerts being sent?
 */
public record NotificationSettingsDto(
        boolean telegramConfigured,
        boolean telegramNotify
) {
}
