package de.wss.portasplit.web.dto;

/** Result of a Telegram test message attempt. */
public record TelegramTestResult(boolean configured, boolean sent, String message) {
}
