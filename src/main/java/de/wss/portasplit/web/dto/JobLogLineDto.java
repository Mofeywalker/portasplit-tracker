package de.wss.portasplit.web.dto;

import de.wss.portasplit.jobs.CheckJobService.LogLine;

import java.time.Instant;

/** One line of the technical logbook, tagged with the job (and source) it came from. */
public record JobLogLineDto(
        long jobId,
        String type,
        String typeLabel,
        Instant at,
        String level,
        String message
) {
    public static JobLogLineDto from(LogLine l) {
        return new JobLogLineDto(
                l.jobId(),
                l.type().name(),
                l.type().label(),
                l.entry().at(),
                l.entry().level().name(),
                l.entry().message());
    }
}
