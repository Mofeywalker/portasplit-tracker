package de.wss.portasplit.web.dto;

/** Body of {@code POST /api/jobs/{type}/enabled}: turns a single check source on or off at runtime. */
public record WorkerToggleRequest(boolean enabled) {
}
