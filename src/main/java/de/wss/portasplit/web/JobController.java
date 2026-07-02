package de.wss.portasplit.web;

import de.wss.portasplit.jobs.CheckJob;
import de.wss.portasplit.jobs.CheckJobService;
import de.wss.portasplit.jobs.JobTrigger;
import de.wss.portasplit.jobs.JobType;
import de.wss.portasplit.web.dto.JobDetailDto;
import de.wss.portasplit.web.dto.JobLogLineDto;
import de.wss.portasplit.web.dto.JobsOverviewDto;
import de.wss.portasplit.web.dto.WorkerToggleRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes the check-job queue and its technical logbook to the dashboard:
 * <ul>
 *   <li>{@code GET /api/jobs} - per-source status cards + the running/queued jobs,</li>
 *   <li>{@code GET /api/jobs/log} - the merged, newest-first technical logbook,</li>
 *   <li>{@code GET /api/jobs/{id}} - a single job with its full log,</li>
 *   <li>{@code POST /api/jobs/{type}/enabled} - turn a single source on/off at runtime.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final CheckJobService jobService;

    public JobController(CheckJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public JobsOverviewDto jobs() {
        return JobsOverviewDto.from(jobService.snapshot());
    }

    @GetMapping("/log")
    public List<JobLogLineDto> log(@RequestParam(defaultValue = "200") int limit) {
        int capped = Math.min(Math.max(limit, 1), 1000);
        return jobService.recentLog(capped).stream().map(JobLogLineDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDetailDto> job(@PathVariable long id) {
        CheckJob job = jobService.job(id);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(JobDetailDto.from(job));
    }

    /**
     * Enables or disables a single source at runtime (persisted, no restart needed) and returns the
     * refreshed overview so the dashboard can update every card in one round-trip.
     */
    @PostMapping("/{type}/enabled")
    public JobsOverviewDto setEnabled(@PathVariable JobType type, @RequestBody WorkerToggleRequest req) {
        jobService.setEnabled(type, req.enabled());
        return JobsOverviewDto.from(jobService.snapshot());
    }

    /**
     * Manually triggers a single source now (its own worker enqueues the job and runs it in the
     * background). Returns the refreshed overview so the card flips to "queued/running" immediately.
     */
    @PostMapping("/{type}/check")
    public JobsOverviewDto check(@PathVariable JobType type) {
        jobService.submit(type, JobTrigger.MANUAL);
        return JobsOverviewDto.from(jobService.snapshot());
    }
}
