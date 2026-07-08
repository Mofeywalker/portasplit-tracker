package de.wss.portasplit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Current availability state for one (shop, product) pair. Updated on every poll; the change
 * history lives in {@link AvailabilityEvent}.
 */
@Entity
@Table(
        name = "product_availability",
        uniqueConstraints = @UniqueConstraint(columnNames = {"shop_id", "product"}),
        indexes = @Index(name = "idx_avail_shop", columnList = "shop_id")
)
public class ProductAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Product product;

    @Column(nullable = false)
    private boolean available = false;

    /** Latest stock count from the API; null when the article was not found for this shop. */
    @Column(name = "current_stock")
    private Integer currentStock;

    private BigDecimal price;

    @Column(length = 1024)
    private String url;

    /** Optional short human note shown in the dashboard, e.g. Amazon delivery date / Prime status. */
    @Column(length = 255)
    private String note;

    /**
     * Set when this (shop, product) shows pickup stock but cannot actually be reserved. Two sources
     * feed it: toom, where a real add-to-cart attempt is refused despite buyboxcases reporting it as
     * available (maintained out-of-band by {@code ToomReserveVerifier}); and Bauhaus, whose
     * {@code /api/purchasability} endpoint reports a stocked store as non-reservable (set/cleared on
     * every poll via {@code AvailabilitySnapshot#managesReserveIssue}). Cleared once the article
     * becomes reservable or sells out again.
     */
    @Column(name = "reserve_issue_note", length = 255)
    private String reserveIssueNote;

    /** Timestamp (epoch millis) of the newest stock entry reported by the API. */
    @Column(name = "source_timestamp")
    private Long sourceTimestamp;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    /**
     * When this (shop, product) was last <em>successfully observed</em> (i.e. a run produced a
     * definitive reading). Unlike {@link #lastCheckedAt}, which advances on every attempt, this only
     * advances when a reading was actually obtained. If {@code lastCheckedAt} is newer than this, the
     * most recent check(s) failed to read the article - it can no longer be updated (e.g. an error).
     */
    @Column(name = "last_observed_at")
    private Instant lastObservedAt;

    @Column(name = "last_changed_at")
    private Instant lastChangedAt;

    @Column(name = "last_available_at")
    private Instant lastAvailableAt;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    protected ProductAvailability() {
    }

    public ProductAvailability(Shop shop, Product product) {
        this.shop = shop;
        this.product = product;
    }

    public Long getId() {
        return id;
    }

    public Shop getShop() {
        return shop;
    }

    public Product getProduct() {
        return product;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(Integer currentStock) {
        this.currentStock = currentStock;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getReserveIssueNote() {
        return reserveIssueNote;
    }

    public void setReserveIssueNote(String reserveIssueNote) {
        this.reserveIssueNote = reserveIssueNote;
    }

    public Long getSourceTimestamp() {
        return sourceTimestamp;
    }

    public void setSourceTimestamp(Long sourceTimestamp) {
        this.sourceTimestamp = sourceTimestamp;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Instant getLastObservedAt() {
        return lastObservedAt;
    }

    public void setLastObservedAt(Instant lastObservedAt) {
        this.lastObservedAt = lastObservedAt;
    }

    public Instant getLastChangedAt() {
        return lastChangedAt;
    }

    public void setLastChangedAt(Instant lastChangedAt) {
        this.lastChangedAt = lastChangedAt;
    }

    public Instant getLastAvailableAt() {
        return lastAvailableAt;
    }

    public void setLastAvailableAt(Instant lastAvailableAt) {
        this.lastAvailableAt = lastAvailableAt;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }
}
