package de.wss.portasplit.web.dto;

/**
 * Kleinanzeigen watcher settings for the dashboard. The watcher is enabled precisely when a search
 * URL is configured (via this setting or the {@code KLEINANZEIGEN_URL} env/config); no link disables it.
 *
 * @param url         the effective search-results URL (empty when none is configured)
 * @param enabled     whether the watcher is active (i.e. a URL is configured)
 * @param overridden  whether the URL comes from a runtime override set here (vs. the static config)
 */
public record KleinanzeigenSettingsDto(String url, boolean enabled, boolean overridden) {}
