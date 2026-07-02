package de.wss.portasplit.web.dto;

/**
 * Partial update of one source's poll interval. Any {@code null} field is left unchanged. When
 * {@code reset} is true, all overrides for the source are cleared and it falls back to the static
 * config default (the other fields are ignored).
 */
public record IntervalUpdateRequest(
        Long minIntervalMs,
        Long maxIntervalMs,
        Long initialDelayMs,
        Boolean reset
) {}
