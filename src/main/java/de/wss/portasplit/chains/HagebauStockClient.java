package de.wss.portasplit.chains;

import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.domain.Shop;
import de.wss.portasplit.domain.ShopSource;
import de.wss.portasplit.jobs.JobType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hagebau is feasible via plain HTTP (PDP → CSRF → save-store → re-read {@code __initialAppState}), but
 * needs each branch mapped to a hagebau {@code storeId}, which requires coordinates the seed data lacks.
 * Registered as a worker; not yet operational. Carries only the PortaSplit (no Cool on hagebau.de).
 */
@Component
public class HagebauStockClient implements ChainStockClient {

    private final AppProperties props;

    public HagebauStockClient(AppProperties props) {
        this.props = props;
    }

    @Override
    public JobType jobType() {
        return JobType.HAGEBAU;
    }

    @Override
    public ShopSource source() {
        return ShopSource.HAGEBAU;
    }

    @Override
    public AppProperties.Chain config() {
        return props.hagebau();
    }

    @Override
    public List<ChainReading> check(List<Shop> branches, List<String> errors) {
        return List.of();
    }

    @Override
    public String unsupportedReason() {
        return "Hagebau: Filial-Store-IDs noch zu mappen (keine Koordinaten im Seed) - Worker folgt";
    }
}
