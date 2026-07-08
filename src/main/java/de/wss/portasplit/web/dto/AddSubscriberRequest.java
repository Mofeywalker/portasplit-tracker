package de.wss.portasplit.web.dto;

/** Request to add a Telegram recipient by chat id from the settings UI. */
public record AddSubscriberRequest(String chatId) {
}
