package de.wss.portasplit.web.dto;

import de.wss.portasplit.jobs.CheckJob;

import java.time.Instant;

/** One check execution as exposed to the dashboard. */
public record JobRunDto(
        long id,
        String type,
        String typeLabel,
        String subtitle,
        String trigger,
        String triggerLabel,
        String state,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String summary,
        String error,
        String notice,
        int logCount
) {
    public static JobRunDto from(CheckJob job) {
        // Read the mutable lifecycle fields under the job's monitor so a running job is never mapped
        // mid-transition (matches the synchronized markRunning/finish in CheckJob). type/trigger/
        // queuedAt are immutable, but reading them here too keeps the snapshot in one place.
        synchronized (job) {
            return new JobRunDto(
                    job.id(),
                    job.type().name(),
                    job.type().label(),
                    job.type().subtitle(),
                    job.trigger().name(),
                    job.trigger().label(),
                    job.state().name(),
                    job.queuedAt(),
                    job.startedAt(),
                    job.finishedAt(),
                    job.durationMs(),
                    job.summary(),
                    job.error(),
                    job.notice(),
                    job.logCount());
        }
    }
}
