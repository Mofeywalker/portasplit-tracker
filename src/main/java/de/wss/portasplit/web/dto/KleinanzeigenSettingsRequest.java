package de.wss.portasplit.web.dto;

/**
 * Update request for the kleinanzeigen watcher URL. A blank/null {@code url} clears the override,
 * falling back to the static config (which may itself be empty → the watcher is then disabled).
 */
public record KleinanzeigenSettingsRequest(String url) {}
