package de.wss.portasplit.scheduler;

import de.wss.portasplit.chains.ChainCheckService;
import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.jobs.CheckJobService;
import de.wss.portasplit.jobs.JobTrigger;
import de.wss.portasplit.jobs.JobType;
import de.wss.portasplit.service.IntervalSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Schedules availability checks at a <em>randomized</em> interval (per source, between its configured
 * {@code min-interval-ms} and {@code max-interval-ms}) to avoid a fixed polling pattern.
 *
 * <p>Each tick only <strong>enqueues</strong> a job on the {@link CheckJobService} queue and returns
 * immediately; that source's own worker then runs it. The sources therefore run in parallel, but
 * runs of the <em>same</em> source never overlap (de-duplicated per source, so a slow run cannot let a
 * second of the same kind pile up).
 *
 * <p>All timers are registered unconditionally; the only gate is the source's per-source runtime
 * {@link CheckJobService#enabled(JobType) enabled} flag (toggleable from the dashboard). There is no
 * global pause - each source is switched on/off individually and runs fully independently.
 */
@Configuration
public class AvailabilityScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityScheduler.class);

    private final CheckJobService jobService;
    private final ChainCheckService chainCheckService;
    private final AppProperties props;
    private final IntervalSettings intervals;

    public AvailabilityScheduler(CheckJobService jobService, ChainCheckService chainCheckService,
                                 AppProperties props, IntervalSettings intervals) {
        this.jobService = jobService;
        this.chainCheckService = chainCheckService;
        this.props = props;
        this.intervals = intervals;
    }

    /**
     * Dedicated scheduler pool so each source's timer (Amazon, Lidl, kleinanzeigen and the per-chain
     * workers) keeps its own cadence instead of queuing behind the others on a single thread. The ticks only enqueue
     * (instant); the actual checks serialize on the {@link CheckJobService} worker. Picked up
     * automatically by Spring's scheduling as the context's TaskScheduler.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(6);
        scheduler.setThreadNamePrefix("poll-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // Register every source's timer unconditionally; whether a tick actually enqueues is decided
        // at runtime (jobService.enabled), so a source disabled in config can still be switched on from
        // the dashboard without a restart, and vice-versa.
        log.info("Per-source polling timers active (Amazon, Lidl, kleinanzeigen and the per-chain "
                + "workers); each source runs on its own parallel worker. CloakBrowser CDP: {}",
                props.cloakbrowser().cdpUrl());
        registrar.addTriggerTask(this::amazonTick, ctx -> nextExecution(JobType.AMAZON, ctx));
        registrar.addTriggerTask(this::lidlTick, ctx -> nextExecution(JobType.LIDL, ctx));
        registrar.addTriggerTask(this::kleinanzeigenTick, ctx -> nextExecution(JobType.KLEINANZEIGEN, ctx));

        // One timer per chain worker (OBI, toom, Globus, …) - same model: each enqueues onto its own
        // parallel worker and is gated only by its per-source enabled flag.
        for (JobType chain : chainCheckService.jobTypes()) {
            registrar.addTriggerTask(() -> chainTick(chain), ctx -> nextExecution(chain, ctx));
        }
    }

    private void chainTick(JobType type) {
        if (jobService.enabled(type)) {
            jobService.submit(type, JobTrigger.SCHEDULED);
        }
    }

    /**
     * Next run for a source, using its current effective interval ({@link IntervalSettings}, i.e. the
     * dashboard override where set, otherwise the static config default). Read fresh on every tick, so a
     * changed interval takes effect from the next scheduled run without a restart.
     */
    private Instant nextExecution(JobType type, TriggerContext context) {
        IntervalSettings.IntervalConfig cfg = intervals.effective(type);
        Instant lastCompletion = context.lastCompletion();
        Instant next = (lastCompletion == null)
                ? Instant.now().plusMillis(cfg.initialDelayMs())
                : lastCompletion.plusMillis(randomDelay(cfg.minIntervalMs(), cfg.maxIntervalMs()));
        jobService.setNextRunAt(type, next);
        return next;
    }

    private void amazonTick() {
        if (jobService.enabled(JobType.AMAZON)) {
            jobService.submit(JobType.AMAZON, JobTrigger.SCHEDULED);
        }
    }

    private void lidlTick() {
        if (jobService.enabled(JobType.LIDL)) {
            jobService.submit(JobType.LIDL, JobTrigger.SCHEDULED);
        }
    }

    private void kleinanzeigenTick() {
        if (jobService.enabled(JobType.KLEINANZEIGEN)) {
            jobService.submit(JobType.KLEINANZEIGEN, JobTrigger.SCHEDULED);
        }
    }

    private static long randomDelay(long minMs, long maxMs) {
        long min = minMs;
        long max = Math.max(min, maxMs);
        return (min == max) ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}
