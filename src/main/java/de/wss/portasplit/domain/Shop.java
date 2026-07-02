package de.wss.portasplit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;

/**
 * A shop we track. A shop belongs to a chain (e.g. OBI, Toom, Bauhaus) and is either a physical
 * branch ({@code onlineOnly = false}) or the chain's own online shop ({@code onlineOnly = true}).
 * The per-chain worker checks branches per PLZ and the online shop via the chain's delivery signal.
 */
@Entity
@Table(name = "shop", uniqueConstraints = @UniqueConstraint(columnNames = "match_name"))
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chain / brand, e.g. "OBI", "Toom", "Bauhaus", "Hornbach", "Hagebau", "Globus Baumarkt". */
    @Column(nullable = false)
    private String chain;

    /** Human-readable display name, e.g. "OBI Montabaur". */
    @Column(nullable = false)
    private String name;

    /** Unique normalized key used to de-duplicate a store on merge. Normalized on write. */
    @Column(name = "match_name", nullable = false)
    private String matchName;

    private String city;
    private String plz;
    private String street;
    private Double lat;
    private Double lon;

    /** Pure online shop (ships nationwide); the chain's own online store rather than a physical branch. */
    @Column(name = "online_only", nullable = false)
    private boolean onlineOnly = false;

    /**
     * Which checker is responsible for this shop (a per-chain branch checker, Amazon or Lidl). Stored
     * via {@link ShopSourceConverter} so Hibernate does not emit a CHECK constraint that would reject
     * future enum values.
     */
    @Convert(converter = ShopSourceConverter.class)
    @Column(name = "source", nullable = false, length = 32)
    private ShopSource source;

    /** Whether this shop is actively polled. */
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Shop() {
    }

    /** Normalize a name for matching: trimmed, lower-cased, internal whitespace collapsed. */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public Long getId() {
        return id;
    }

    public String getChain() {
        return chain;
    }

    public void setChain(String chain) {
        this.chain = chain;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMatchName() {
        return matchName;
    }

    public void setMatchName(String matchName) {
        this.matchName = normalize(matchName);
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPlz() {
        return plz;
    }

    public void setPlz(String plz) {
        this.plz = plz;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public boolean isOnlineOnly() {
        return onlineOnly;
    }

    public void setOnlineOnly(boolean onlineOnly) {
        this.onlineOnly = onlineOnly;
    }

    public ShopSource getSource() {
        return source;
    }

    public void setSource(ShopSource source) {
        this.source = source;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
