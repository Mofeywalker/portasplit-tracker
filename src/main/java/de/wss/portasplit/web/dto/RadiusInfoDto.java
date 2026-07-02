package de.wss.portasplit.web.dto;

/**
 * Radius-search summary attached to the dashboard overview so the UI can show how many branches the
 * Umkreissuche currently surfaces and around which centre.
 */
public record RadiusInfoDto(
        /** Filter switched on by the user. */
        boolean enabled,
        /** Actually filtering: enabled, a resolved centre and a positive radius. */
        boolean active,
        double km,
        Double centerLat,
        Double centerLon,
        String centerLabel,
        /** Total physical branches tracked (online shops excluded). */
        long totalBranches,
        /** Physical branches in scope (within the radius; equals total when inactive). */
        long branchesInRadius
) {
}
