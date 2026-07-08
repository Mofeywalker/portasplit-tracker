package de.wss.portasplit.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One execution of a check on the queue. Carries its lifecycle ({@link JobState}), timing, a
 * human-readable {@code summary}/{@code error}, and a structured technical {@link JobLogEntry log}.
 *
 * <p>Mutated by the single worker thread while it runs; read concurrently by the dashboard's HTTP
 * threads. The state-transition methods ({@link #markRunning}/{@link #finish}) and the multi-field
 * read used by the DTO mapper synchronize on {@code this}, so a dashboard read never observes a
 * half-applied transition (e.g. {@code state=RUNNING} together with a non-null {@code finishedAt}).
 * The fields stay {@code volatile} so any single-field read off the lock is still visible.
 */
public class CheckJob {

    private final long id;
    private final JobType type;
    private final JobTrigger trigger;
    private final Instant queuedAt;
    private final List<JobLogEntry> log = Collections.synchronizedList(new ArrayList<>());

    private volatile JobState state = JobState.QUEUED;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile String summary;
    private volatile String error;
    /** Non-fatal heads-up worth surfacing (e.g. "Artikelseite nicht mehr erreichbar"), or {@code null}. */
    private volatile String notice;

    public CheckJob(long id, JobType type, JobTrigger trigger, Instant queuedAt) {
        this.id = id;
        this.type = type;
        this.trigger = trigger;
        this.queuedAt = queuedAt;
    }

    public synchronized void markRunning(Instant when) {
        this.startedAt = when;
        this.state = JobState.RUNNING;
    }

    public synchronized void finish(JobState finalState, Instant when, String summary, String error) {
        finish(finalState, when, summary, error, null);
    }

    public synchronized void finish(JobState finalState, Instant when, String summary, String error, String notice) {
        this.state = finalState;
        this.finishedAt = when;
        this.summary = summary;
        this.error = error;
        this.notice = notice;
    }

    /** Appends one technical log line (thread-safe). */
    public void addLog(JobLogEntry entry) {
        log.add(entry);
    }

    /** A defensive copy of the log lines for safe reading off the worker thread. */
    public List<JobLogEntry> logSnapshot() {
        synchronized (log) {
            return new ArrayList<>(log);
        }
    }

    public int logCount() {
        return log.size();
    }

    public long id() {
        return id;
    }

    public JobType type() {
        return type;
    }

    public JobTrigger trigger() {
        return trigger;
    }

    public JobState state() {
        return state;
    }

    public Instant queuedAt() {
        return queuedAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public String summary() {
        return summary;
    }

    public String error() {
        return error;
    }

    public String notice() {
        return notice;
    }

    /** Wall-clock duration of the run, or {@code null} while not yet finished. */
    public synchronized Long durationMs() {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }
}
