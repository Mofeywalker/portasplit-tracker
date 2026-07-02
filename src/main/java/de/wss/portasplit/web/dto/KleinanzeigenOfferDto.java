package de.wss.portasplit.web.dto;

/**
 * One kleinanzeigen offer as shown on the dashboard.
 *
 * @param fresh    whether it was within the freshness window at the last check.
 * @param notified whether a Telegram notification was already sent for it.
 */
public record KleinanzeigenOfferDto(
        String adId,
        String title,
        String url,
        String price,
        String location,
        String posted,
        Long ageSeconds,
        boolean fresh,
        boolean notified,
        boolean topAd
) {}
