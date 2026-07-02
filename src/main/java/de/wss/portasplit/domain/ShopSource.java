package de.wss.portasplit.domain;

import java.util.Locale;

/**
 * Where a shop's availability data comes from — i.e. which checker polls it. {@link #AMAZON} and
 * {@link #LIDL} shops are scraped from their product pages via a stealth browser; the remaining
 * values are the per-chain branch checkers (OBI, toom, …), each of which polls that chain's own
 * site/API for its branches and online shop.
 */
public enum ShopSource {

    AMAZON,
    LIDL,
    OBI,
    TOOM,
    GLOBUS,
    HAGEBAU,
    HORNBACH,
    BAUHAUS;

    /**
     * Lenient parse of a stored/config source string. Returns {@code null} for a blank or unknown
     * value so callers can fall back (e.g. derive the source from the shop's chain).
     */
    public static ShopSource fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Maps a chain name (as used in {@code Shop.chain}) to the per-chain source whose own checker
     * polls that chain's branches directly. Returns {@code null} for chains we don't check per-chain.
     */
    public static ShopSource forChain(String chain) {
        if (chain == null) {
            return null;
        }
        return switch (chain.trim().toLowerCase(Locale.ROOT)) {
            case "obi" -> OBI;
            case "toom" -> TOOM;
            case "globus baumarkt", "globus" -> GLOBUS;
            case "hagebau" -> HAGEBAU;
            case "hornbach" -> HORNBACH;
            case "bauhaus" -> BAUHAUS;
            default -> null;
        };
    }

    /**
     * Resolves the effective source for a shop: the explicit {@code source} if given and valid,
     * otherwise the per-chain source derived from {@code chain}. Returns {@code null} when neither
     * resolves (an unknown chain with no explicit source, which no checker can handle).
     */
    public static ShopSource resolve(String source, String chain) {
        ShopSource explicit = fromString(source);
        return explicit != null ? explicit : forChain(chain);
    }
}
