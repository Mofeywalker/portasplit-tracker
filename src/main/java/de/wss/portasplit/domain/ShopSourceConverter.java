package de.wss.portasplit.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link ShopSource} as a plain string. Using a converter (instead of {@code @Enumerated})
 * stops Hibernate from generating a {@code CHECK (source in (...))} constraint, which would
 * otherwise reject newly added enum values on an existing SQLite database.
 */
@Converter(autoApply = false)
public class ShopSourceConverter implements AttributeConverter<ShopSource, String> {

    @Override
    public String convertToDatabaseColumn(ShopSource attribute) {
        if (attribute == null) {
            throw new IllegalStateException("Shop.source must not be null");
        }
        return attribute.name();
    }

    @Override
    public ShopSource convertToEntityAttribute(String dbData) {
        ShopSource source = ShopSource.fromString(dbData);
        if (source == null) {
            throw new IllegalStateException("Unknown shop source in database: " + dbData);
        }
        return source;
    }
}
