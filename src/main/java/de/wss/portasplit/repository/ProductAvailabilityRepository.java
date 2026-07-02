package de.wss.portasplit.repository;

import de.wss.portasplit.domain.Product;
import de.wss.portasplit.domain.ProductAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductAvailabilityRepository extends JpaRepository<ProductAvailability, Long> {

    Optional<ProductAvailability> findByShopIdAndProduct(Long shopId, Product product);

    @Query("select a from ProductAvailability a join fetch a.shop order by a.shop.chain, a.shop.name, a.product")
    List<ProductAvailability> findAllWithShop();

    List<ProductAvailability> findByShopId(Long shopId);

    long countByAvailableTrue();

    void deleteByShopId(Long shopId);
}
