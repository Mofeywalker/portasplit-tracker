package de.wss.portasplit.web.dto;

/** Aggregate counters shown as cards at the top of the dashboard. */
public record SummaryDto(
        long totalShops,
        long branchShops,
        long onlineShops,
        long enabledShops,
        long availableNow
) {
}
