package de.wss.portasplit.service;

import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.domain.ShopSource;
import de.wss.portasplit.repository.AvailabilityEventRepository;
import de.wss.portasplit.repository.ProductAvailabilityRepository;
import de.wss.portasplit.repository.ShopRepository;
import de.wss.portasplit.web.dto.ShopRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private final ShopRepository shopRepository;
    private final ProductAvailabilityRepository availabilityRepository;
    private final AvailabilityEventRepository eventRepository;

    public ShopService(ShopRepository shopRepository,
                       ProductAvailabilityRepository availabilityRepository,
                       AvailabilityEventRepository eventRepository) {
        this.shopRepository = shopRepository;
        this.availabilityRepository = availabilityRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public List<Shop> findAll() {
        return shopRepository.findAllByOrderByChainAscNameAsc();
    }

    @Transactional(readOnly = true)
    public Shop get(Long id) {
        return shopRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Shop " + id + " not found"));
    }

    @Transactional
    public Shop create(ShopRequest req) {
        String matchName = Shop.normalize(StringUtils.hasText(req.matchName()) ? req.matchName() : req.name());
        shopRepository.findByMatchName(matchName).ifPresent(existing -> {
            throw new ConflictException("A shop with matchName '" + matchName + "' already exists");
        });
        Shop shop = new Shop();
        apply(shop, req, matchName);
        Shop saved = shopRepository.save(shop);
        log.info("Created shop '{}' (matchName='{}')", saved.getName(), saved.getMatchName());
        return saved;
    }

    @Transactional
    public Shop update(Long id, ShopRequest req) {
        Shop shop = get(id);
        String matchName = Shop.normalize(StringUtils.hasText(req.matchName()) ? req.matchName() : req.name());
        Optional<Shop> clash = shopRepository.findByMatchName(matchName);
        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            throw new ConflictException("Another shop already uses matchName '" + matchName + "'");
        }
        apply(shop, req, matchName);
        Shop saved = shopRepository.save(shop);
        log.info("Updated shop '{}' (id={})", saved.getName(), saved.getId());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Shop shop = get(id);
        availabilityRepository.deleteByShopId(id);
        eventRepository.deleteByShopId(id);
        shopRepository.delete(shop);
        log.info("Deleted shop '{}' (id={})", shop.getName(), id);
    }

    /**
     * Inserts shops that are not yet present (matched by {@code matchName}); existing rows are left
     * untouched so dashboard edits are preserved. Returns the number of inserted shops.
     */
    @Transactional
    public int mergeIfMissing(List<Shop> incoming) {
        int inserted = 0;
        for (Shop candidate : incoming) {
            if (!StringUtils.hasText(candidate.getMatchName())) {
                candidate.setMatchName(candidate.getName());
            }
            if (shopRepository.findByMatchName(candidate.getMatchName()).isEmpty()) {
                shopRepository.save(candidate);
                inserted++;
                log.info("Seeded shop '{}' (matchName='{}')", candidate.getName(), candidate.getMatchName());
            }
        }
        return inserted;
    }

    /**
     * Enriches existing shops with coordinates (and street/city) from the seed where they are still
     * missing. Needed because shops seeded before the Umkreissuche have no lat/lon, and
     * {@link #mergeIfMissing} never touches existing rows - without this, the radius filter could not
     * place them. Only fills blanks, so dashboard edits are preserved. Returns how many rows changed.
     */
    @Transactional
    public int backfillCoordinates(List<Shop> incoming) {
        int updated = 0;
        for (Shop in : incoming) {
            if (in.getLat() == null || in.getLon() == null || !StringUtils.hasText(in.getMatchName())) {
                continue;
            }
            Shop existing = shopRepository.findByMatchName(in.getMatchName()).orElse(null);
            if (existing == null) {
                continue;
            }
            boolean changed = false;
            if (existing.getLat() == null || existing.getLon() == null) {
                existing.setLat(in.getLat());
                existing.setLon(in.getLon());
                changed = true;
            }
            if (!StringUtils.hasText(existing.getStreet()) && StringUtils.hasText(in.getStreet())) {
                existing.setStreet(in.getStreet());
                changed = true;
            }
            if (!StringUtils.hasText(existing.getCity()) && StringUtils.hasText(in.getCity())) {
                existing.setCity(in.getCity());
                changed = true;
            }
            if (changed) {
                shopRepository.save(existing);
                updated++;
            }
        }
        return updated;
    }

    private void apply(Shop shop, ShopRequest req, String matchName) {
        shop.setChain(req.chain().trim());
        shop.setName(req.name().trim());
        shop.setMatchName(matchName);
        shop.setCity(req.city());
        shop.setPlz(req.plz());
        shop.setStreet(req.street());
        shop.setLat(req.lat());
        shop.setLon(req.lon());
        shop.setOnlineOnly(Boolean.TRUE.equals(req.onlineOnly()));
        ShopSource source = ShopSource.resolve(req.source(), req.chain());
        if (source == null) {
            throw new ConflictException(
                    "Für die Kette „" + req.chain() + "“ ist keine Prüfquelle bekannt.");
        }
        shop.setSource(source);
        shop.setEnabled(req.enabled() == null || req.enabled());
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }
}
