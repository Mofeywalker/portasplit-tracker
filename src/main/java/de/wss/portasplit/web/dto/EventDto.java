package de.wss.portasplit.web.dto;

import de.wss.portasplit.domain.AvailabilityEvent;

import java.math.BigDecimal;
import java.time.Instant;

/** History/activity record for the dashboard. */
public record EventDto(
        Long id,
        Long shopId,
        String shopName,
        String chain,
        String product,
        boolean available,
        Integer stock,
        BigDecimal price,
        String eventType,
        Instant createdAt
) {
    public static EventDto from(AvailabilityEvent e) {
        return new EventDto(e.getId(), e.getShopId(), e.getShopName(), e.getChain(),
                e.getProduct().name(), e.isAvailable(), e.getStock(), e.getPrice(),
                e.getEventType(), e.getCreatedAt());
    }
}
