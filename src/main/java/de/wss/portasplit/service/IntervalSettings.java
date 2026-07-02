package de.wss.portasplit.service;

import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.jobs.JobType;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Runtime-editable poll intervals per source, backed by the database. Every {@link JobType source}'s
 * randomized poll window (min/max interval) and its post-startup initial delay can be overridden from
 * the dashboard; where no override is stored the static {@code app.*} configuration is used as the
 * default. Consulted by the {@code AvailabilityScheduler} on every tick, so a change takes effect from
 * the next scheduled run without a restart.
 *
 * <p>Persisted as three {@link SettingsService} keys per source
 * ({@code interval.<JOBTYPE>.min|max|initial}).
 */
@Service
public class IntervalSettings {

    /** Guardrail: never poll faster than this, whatever the dashboard requests. */
    public static final long MIN_INTERVAL_FLOOR_MS = 5_000L;

    private static final String PREFIX = "interval.";

    private final SettingsService settings;
    private final AppProperties props;

    public IntervalSettings(SettingsService settings, AppProperties props) {
        this.settings = settings;
        this.props = props;
    }

    /** A source's randomized poll window ({@code [min, max]}) plus its post-startup initial delay (ms). */
    public record IntervalConfig(long minIntervalMs, long maxIntervalMs, long initialDelayMs) {}

    /**
     * Effective interval for a source: the dashboard override for each field where present, otherwise
     * the static config default. Clamped defensively so {@code min >= floor}, {@code max >= min} and
     * {@code initial >= 0} even if a stored pair became inconsistent.
     */
    public IntervalConfig effective(JobType type) {
        IntervalConfig d = defaults(type);
        long min = Math.max(MIN_INTERVAL_FLOOR_MS, overrideMs(type, "min").orElse(d.minIntervalMs()));
        long max = Math.max(min, overrideMs(type, "max").orElse(d.maxIntervalMs()));
        long initial = Math.max(0, overrideMs(type, "initial").orElse(d.initialDelayMs()));
        return new IntervalConfig(min, max, initial);
    }

    /** The static {@code app.*} default interval for a source (ignoring any dashboard override). */
    public IntervalConfig defaults(JobType type) {
        return switch (type) {
            case AMAZON -> of(props.amazon().minIntervalMs(), props.amazon().maxIntervalMs(),
                    props.amazon().initialDelayMs());
            case LIDL -> of(props.lidl().minIntervalMs(), props.lidl().maxIntervalMs(),
                    props.lidl().initialDelayMs());
            case KLEINANZEIGEN -> of(props.kleinanzeigen().minIntervalMs(), props.kleinanzeigen().maxIntervalMs(),
                    props.kleinanzeigen().initialDelayMs());
            case OBI -> chain(props.obi());
            case TOOM -> chain(props.toom());
            case GLOBUS -> chain(props.globus());
            case HAGEBAU -> chain(props.hagebau());
            case HORNBACH -> chain(props.hornbach());
            case BAUHAUS -> chain(props.bauhaus());
        };
    }

    /** Whether any of a source's interval fields has a stored dashboard override. */
    public boolean customized(JobType type) {
        return overrideMs(type, "min").isPresent()
                || overrideMs(type, "max").isPresent()
                || overrideMs(type, "initial").isPresent();
    }

    /**
     * Applies a partial update: each non-null field replaces that override, unspecified fields keep
     * their current effective value. Validates the resulting combination and persists all three fields.
     *
     * @throws IllegalArgumentException if the resulting values are inconsistent (min below the floor,
     *                                  max below min, or a negative initial delay).
     */
    public IntervalConfig update(JobType type, Long minIntervalMs, Long maxIntervalMs, Long initialDelayMs) {
        IntervalConfig cur = effective(type);
        long min = minIntervalMs != null ? minIntervalMs : cur.minIntervalMs();
        long max = maxIntervalMs != null ? maxIntervalMs : cur.maxIntervalMs();
        long initial = initialDelayMs != null ? initialDelayMs : cur.initialDelayMs();

        if (min < MIN_INTERVAL_FLOOR_MS) {
            throw new IllegalArgumentException(
                    "Mindestintervall darf nicht unter " + (MIN_INTERVAL_FLOOR_MS / 1000) + " s liegen");
        }
        if (max < min) {
            throw new IllegalArgumentException("Maximalintervall darf nicht kleiner als das Mindestintervall sein");
        }
        if (initial < 0) {
            throw new IllegalArgumentException("Startverzögerung darf nicht negativ sein");
        }

        settings.set(key(type, "min"), String.valueOf(min));
        settings.set(key(type, "max"), String.valueOf(max));
        settings.set(key(type, "initial"), String.valueOf(initial));
        return new IntervalConfig(min, max, initial);
    }

    /** Removes all dashboard overrides for a source, so it falls back to the static config default. */
    public IntervalConfig reset(JobType type) {
        settings.set(key(type, "min"), null);
        settings.set(key(type, "max"), null);
        settings.set(key(type, "initial"), null);
        return defaults(type);
    }

    private Optional<Long> overrideMs(JobType type, String field) {
        return settings.get(key(type, field)).map(v -> {
            try {
                return Long.parseLong(v.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    private static String key(JobType type, String field) {
        return PREFIX + type.name() + "." + field;
    }

    private static IntervalConfig chain(AppProperties.Chain c) {
        return of(c.minIntervalMs(), c.maxIntervalMs(), c.initialDelayMs());
    }

    private static IntervalConfig of(long min, long max, long initial) {
        return new IntervalConfig(min, max, initial);
    }
}
