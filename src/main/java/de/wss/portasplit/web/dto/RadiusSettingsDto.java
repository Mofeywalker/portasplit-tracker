package de.wss.portasplit.web.dto;

/** Current radius-search configuration, for the settings page. */
public record RadiusSettingsDto(
        boolean enabled,
        double km,
        Double centerLat,
        Double centerLon,
        String centerLabel,
        /** A centre is configured (coordinates resolved). */
        boolean centerResolved,
        /** The bundled PLZ geocoder dataset loaded successfully (PLZ entry usable). */
        boolean geocoderAvailable
) {
}
