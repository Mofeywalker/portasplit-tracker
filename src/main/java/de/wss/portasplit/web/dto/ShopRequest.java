package de.wss.portasplit.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload for creating or updating a shop via the dashboard. */
public record ShopRequest(
        @NotBlank String chain,
        @NotBlank String name,
        String matchName,
        String city,
        String plz,
        String street,
        Double lat,
        Double lon,
        Boolean onlineOnly,
        /** Optional explicit checker source (e.g. "AMAZON", "LIDL"); when blank it is derived from the chain. */
        String source,
        Boolean enabled
) {
}
