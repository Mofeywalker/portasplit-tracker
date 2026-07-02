package de.wss.portasplit.jobs;

import java.time.Instant;

/**
 * A single line in a job's technical logbook: a timestamped, levelled message describing exactly what
 * happened during a check (navigation, Cloudflare challenge, parsed counts, …).
 */
public record JobLogEntry(Instant at, Level level, String message) {

    /** Severity of a log line; mirrors the usual slf4j levels for the dashboard's colour coding. */
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }
}
