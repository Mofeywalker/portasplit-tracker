package de.wss.portasplit.web.dto;

import de.wss.portasplit.domain.Shop;

import java.time.Instant;

/** Read view of a {@link Shop}. */
public record ShopDto(
        Long id,
        String chain,
        String name,
        String matchName,
        String city,
        String plz,
        String street,
        Double lat,
        Double lon,
        boolean onlineOnly,
        String source,
        boolean enabled,
        Instant createdAt
) {
    public static ShopDto from(Shop s) {
        return new ShopDto(s.getId(), s.getChain(), s.getName(), s.getMatchName(),
                s.getCity(), s.getPlz(), s.getStreet(), s.getLat(), s.getLon(),
                s.isOnlineOnly(), s.getSource().name(), s.isEnabled(), s.getCreatedAt());
    }
}
