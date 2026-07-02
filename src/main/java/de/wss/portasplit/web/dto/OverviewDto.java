package de.wss.portasplit.web.dto;

import java.util.List;

/** Full payload for the dashboard's main view. */
public record OverviewDto(
        SummaryDto summary,
        RadiusInfoDto radius,
        List<ShopOverviewDto> shops
) {
}
