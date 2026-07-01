package it.mazzoni.vis.admin;

import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Service
@Profile("!demo")
public class UniverseSelectionService {

    private static final int DEFAULT_MAX_SYMBOLS = 100;
    private static final int HARD_MAX_SYMBOLS = 500;
    private static final List<String> DEFAULT_EXCHANGES = List.of("NYSE", "NASDAQ");

    private final MarketDataClient marketDataClient;
    private final SeedService seedService;

    public UniverseSelectionService(MarketDataClient marketDataClient, SeedService seedService) {
        this.marketDataClient = marketDataClient;
        this.seedService = seedService;
    }

    public List<UniverseTemplateResponse> templates() {
        return List.of(
                new UniverseTemplateResponse(
                        "us-blue-chip",
                        "US blue chip",
                        "Large US-listed companies, capped and sorted by market cap.",
                        new UniverseSelectionRequest(List.of("NYSE", "NASDAQ"), List.of("US"), List.of(),
                                false, new BigDecimal("10000000000"), null, null, 100, UniverseSortBy.MARKET_CAP)),
                new UniverseTemplateResponse(
                        "dividend-aristocrats",
                        "Dividend aristocrats",
                        "Dividend-oriented defensive sectors. Dividend streak validation occurs after seeding.",
                        new UniverseSelectionRequest(List.of("NYSE", "NASDAQ"), List.of("US"),
                                List.of("Consumer Defensive", "Consumer Staples", "Healthcare", "Utilities", "Industrials"),
                                false, null, null, null, 100, UniverseSortBy.ALPHABETICAL)),
                new UniverseTemplateResponse(
                        "value-candidates",
                        "Value candidates",
                        "Broad US-listed candidate set for later P/E, P/B, and FCF validation after seeding.",
                        new UniverseSelectionRequest(List.of("NYSE", "NASDAQ"), List.of("US"), List.of(),
                                false, null, null, null, 100, UniverseSortBy.ALPHABETICAL)),
                new UniverseTemplateResponse(
                        "defensive-quality",
                        "Defensive quality",
                        "Consumer staples, healthcare, and utilities candidate set for later quality validation.",
                        new UniverseSelectionRequest(List.of("NYSE", "NASDAQ"), List.of("US"),
                                List.of("Consumer Defensive", "Consumer Staples", "Healthcare", "Utilities"),
                                false, null, null, null, 100, UniverseSortBy.MARKET_CAP))
        );
    }

    public UniversePreviewResponse preview(UniverseSelectionRequest request) {
        UniverseSelectionRequest criteria = request != null ? request : new UniverseSelectionRequest(
                null, null, null, false, null, null, null, DEFAULT_MAX_SYMBOLS, UniverseSortBy.MARKET_CAP);
        int maxSymbols = normalizeMaxSymbols(criteria.maxSymbols());
        List<FmpStockListEntry> entries = loadEntries(criteria.exchanges());
        List<UniversePreviewRow> matches = entries.stream()
                .filter(hasSymbol())
                .filter(matchesCountries(criteria.countries()))
                .filter(matchesSectors(criteria.sectors(), Boolean.TRUE.equals(criteria.excludeSectors())))
                .filter(matchesMarketCap(criteria.marketCapMin(), criteria.marketCapMax()))
                .filter(matchesVolume(criteria.volumeMin()))
                .map(UniverseSelectionService::toRow)
                .sorted(comparator(criteria.sortBy()))
                .toList();

        boolean capped = matches.size() > maxSymbols;
        List<UniversePreviewRow> returned = capped ? matches.subList(0, maxSymbols) : matches;
        return new UniversePreviewResponse(
                matches.size(),
                returned.size(),
                capped,
                capped ? "Results capped at " + maxSymbols + " symbols. Narrow filters before seeding a large universe." : null,
                returned);
    }

    public UniverseSeedCriteriaResponse seed(UniverseSelectionRequest request) {
        UniversePreviewResponse preview = preview(request);
        List<String> symbols = preview.symbols().stream()
                .map(UniversePreviewRow::symbol)
                .toList();
        return new UniverseSeedCriteriaResponse(preview, seedService.seedTickers(symbols));
    }

    private List<FmpStockListEntry> loadEntries(List<String> exchanges) {
        List<String> normalizedExchanges = normalizeList(exchanges);
        if (normalizedExchanges.isEmpty()) {
            normalizedExchanges = DEFAULT_EXCHANGES;
        }
        Map<String, FmpStockListEntry> bySymbol = new LinkedHashMap<>();
        for (String exchange : normalizedExchanges) {
            for (FmpStockListEntry entry : marketDataClient.listSymbols(exchange)) {
                if (entry.symbol() != null) {
                    bySymbol.putIfAbsent(entry.symbol().trim().toUpperCase(Locale.ROOT), entry);
                }
            }
        }
        return new ArrayList<>(bySymbol.values());
    }

    private static Predicate<FmpStockListEntry> hasSymbol() {
        return entry -> entry.symbol() != null && !entry.symbol().isBlank();
    }

    private static Predicate<FmpStockListEntry> matchesCountries(List<String> countries) {
        Set<String> accepted = normalizeSet(countries);
        if (accepted.isEmpty()) return entry -> true;
        return entry -> entry.country() != null && accepted.contains(entry.country().trim().toUpperCase(Locale.ROOT));
    }

    private static Predicate<FmpStockListEntry> matchesSectors(List<String> sectors, boolean exclude) {
        Set<String> normalized = normalizeSet(sectors);
        if (normalized.isEmpty()) return entry -> true;
        return entry -> {
            boolean matched = entry.sector() != null && normalized.contains(entry.sector().trim().toUpperCase(Locale.ROOT));
            return exclude ? !matched : matched;
        };
    }

    private static Predicate<FmpStockListEntry> matchesMarketCap(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return entry -> true;
        return entry -> {
            BigDecimal marketCap = entry.marketCap();
            if (marketCap == null) return false;
            if (min != null && marketCap.compareTo(min) < 0) return false;
            return max == null || marketCap.compareTo(max) <= 0;
        };
    }

    private static Predicate<FmpStockListEntry> matchesVolume(Long volumeMin) {
        if (volumeMin == null) return entry -> true;
        return entry -> entry.volume() != null && entry.volume() >= volumeMin;
    }

    private static Comparator<UniversePreviewRow> comparator(UniverseSortBy sortBy) {
        UniverseSortBy effective = sortBy != null ? sortBy : UniverseSortBy.MARKET_CAP;
        return switch (effective) {
            case VOLUME -> Comparator
                    .comparing(UniversePreviewRow::volume, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(UniversePreviewRow::symbol);
            case ALPHABETICAL -> Comparator.comparing(UniversePreviewRow::symbol);
            case MARKET_CAP -> Comparator
                    .comparing(UniversePreviewRow::marketCap, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(UniversePreviewRow::symbol);
        };
    }

    private static UniversePreviewRow toRow(FmpStockListEntry entry) {
        return new UniversePreviewRow(
                entry.symbol().trim().toUpperCase(Locale.ROOT),
                entry.name(),
                firstNonBlank(entry.exchangeShortName(), entry.exchange()),
                entry.country(),
                entry.sector(),
                entry.marketCap(),
                entry.volume());
    }

    private static int normalizeMaxSymbols(Integer requested) {
        if (requested == null) return DEFAULT_MAX_SYMBOLS;
        if (requested < 1) return DEFAULT_MAX_SYMBOLS;
        return Math.min(requested, HARD_MAX_SYMBOLS);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static Set<String> normalizeSet(List<String> values) {
        return Set.copyOf(normalizeList(values));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}
