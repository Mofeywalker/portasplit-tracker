package de.wss.portasplit.service;

import de.wss.portasplit.domain.Shop;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.distance.DistanceUtils;
import org.locationtech.spatial4j.shape.Point;
import org.springframework.stereotype.Service;

/**
 * The Umkreissuche (radius search). Every chain branch is always scraped; this service decides, for a
 * configurable centre + radius, which branches are <em>in scope</em> - i.e. shown on the dashboard and
 * allowed to fire Telegram alerts. The geodesic distance is computed with Spatial4j.
 *
 * <p>The very same {@link #inScope} rule is consulted by both the dashboard display filter
 * ({@code DashboardService}) and the notification gate ({@code AvailabilityReconciler}), so the two can
 * never drift apart. Online shops are always in scope (nationwide delivery is unaffected by a radius),
 * and when the radius is inactive everything is in scope.
 */
@Service
public class RadiusService {

    private final SettingsService settings;
    private final SpatialContext geo = SpatialContext.GEO;

    public RadiusService(SettingsService settings) {
        this.settings = settings;
    }

    /**
     * Current radius configuration. {@code active()} is the gate used everywhere: a radius only filters
     * once it is switched on, has a resolved centre and a positive distance.
     */
    public record RadiusConfig(boolean enabled, double km, Double centerLat, Double centerLon, String label) {
        public boolean active() {
            return enabled && centerLat != null && centerLon != null && km > 0;
        }
    }

    /** Reads the persisted radius settings in one shot (use this once, then reuse for a batch of shops). */
    public RadiusConfig config() {
        boolean enabled = settings.get(SettingsService.RADIUS_ENABLED).map(Boolean::parseBoolean).orElse(false);
        double km = settings.get(SettingsService.RADIUS_KM).map(RadiusService::parseDoubleOr0).orElse(0.0);
        Double lat = settings.get(SettingsService.RADIUS_CENTER_LAT).map(Double::valueOf).orElse(null);
        Double lon = settings.get(SettingsService.RADIUS_CENTER_LON).map(Double::valueOf).orElse(null);
        String label = settings.get(SettingsService.RADIUS_CENTER_LABEL).orElse(null);
        return new RadiusConfig(enabled, km, lat, lon, label);
    }

    /** Great-circle distance (km) from the centre to a point, or {@code null} if either is unknown. */
    public Double distanceKm(RadiusConfig cfg, Double lat, Double lon) {
        if (cfg.centerLat() == null || cfg.centerLon() == null || lat == null || lon == null) {
            return null;
        }
        Point center = geo.getShapeFactory().pointLatLon(cfg.centerLat(), cfg.centerLon());
        Point target = geo.getShapeFactory().pointLatLon(lat, lon);
        return geo.calcDistance(center, target) * DistanceUtils.DEG_TO_KM;
    }

    /** Convenience overload that reads the config first (single-shot use, e.g. one notification). */
    public Double distanceKm(Double lat, Double lon) {
        return distanceKm(config(), lat, lon);
    }

    /**
     * Whether a shop is in scope under the given config: online shops always are; with the radius
     * inactive everything is; otherwise a branch must have coordinates within {@code km} of the centre.
     * A branch without coordinates is out of scope while a radius is active (it cannot be placed).
     */
    public boolean inScope(RadiusConfig cfg, Shop shop) {
        if (shop.isOnlineOnly()) {
            return true;
        }
        if (!cfg.active()) {
            return true;
        }
        Double d = distanceKm(cfg, shop.getLat(), shop.getLon());
        return d != null && d <= cfg.km();
    }

    /** Convenience overload reading the config first; for one-off checks (notification gate). */
    public boolean inScope(Shop shop) {
        return inScope(config(), shop);
    }

    private static double parseDoubleOr0(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
