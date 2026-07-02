package de.wss.portasplit.web.dto;

/**
 * Update for the radius search. Any field may be null (left unchanged). When {@code plz} is given it is
 * geocoded to the centre coordinates; alternatively {@code centerLat}/{@code centerLon} set the centre
 * directly. {@code label} defaults to the PLZ when resolved from one.
 */
public record RadiusSettingsRequest(
        Boolean enabled,
        Double km,
        String plz,
        Double centerLat,
        Double centerLon,
        String label
) {
}
