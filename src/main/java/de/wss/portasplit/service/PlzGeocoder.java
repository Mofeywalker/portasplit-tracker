package de.wss.portasplit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Offline German postal-code → coordinate lookup, backed by the bundled {@code plz-centroids.json}
 * (one median centroid per 5-digit PLZ, derived from the open zauberware postal-code dataset). Used by
 * the Umkreissuche so a user can set the radius centre by typing a PLZ instead of raw coordinates — no
 * network geocoding at runtime.
 */
@Component
public class PlzGeocoder {

    private static final Logger log = LoggerFactory.getLogger(PlzGeocoder.class);

    /** PLZ → {lat, lon}. Immutable after construction. */
    private final Map<String, double[]> centroids;

    public PlzGeocoder(ObjectMapper objectMapper) {
        Map<String, double[]> loaded = Map.of();
        try (InputStream in = new ClassPathResource("plz-centroids.json").getInputStream()) {
            loaded = objectMapper.readValue(in, new TypeReference<Map<String, double[]>>() {});
        } catch (Exception e) {
            log.error("Failed to load plz-centroids.json — PLZ geocoding for the radius search is disabled: {}",
                    e.getMessage());
        }
        this.centroids = loaded;
        log.info("PLZ geocoder ready: {} postal codes", centroids.size());
    }

    /** Normalizes to a 5-digit German PLZ (keeps leading zeros) or returns null if it isn't one. */
    public static String normalizePlz(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.trim().replaceAll("[^0-9]", "");
        return digits.length() == 5 ? digits : null;
    }

    /** {@code {lat, lon}} centroid of the given PLZ, if known. */
    public Optional<double[]> coordinates(String plz) {
        String key = normalizePlz(plz);
        if (key == null) {
            return Optional.empty();
        }
        double[] c = centroids.get(key);
        return c == null ? Optional.empty() : Optional.of(new double[] {c[0], c[1]});
    }

    public boolean isLoaded() {
        return !centroids.isEmpty();
    }
}
