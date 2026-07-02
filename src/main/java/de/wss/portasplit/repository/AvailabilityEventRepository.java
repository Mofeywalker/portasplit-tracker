package de.wss.portasplit.repository;

import de.wss.portasplit.domain.AvailabilityEvent;
import de.wss.portasplit.domain.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvailabilityEventRepository extends JpaRepository<AvailabilityEvent, Long> {

    List<AvailabilityEvent> findByShopIdAndProductOrderByCreatedAtAsc(Long shopId, Product product);

    List<AvailabilityEvent> findByShopIdOrderByCreatedAtDesc(Long shopId, Pageable pageable);

    List<AvailabilityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    void deleteByShopId(Long shopId);
}
