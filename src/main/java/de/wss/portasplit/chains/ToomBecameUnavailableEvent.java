package de.wss.portasplit.chains;

import de.wss.portasplit.domain.Product;

/**
 * Published by {@code AvailabilityReconciler} when a toom branch flips from available back to
 * unavailable. {@link ToomReserveVerifier} uses it to reset that (product, PLZ)'s reserve-attempt
 * counter, so the next genuine restock gets a fresh allowance of attempts.
 *
 * @param shopId  the branch shop id
 * @param product the product that went unavailable
 * @param plz     the branch PLZ (episode key together with the product)
 */
public record ToomBecameUnavailableEvent(Long shopId, Product product, String plz) {
}
