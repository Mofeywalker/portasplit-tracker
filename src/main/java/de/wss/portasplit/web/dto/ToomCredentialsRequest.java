package de.wss.portasplit.web.dto;

/** Request to store toom account credentials (password is encrypted at rest, never logged). */
public record ToomCredentialsRequest(String email, String password) {
}
