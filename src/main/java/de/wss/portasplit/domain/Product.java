package de.wss.portasplit.domain;

/**
 * The two products we track.
 */
public enum Product {

    PORTASPLIT("Midea PortaSplit"),
    PORTASPLIT_COOL("Midea PortaSplit Cool");

    private final String displayName;

    Product(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
