package de.wss.portasplit.chains;

import de.wss.portasplit.domain.Product;

/**
 * Published by {@code AvailabilityReconciler} when a toom <em>branch</em> (market-pickup) flips from
 * unavailable to available within the configured radius. Handled after the reconciliation transaction
 * commits by {@link ToomReserveVerifier}, which probes toom's real reserve gate
 * ({@code /shop/rest/V1/toom/basket/items/limited}) and only alerts when the article is genuinely
 * reservable - toom's public {@code buyboxcases} signal over-reports, so a flip alone is not proof
 * that the pickup-reservation can actually be created.
 *
 * @param shopId      the branch shop id
 * @param product     the product that became available
 * @param plz         the branch PLZ (mapped to a toom {@code market_id} by the verifier)
 * @param marketName  the branch display name (for the alert)
 * @param productUrl  last known product URL (for the alert deep link), may be {@code null}
 */
public record ToomBecameAvailableEvent(Long shopId, Product product, String plz, String marketName,
                                       String productUrl) {
}
