package de.wss.portasplit.domain;

import de.wss.portasplit.domain.TelegramSubscriber.State;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link State} as a plain string. Using a converter (instead of {@code @Enumerated}) stops
 * Hibernate from generating a {@code CHECK (state in (...))} constraint, which SQLite's {@code update}
 * DDL never alters — so a newly added enum value would otherwise be rejected on an existing database.
 */
@Converter(autoApply = false)
public class TelegramSubscriberStateConverter implements AttributeConverter<State, String> {

    @Override
    public String convertToDatabaseColumn(State attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public State convertToEntityAttribute(String dbData) {
        return dbData == null ? null : State.valueOf(dbData);
    }
}
