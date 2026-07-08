package de.wss.portasplit.domain;

import de.wss.portasplit.domain.TelegramSubscriber.Source;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link Source} as a plain string. Using a converter (instead of {@code @Enumerated}) stops
 * Hibernate from generating a {@code CHECK (source in (...))} constraint, which SQLite's {@code update}
 * DDL never alters — so a newly added enum value (e.g. MANUAL) would otherwise be rejected on an
 * existing database.
 */
@Converter(autoApply = false)
public class TelegramSubscriberSourceConverter implements AttributeConverter<Source, String> {

    @Override
    public String convertToDatabaseColumn(Source attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public Source convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Source.valueOf(dbData);
    }
}
