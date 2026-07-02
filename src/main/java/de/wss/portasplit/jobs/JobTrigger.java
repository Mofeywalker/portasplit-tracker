package de.wss.portasplit.jobs;

/** What caused a {@link CheckJob} to be enqueued. */
public enum JobTrigger {

    /** Enqueued by the periodic scheduler. */
    SCHEDULED("Automatisch"),
    /** Enqueued by the user via the "Jetzt prüfen" button / {@code POST /api/check}. */
    MANUAL("Manuell");

    private final String label;

    JobTrigger(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
