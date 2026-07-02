package de.wss.portasplit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.domain.ShopSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * On startup, merges the bundled seed list and an optional external {@code shops.json} into the
 * database. Existing shops (matched by {@code matchName}) are never overwritten, so the JSON files
 * and dashboard edits coexist.
 */
@Component
public class ShopInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ShopInitializer.class);

    private final ShopService shopService;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public ShopInitializer(ShopService shopService,
                           ResourceLoader resourceLoader,
                           ObjectMapper objectMapper,
                           AppProperties props) {
        this.shopService = shopService;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.shops().mergeOnStartup()) {
            log.info("Shop merge on startup disabled (app.shops.merge-on-startup=false)");
            return;
        }

        List<Shop> incoming = new ArrayList<>();
        // 1) bundled seed (classpath)
        incoming.addAll(load(resourceLoader.getResource(props.shops().seedFile()), "seed"));
        // 2) optional external override on the filesystem
        String configFile = props.shops().configFile();
        if (StringUtils.hasText(configFile)) {
            incoming.addAll(load(new FileSystemResource(configFile), "config"));
        }

        // Seed all shops — including the scraped Amazon/Lidl rows — regardless of whether their
        // checker is currently enabled. A source can be switched on at runtime from the dashboard, and
        // the checker then needs an enabled shop row to write to; gating is owned by the job queue
        // (CheckJobService.enabled), not by withholding the shop. A disabled source is simply not polled.
        if (incoming.isEmpty()) {
            log.info("No shops to merge from seed/config files");
            return;
        }
        int inserted = shopService.mergeIfMissing(incoming);
        // Existing rows (seeded before the Umkreissuche) carry no coordinates; enrich them from the seed
        // so the radius filter can place them. Only fills blanks — dashboard edits are kept.
        int enriched = shopService.backfillCoordinates(incoming);
        log.info("Shop merge complete: {} candidate(s), {} newly inserted, {} enriched with coordinates",
                incoming.size(), inserted, enriched);
    }

    private List<Shop> load(Resource resource, String label) {
        if (resource == null || !resource.exists()) {
            log.debug("No {} shop file at {}", label, resource);
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            SeedShop[] seeds = objectMapper.readValue(in, SeedShop[].class);
            List<Shop> shops = new ArrayList<>(seeds.length);
            for (SeedShop seed : seeds) {
                if (!StringUtils.hasText(seed.chain()) || !StringUtils.hasText(seed.name())) {
                    log.warn("Skipping {} entry without chain/name: {}", label, seed);
                    continue;
                }
                Shop shop = seed.toShop();
                if (shop.getSource() == null) {
                    log.warn("Skipping {} entry '{}' - no checker for chain '{}' and no explicit source",
                            label, seed.name(), seed.chain());
                    continue;
                }
                shops.add(shop);
            }
            log.info("Loaded {} shop(s) from {} file ({})", shops.size(), label, resource.getDescription());
            return shops;
        } catch (Exception e) {
            log.error("Failed to read {} shop file {}: {}", label, resource.getDescription(), e.getMessage());
            return List.of();
        }
    }

    /** JSON shape of a shop entry in the seed/config files. */
    record SeedShop(
            String chain,
            String name,
            String matchName,
            String city,
            String plz,
            String street,
            Double lat,
            Double lon,
            Boolean onlineOnly,
            String source,
            Boolean enabled
    ) {
        Shop toShop() {
            Shop shop = new Shop();
            shop.setChain(chain.trim());
            shop.setName(name.trim());
            shop.setMatchName(StringUtils.hasText(matchName) ? matchName : name);
            shop.setCity(city);
            shop.setPlz(plz);
            shop.setStreet(street);
            shop.setLat(lat);
            shop.setLon(lon);
            shop.setOnlineOnly(Boolean.TRUE.equals(onlineOnly));
            shop.setSource(ShopSource.resolve(source, chain));
            shop.setEnabled(enabled == null || enabled);
            return shop;
        }
    }
}
