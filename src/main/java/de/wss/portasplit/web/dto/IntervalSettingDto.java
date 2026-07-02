package de.wss.portasplit.web.dto;

import de.wss.portasplit.jobs.JobType;
import de.wss.portasplit.service.IntervalSettings;

/**
 * A single source's configurable poll interval for the settings page: the currently effective
 * min/max poll window and initial delay (all in ms), the static config defaults it falls back to, and
 * whether it currently carries a dashboard override.
 */
public record IntervalSettingDto(
        String type,
        String label,
        String subtitle,
        long minIntervalMs,
        long maxIntervalMs,
        long initialDelayMs,
        long defaultMinIntervalMs,
        long defaultMaxIntervalMs,
        long defaultInitialDelayMs,
        boolean customized
) {
    public static IntervalSettingDto of(JobType type, IntervalSettings intervals) {
        IntervalSettings.IntervalConfig eff = intervals.effective(type);
        IntervalSettings.IntervalConfig def = intervals.defaults(type);
        return new IntervalSettingDto(
                type.name(), type.label(), type.subtitle(),
                eff.minIntervalMs(), eff.maxIntervalMs(), eff.initialDelayMs(),
                def.minIntervalMs(), def.maxIntervalMs(), def.initialDelayMs(),
                intervals.customized(type));
    }
}
