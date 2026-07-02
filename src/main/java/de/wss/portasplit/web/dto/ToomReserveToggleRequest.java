package de.wss.portasplit.web.dto;

/** Request to switch the toom auto-reserve feature on or off. */
public record ToomReserveToggleRequest(Boolean enabled) {
}
