package de.wss.portasplit.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Thread-bound sink that lets the low-level scrapers ({@link de.wss.portasplit.amazon.CloakBrowserClient}
 * and the per-source clients) record technical log lines into the {@link CheckJob} currently running on
 * this thread - without having to thread a job object through every method call.
 *
 * <p>The {@link CheckJobService} worker calls {@link #begin(CheckJob)} before running a job and
 * {@link #end()} afterwards. Any {@code info/warn/...} call in between is appended to that job's logbook
 * <em>and</em> mirrored to slf4j (so the server log keeps its familiar output). Calls made off the
 * worker thread (no bound job) just fall through to slf4j.
 *
 * <p>Messages use the usual slf4j {@code {}} placeholder syntax.
 */
@Component
public class JobLogger {

    private static final Logger log = LoggerFactory.getLogger("de.wss.portasplit.jobs.log");

    private final ThreadLocal<CheckJob> current = new ThreadLocal<>();

    /** Binds {@code job} as the sink for the current thread. */
    public void begin(CheckJob job) {
        current.set(job);
    }

    /** Unbinds the current thread's job. Always call from a {@code finally} block. */
    public void end() {
        current.remove();
    }

    /** The job bound to this thread, or {@code null} if none. */
    public CheckJob currentJob() {
        return current.get();
    }

    public void debug(String message, Object... args) {
        emit(JobLogEntry.Level.DEBUG, message, args);
    }

    public void info(String message, Object... args) {
        emit(JobLogEntry.Level.INFO, message, args);
    }

    public void warn(String message, Object... args) {
        emit(JobLogEntry.Level.WARN, message, args);
    }

    public void error(String message, Object... args) {
        emit(JobLogEntry.Level.ERROR, message, args);
    }

    private void emit(JobLogEntry.Level level, String message, Object... args) {
        String text = (args == null || args.length == 0)
                ? message
                : MessageFormatter.arrayFormat(message, args).getMessage();

        CheckJob job = current.get();
        if (job != null) {
            job.addLog(new JobLogEntry(Instant.now(), level, text));
        }

        switch (level) {
            case DEBUG -> log.debug(text);
            case INFO -> log.info(text);
            case WARN -> log.warn(text);
            case ERROR -> log.error(text);
        }
    }
}
