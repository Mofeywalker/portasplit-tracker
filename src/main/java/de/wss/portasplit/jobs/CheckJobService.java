package de.wss.portasplit.jobs;

import de.wss.portasplit.chains.ChainCheckService;
import de.wss.portasplit.service.AmazonAvailabilityService;
import de.wss.portasplit.service.KleinanzeigenService;
import de.wss.portasplit.service.LidlAvailabilityService;
import de.wss.portasplit.service.ScrapeCheckResult;
import de.wss.portasplit.service.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central job queue for all availability checks. Every check - whether triggered by the scheduler or
 * manually from the dashboard - is enqueued as a {@link CheckJob}. Each {@link JobType source} has its
 * <strong>own single-thread worker</strong>, so the sources run <strong>in parallel</strong>,
 * while runs of the <em>same</em> source still execute strictly first-in-first-out (a slow run only
 * delays the next run of that same source, never the others). True browser-level parallelism comes
 * from each source driving its own CloakBrowser stealth context (see {@code CloakBrowserClient}).
 *
 * <p>Submitting is non-blocking: {@link #submit} returns immediately with the queued job, so the HTTP
 * request thread is never held for the duration of a (potentially ~45 s) scrape. While a job runs, the
 * scrapers append technical log lines to it via {@link JobLogger} (thread-bound, so the parallel
 * workers never cross-contaminate each other's logs), building the dashboard's logbook.
 *
 * <p>De-duplication: if a job of the same {@link JobType} is already waiting in the queue, submitting
 * again returns the existing one instead of piling up duplicates (so a fast-firing timer cannot flood
 * the queue while that source's worker is busy).
 *
 * <p>Each source can be enabled/disabled at runtime from the dashboard ({@link #setEnabled}); the
 * flag is persisted and consulted by the scheduler on every tick, so toggling needs no restart.
 *
 * <p>State is kept in memory (a bounded ring of recent finished jobs plus the running/queued jobs),
 * a transient pattern that avoids extra writes to the single-writer SQLite database.
 */
@Service
public class CheckJobService {

    private static final Logger log = LoggerFactory.getLogger(CheckJobService.class);

    /** How many finished jobs (with their full logbooks) to retain for the dashboard. */
    private static final int RECENT_MAX = 60;

    private final AmazonAvailabilityService amazonService;
    private final LidlAvailabilityService lidlService;
    private final KleinanzeigenService kleinanzeigenService;
    private final ChainCheckService chainCheckService;
    private final JobLogger jobLogger;
    private final SettingsService settings;

    /**
     * One single-thread worker per source, so the sources execute in parallel instead of behind
     * a single global worker. Within a source, runs still serialize (FIFO).
     */
    private final Map<JobType, ExecutorService> workers = new EnumMap<>(JobType.class);
    private final AtomicLong seq = new AtomicLong();

    private final Object lock = new Object();
    private final Deque<CheckJob> queued = new ArrayDeque<>();
    private final Deque<CheckJob> recent = new ArrayDeque<>();
    /** Currently-running job per source (at most one per type). Guarded by {@link #lock}. */
    private final Map<JobType, CheckJob> running = new EnumMap<>(JobType.class);

    private final Map<JobType, CheckJob> lastByType = new ConcurrentHashMap<>();
    private final Map<JobType, Instant> nextRunAt = new ConcurrentHashMap<>();

    public CheckJobService(AmazonAvailabilityService amazonService,
                           LidlAvailabilityService lidlService,
                           KleinanzeigenService kleinanzeigenService,
                           ChainCheckService chainCheckService,
                           JobLogger jobLogger,
                           SettingsService settings) {
        this.amazonService = amazonService;
        this.lidlService = lidlService;
        this.kleinanzeigenService = kleinanzeigenService;
        this.chainCheckService = chainCheckService;
        this.jobLogger = jobLogger;
        this.settings = settings;
        for (JobType type : JobType.values()) {
            workers.put(type, Executors.newSingleThreadExecutor(namedWorker(type)));
        }
    }

    /**
     * Enqueues a check of {@code type}. Returns the queued job immediately (non-blocking). If a job of
     * the same type is already waiting, that existing job is returned instead of enqueuing a duplicate.
     */
    public CheckJob submit(JobType type, JobTrigger trigger) {
        CheckJob job;
        synchronized (lock) {
            for (CheckJob q : queued) {
                if (q.type() == type) {
                    return q;
                }
            }
            job = new CheckJob(seq.incrementAndGet(), type, trigger, Instant.now());
            queued.addLast(job);
        }
        // Submit to this source's own worker (outside the lock): each source runs on its own thread,
        // so the sources execute in parallel while same-source runs stay strictly sequential.
        workers.get(type).submit(() -> execute(job));
        log.debug("Enqueued {} job #{} ({})", type, job.id(), trigger);
        return job;
    }

    /**
     * Runs {@code task} synchronously on the calling thread inside a one-off {@link CheckJob} context so
     * its {@link JobLogger} lines land in the dashboard logbook - used for the toom login, which is
     * triggered from an HTTP request thread that otherwise has no bound job. Tagged with {@code type}
     * and kept in the recent-jobs ring like a normal run, but NOT placed in the per-type running/last
     * maps, so it never disturbs that source's scheduler cards or scrape cadence.
     */
    public CheckJob runInline(JobType type, JobTrigger trigger, String startMessage, Runnable task) {
        CheckJob job = new CheckJob(seq.incrementAndGet(), type, trigger, Instant.now());
        job.markRunning(Instant.now());
        jobLogger.begin(job);
        JobState state = JobState.SUCCESS;
        String error = null;
        try {
            if (startMessage != null) {
                jobLogger.info(startMessage);
            }
            task.run();
        } catch (Throwable t) {
            state = JobState.FAILED;
            error = t.getMessage() != null ? t.getMessage() : t.toString();
            jobLogger.error("Fehler: {}", error);
        } finally {
            jobLogger.end();
        }
        job.finish(state, Instant.now(), null, error);
        synchronized (lock) {
            recent.addFirst(job);
            while (recent.size() > RECENT_MAX) {
                recent.removeLast();
            }
        }
        return job;
    }

    /** Enqueues every currently-enabled source. Used by the manual "Jetzt prüfen" trigger. */
    public List<CheckJob> submitAllEnabled(JobTrigger trigger) {
        List<CheckJob> jobs = new ArrayList<>();
        for (JobType type : JobType.values()) {
            if (enabled(type)) {
                jobs.add(submit(type, trigger));
            }
        }
        return jobs;
    }

    /**
     * Whether a source is currently enabled (and therefore worth scheduling/queuing). A per-source
     * runtime override set from the dashboard (persisted via {@link SettingsService}) takes precedence
     * over the static {@code app.*} configuration, so workers can be switched on/off without a restart.
     */
    public boolean enabled(JobType type) {
        // Kleinanzeigen is structurally unavailable without a configured search URL: it then stays off
        // regardless of any runtime worker override (no link → disabled).
        if (type == JobType.KLEINANZEIGEN && !kleinanzeigenService.hasUrl()) {
            return false;
        }
        Optional<String> override = settings.get(SettingsService.WORKER_ENABLED_PREFIX + type.name());
        if (override.isPresent()) {
            return Boolean.parseBoolean(override.get());
        }
        if (chainCheckService.handles(type)) {
            return chainCheckService.enabled(type);
        }
        return switch (type) {
            case AMAZON -> amazonService.enabled();
            case LIDL -> lidlService.enabled();
            case KLEINANZEIGEN -> kleinanzeigenService.enabled();
            default -> false;
        };
    }

    /**
     * Enables or disables a source at runtime (persisted). The change takes effect immediately for
     * scheduling and manual checks; a job already <em>queued</em> for that source is skipped when its
     * worker reaches it (re-checked in {@link #execute}), while a job already <em>mid-run</em> finishes.
     */
    public void setEnabled(JobType type, boolean enabled) {
        settings.putBool(SettingsService.WORKER_ENABLED_PREFIX + type.name(), enabled);
        log.info("Worker {} {} via dashboard", type, enabled ? "enabled" : "disabled");
    }

    /** Records when a source is next scheduled to run, for the dashboard countdown. */
    public void setNextRunAt(JobType type, Instant when) {
        if (when != null) {
            nextRunAt.put(type, when);
        }
    }

    public Long nextRunInMs(JobType type) {
        Instant when = nextRunAt.get(type);
        if (when == null) {
            return null;
        }
        return Math.max(0, when.toEpochMilli() - Instant.now().toEpochMilli());
    }

    // --- execution -------------------------------------------------------------------------------

    private void execute(CheckJob job) {
        synchronized (lock) {
            queued.remove(job);
            running.put(job.type(), job);
        }
        job.markRunning(Instant.now());
        jobLogger.begin(job);

        JobState finalState = JobState.FAILED;
        String summary = null;
        String error = null;
        try {
            // Re-check at run time: the source may have been disabled (dashboard toggle) after this job
            // was enqueued but before its worker picked it up. Skip rather than run a now-disabled source.
            if (!enabled(job.type())) {
                finalState = JobState.SKIPPED;
                summary = "Quelle inzwischen deaktiviert";
                jobLogger.info("Prüfung „{}“ übersprungen - Quelle wurde deaktiviert", job.type().label());
            } else {
                jobLogger.info("Prüfung „{}“ gestartet ({})", job.type().label(), job.trigger().label());
                Outcome outcome = dispatch(job.type());
                finalState = outcome.state();
                summary = outcome.summary();
                error = outcome.error();
                if (summary != null) {
                    jobLogger.info("Fertig: {}", summary);
                }
                if (error != null && !error.isBlank()) {
                    jobLogger.warn("Probleme: {}", error);
                }
            }
        } catch (Throwable t) {
            error = t.getMessage() != null ? t.getMessage() : t.toString();
            jobLogger.error("Unerwarteter Fehler: {}", error);
            log.error("Job #{} ({}) threw", job.id(), job.type(), t);
        } finally {
            jobLogger.end();
        }

        job.finish(finalState, Instant.now(), summary, error);
        synchronized (lock) {
            running.remove(job.type());
            lastByType.put(job.type(), job);
            recent.addFirst(job);
            while (recent.size() > RECENT_MAX) {
                recent.removeLast();
            }
        }
    }

    /** Runs the actual source check and maps its result onto a queue {@link Outcome}. */
    private Outcome dispatch(JobType type) {
        if (chainCheckService.handles(type)) {
            return scrapeOutcome(chainCheckService.runCheck(type), "Filiale(n)", "verfügbar");
        }
        return switch (type) {
            case AMAZON -> scrapeOutcome(amazonService.runCheck(), "Produkt(e)", "verfügbar");
            case LIDL -> scrapeOutcome(lidlService.runCheck(), "Produkt(e)", "verfügbar");
            case KLEINANZEIGEN -> scrapeOutcome(kleinanzeigenService.runCheck(), "Anzeige(n)", "neu gemeldet");
            default -> new Outcome(JobState.SKIPPED, "unbekannte Quelle", null);
        };
    }

    private Outcome scrapeOutcome(ScrapeCheckResult r, String scannedNoun, String availableVerb) {
        if (!r.ran()) {
            String reason = r.errors().isEmpty() ? "übersprungen" : String.join("; ", r.errors());
            return new Outcome(JobState.SKIPPED, reason, null);
        }
        String summary = String.format("%d %s geprüft · %d %s",
                r.scanned(), scannedNoun, r.available(), availableVerb);
        if (r.errors().isEmpty()) {
            return new Outcome(JobState.SUCCESS, summary, null);
        }
        String error = String.join("; ", r.errors());
        // Some progress despite errors → partial (WARN); nothing read at all → FAILED.
        JobState state = r.scanned() > 0 ? JobState.WARN : JobState.FAILED;
        return new Outcome(state, summary, error);
    }

    private record Outcome(JobState state, String summary, String error) {}

    // --- read side (dashboard) -------------------------------------------------------------------

    /** Snapshot of every source's current state plus the running/queued jobs. */
    public JobsSnapshot snapshot() {
        // Resolve the enabled-state (a DB read) and countdowns outside the in-memory lock, so the
        // frequently-polled snapshot never holds the lock across a database call.
        Map<JobType, Boolean> enabledByType = new EnumMap<>(JobType.class);
        Map<JobType, Long> nextByType = new EnumMap<>(JobType.class);
        for (JobType type : JobType.values()) {
            enabledByType.put(type, enabled(type));
            nextByType.put(type, nextRunInMs(type));
        }
        synchronized (lock) {
            List<SourceStatus> sources = new ArrayList<>(JobType.values().length);
            for (JobType type : JobType.values()) {
                boolean isRunning = running.containsKey(type);
                boolean isQueued = !isRunning && hasQueued(type);
                sources.add(new SourceStatus(type, enabledByType.get(type), isRunning, isQueued,
                        lastByType.get(type), nextByType.get(type)));
            }
            return new JobsSnapshot(sources, new ArrayList<>(running.values()),
                    new ArrayList<>(queued));
        }
    }

    private boolean hasQueued(JobType type) {
        for (CheckJob q : queued) {
            if (q.type() == type) {
                return true;
            }
        }
        return false;
    }

    /** A flattened, newest-first view of every retained job's log lines for the logbook. */
    public List<LogLine> recentLog(int limit) {
        List<CheckJob> jobs = new ArrayList<>();
        synchronized (lock) {
            // Read `running` and `recent` together under the lock: a finishing job is moved from
            // `running` to `recent` atomically (same synchronized block in execute()), so the two are
            // disjoint here - concatenating them never duplicates a job's log lines.
            jobs.addAll(running.values());
            jobs.addAll(recent);
        }
        List<LogLine> lines = new ArrayList<>();
        for (CheckJob job : jobs) {
            for (JobLogEntry e : job.logSnapshot()) {
                lines.add(new LogLine(job.id(), job.type(), e));
            }
        }
        lines.sort(Comparator.comparing((LogLine l) -> l.entry().at()).reversed());
        if (lines.size() > limit) {
            return new ArrayList<>(lines.subList(0, limit));
        }
        return lines;
    }

    /** Looks up a single job (running, queued or recently finished) by id. */
    public CheckJob job(long id) {
        synchronized (lock) {
            for (CheckJob run : running.values()) {
                if (run.id() == id) {
                    return run;
                }
            }
            for (CheckJob q : queued) {
                if (q.id() == id) {
                    return q;
                }
            }
            for (CheckJob r : recent) {
                if (r.id() == id) {
                    return r;
                }
            }
        }
        return null;
    }

    /** Per-source status for the dashboard cards. */
    public record SourceStatus(JobType type, boolean enabled, boolean running, boolean queued,
                               CheckJob lastRun, Long nextRunInMs) {}

    /**
     * Full queue snapshot: per-source cards, the jobs currently running (one per source at most, so
     * they run in parallel) and the waiting queue.
     */
    public record JobsSnapshot(List<SourceStatus> sources, List<CheckJob> running,
                               List<CheckJob> queued) {}

    /** One logbook line tagged with the job it belongs to. */
    public record LogLine(long jobId, JobType type, JobLogEntry entry) {}

    @PreDestroy
    void shutdown() {
        workers.values().forEach(ExecutorService::shutdownNow);
    }

    private static ThreadFactory namedWorker(JobType type) {
        String name = "check-worker-" + type.name().toLowerCase();
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
