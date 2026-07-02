package de.wss.portasplit.web.dto;

import de.wss.portasplit.jobs.CheckJobService.SourceStatus;

/**
 * Per-source status card for the dashboard: the current high-level {@code state} (IDLE / QUEUED /
 * RUNNING / SUCCESS / WARN / FAILED / SKIPPED / DISABLED), the last finished run, and the countdown to
 * the next scheduled run.
 */
public record JobSourceDto(
        String type,
        String typeLabel,
        String subtitle,
        boolean enabled,
        String state,
        boolean running,
        boolean queued,
        JobRunDto lastRun,
        Long nextRunInMs
) {
    public static JobSourceDto from(SourceStatus s) {
        String state;
        if (s.running()) {
            state = "RUNNING";
        } else if (s.queued()) {
            state = "QUEUED";
        } else if (!s.enabled()) {
            // Disabled wins over any prior run, so a paused source shows a calm "Deaktiviert" badge
            // instead of a stale red "Fehler" from before it was turned off.
            state = "DISABLED";
        } else if (s.lastRun() != null) {
            state = s.lastRun().state().name();
        } else {
            state = "IDLE";
        }
        return new JobSourceDto(
                s.type().name(),
                s.type().label(),
                s.type().subtitle(),
                s.enabled(),
                state,
                s.running(),
                s.queued(),
                s.lastRun() != null ? JobRunDto.from(s.lastRun()) : null,
                s.nextRunInMs());
    }
}
