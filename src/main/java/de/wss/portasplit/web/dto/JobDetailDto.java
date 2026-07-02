package de.wss.portasplit.web.dto;

import de.wss.portasplit.jobs.CheckJob;
import de.wss.portasplit.jobs.JobLogEntry;

import java.util.List;

/** A single job with its full technical logbook ({@code GET /api/jobs/{id}}). */
public record JobDetailDto(JobRunDto run, List<JobLogLineDto> log) {

    public static JobDetailDto from(CheckJob job) {
        List<JobLogLineDto> log = new java.util.ArrayList<>();
        for (JobLogEntry e : job.logSnapshot()) {
            log.add(new JobLogLineDto(job.id(), job.type().name(), job.type().label(),
                    e.at(), e.level().name(), e.message()));
        }
        return new JobDetailDto(JobRunDto.from(job), log);
    }
}
