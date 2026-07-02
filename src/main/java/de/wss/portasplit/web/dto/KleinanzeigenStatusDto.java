package de.wss.portasplit.web.dto;

import java.time.Instant;
import java.util.List;

/** Dashboard payload for the kleinanzeigen new-offer watcher. */
public record KleinanzeigenStatusDto(
        boolean enabled,
        String searchUrl,
        int freshnessMinutes,
        Instant lastCheckedAt,
        boolean lastOk,
        String lastError,
        int total,
        int fresh,
        List<KleinanzeigenOfferDto> offers
) {}
