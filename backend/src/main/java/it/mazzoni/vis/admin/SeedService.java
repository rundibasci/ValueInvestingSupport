package it.mazzoni.vis.admin;

import it.mazzoni.vis.marketdata.MarketDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Profile("!demo")
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private final SeedTickerService seedTickerService;

    public SeedService(SeedTickerService seedTickerService) {
        this.seedTickerService = seedTickerService;
    }

    public List<SeedResult> seedTickers(List<String> symbols) {
        List<SeedResult> results = new ArrayList<>();
        for (String symbol : symbols) {
            String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                results.add(seedOne(normalized));
            }
        }
        return results;
    }

    private SeedResult seedOne(String symbol) {
        try {
            return seedTickerService.seedOne(symbol);
        } catch (MarketDataException e) {
            log.warn("Seed failed for {}: {}", symbol, e.getMessage());
            String errorMsg = switch (e.getErrorCode()) {
                case NOT_FOUND         -> "not found";
                case PLAN_RESTRICTION  -> "not available on current FMP plan";
                default                -> e.getMessage();
            };
            return SeedResult.failed(symbol, errorMsg);
        } catch (Exception e) {
            log.warn("Seed failed for {}: {}", symbol, e.getMessage());
            return SeedResult.failed(symbol, e.getMessage());
        }
    }
}
