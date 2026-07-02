package de.wss.portasplit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-off schema fix-ups that Hibernate's {@code ddl-auto=update} cannot perform on SQLite.
 *
 * <p>Earlier versions mapped {@code Shop.source} with {@code @Enumerated}, which made Hibernate emit
 * a {@code CHECK (source in (...))} constraint. SQLite cannot drop a CHECK constraint in place and
 * {@code update} won't touch it, so adding a new source value fails. This rebuilds the {@code shop}
 * table without that constraint. Runs before {@link ShopInitializer}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaMigrations implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrations.class);

    private final DataSource dataSource;

    public SchemaMigrations(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            dropLegacySourceCheckConstraint(conn);
        } catch (Exception e) {
            log.error("Schema migration failed: {}", e.getMessage(), e);
        }
    }

    /** Rebuilds the shop table without the legacy {@code CHECK (source in (...))} constraint. */
    private void dropLegacySourceCheckConstraint(Connection conn) throws Exception {
        String ddl = shopTableDdl(conn);
        if (ddl == null || !ddl.toLowerCase().contains("check (source in")) {
            return; // already clean (or table not created yet)
        }
        log.info("Migrating: dropping legacy CHECK constraint on shop.source");
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys=OFF");
            st.execute("ALTER TABLE shop RENAME TO shop__migrate_old");
            st.execute("""
                    CREATE TABLE shop (
                      id integer primary key,
                      chain varchar(255) not null,
                      city varchar(255),
                      created_at timestamp not null,
                      enabled boolean not null,
                      lat float,
                      lon float,
                      match_name varchar(255) not null,
                      name varchar(255) not null,
                      online_only boolean not null,
                      plz varchar(255),
                      source varchar(32) not null,
                      street varchar(255)
                    )
                    """);
            st.execute("""
                    INSERT INTO shop (id,chain,city,created_at,enabled,lat,lon,match_name,name,online_only,plz,source,street)
                    SELECT id,chain,city,created_at,enabled,lat,lon,match_name,name,online_only,plz,source,street
                    FROM shop__migrate_old
                    """);
            st.execute("DROP TABLE shop__migrate_old");
        }
        log.info("Migration complete: shop.source CHECK constraint removed");
    }

    private String shopTableDdl(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type='table' AND name='shop'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
