package de.wss.portasplit.web.dto;

import java.time.Instant;

/** Dashboard view of the toom auto-reserve feature state. Never carries the password. */
public record ToomReserveStatusDto(
        boolean featureEnabled,
        boolean cryptoConfigured,
        boolean hasCredentials,
        String email,
        boolean loggedIn,
        Instant lastLoginAt,
        String lastError) {
}
