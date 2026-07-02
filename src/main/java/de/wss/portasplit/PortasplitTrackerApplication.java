package de.wss.portasplit;

import de.wss.portasplit.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class PortasplitTrackerApplication {

    private static final Logger log = LoggerFactory.getLogger(PortasplitTrackerApplication.class);

    public static void main(String[] args) {
        ensureDatabaseDirectory();
        SpringApplication.run(PortasplitTrackerApplication.class, args);
    }

    /** SQLite creates the DB file but not its parent directory, so make sure it exists up front. */
    private static void ensureDatabaseDirectory() {
        String dbPath = System.getenv().getOrDefault("PORTASPLIT_DB_PATH", "./data/portasplit.db");
        try {
            Path parent = Paths.get(dbPath).toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            log.warn("Could not create database directory for '{}': {}", dbPath, e.getMessage());
        }
    }
}
