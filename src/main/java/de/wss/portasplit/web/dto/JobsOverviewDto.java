package de.wss.portasplit.web.dto;

import de.wss.portasplit.jobs.CheckJobService.JobsSnapshot;

import java.util.List;

/** Full {@code GET /api/jobs} payload: the per-source cards and the live queue. */
public record JobsOverviewDto(
        List<JobSourceDto> sources,
        List<JobRunDto> running,
        List<JobRunDto> queued
) {
    public static JobsOverviewDto from(JobsSnapshot snap) {
        return new JobsOverviewDto(
                snap.sources().stream().map(JobSourceDto::from).toList(),
                snap.running().stream().map(JobRunDto::from).toList(),
                snap.queued().stream().map(JobRunDto::from).toList());
    }
}
