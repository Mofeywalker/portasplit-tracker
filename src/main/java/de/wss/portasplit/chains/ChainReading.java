package de.wss.portasplit.chains;

import de.wss.portasplit.domain.Product;
import de.wss.portasplit.service.AvailabilitySnapshot;

/** One availability reading for a (shop, product) produced by a {@link ChainStockClient}. */
public record ChainReading(long shopId, Product product, AvailabilitySnapshot snapshot) {
}
