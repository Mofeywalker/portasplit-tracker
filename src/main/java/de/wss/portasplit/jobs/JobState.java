package de.wss.portasplit.jobs;

/** Lifecycle state of a single {@link CheckJob}. */
public enum JobState {

    /** Waiting in the queue for the worker to pick it up. */
    QUEUED,
    /** Currently being executed by the worker thread. */
    RUNNING,
    /** Finished cleanly. */
    SUCCESS,
    /** Finished, but the run reported one or more recoverable problems (partial result). */
    WARN,
    /** Failed: the source could not be read at all. */
    FAILED,
    /** Did not actually run (source disabled or nothing to do). */
    SKIPPED
}
