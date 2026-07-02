package de.wss.portasplit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable history record written whenever the observed stock or availability for a
 * (shop, product) pair changes. Powers the dashboard history view and charts.
 */
@Entity
@Table(
        name = "availability_event",
        indexes = {
                @Index(name = "idx_event_shop_product", columnList = "shop_id, product, created_at"),
                @Index(name = "idx_event_created", columnList = "created_at")
        }
)
public class AvailabilityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    /** Denormalized so history survives even if the shop is later renamed. */
    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @Column(name = "chain")
    private String chain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Product product;

    @Column(nullable = false)
    private boolean available;

    private Integer stock;

    private BigDecimal price;

    @Column(name = "event_type", length = 32, nullable = false)
    private String eventType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AvailabilityEvent() {
    }

    public AvailabilityEvent(Shop shop, Product product, boolean available, Integer stock,
                             BigDecimal price, String eventType, Instant createdAt) {
        this.shopId = shop.getId();
        this.shopName = shop.getName();
        this.chain = shop.getChain();
        this.product = product;
        this.available = available;
        this.stock = stock;
        this.price = price;
        this.eventType = eventType;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getShopId() {
        return shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public String getChain() {
        return chain;
    }

    public Product getProduct() {
        return product;
    }

    public boolean isAvailable() {
        return available;
    }

    public Integer getStock() {
        return stock;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
