package de.wss.portasplit.jobs;

/**
 * The independent check sources, each of which becomes its own {@link CheckJob} on the queue and
 * runs on its own parallel worker. The {@code label} is the user-facing German name shown in the
 * dashboard's per-job cards. The chain sources (OBI…Bauhaus) check that chain's branches directly.
 */
public enum JobType {

    AMAZON("Amazon", "Amazon.de"),
    LIDL("Lidl", "Lidl.de"),
    KLEINANZEIGEN("Kleinanzeigen", "kleinanzeigen.de"),
    OBI("OBI", "OBI-Filialen"),
    TOOM("toom", "toom-Filialen"),
    GLOBUS("Globus", "Globus-Baumarkt-Filialen"),
    HAGEBAU("Hagebau", "Hagebau-Filialen"),
    HORNBACH("Hornbach", "Hornbach-Filialen"),
    BAUHAUS("Bauhaus", "Bauhaus-Filialen");

    private final String label;
    private final String subtitle;

    JobType(String label, String subtitle) {
        this.label = label;
        this.subtitle = subtitle;
    }

    public String label() {
        return label;
    }

    public String subtitle() {
        return subtitle;
    }
}
