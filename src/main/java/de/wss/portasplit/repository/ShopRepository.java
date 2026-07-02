package de.wss.portasplit.repository;

import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.domain.ShopSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findByMatchName(String matchName);

    /** Enabled shops handled by the given checker (a per-chain checker, Amazon or Lidl). */
    List<Shop> findByEnabledTrueAndSource(ShopSource source);

    List<Shop> findAllByOrderByChainAscNameAsc();
}
