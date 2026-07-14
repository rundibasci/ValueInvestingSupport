package it.mazzoni.vis.admin;

import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

    private static final Logger log = LoggerFactory.getLogger(UniverseSelectionService.class);

    private static final int DEFAULT_MAX_SYMBOLS = 100;
    private static final int HARD_MAX_SYMBOLS = 500;
    private static final List<String> DEFAULT_EXCHANGES = List.of("NYSE", "NASDAQ");

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final SeedService seedService;

    public UniverseSelectionService(MarketDataClient marketDataClient,
                                    SecurityRepository securityRepository,
                                    FundamentalSnapshotRepository fundamentalSnapshotRepository,
                                    PriceQuoteRepository priceQuoteRepository,
                                    SeedService seedService) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.priceQuoteRepository = priceQuoteRepository;
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
        validate(criteria);
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
            for (FmpStockListEntry entry : loadEntriesForExchange(exchange)) {
                if (entry.symbol() != null) {
                    bySymbol.putIfAbsent(entry.symbol().trim().toUpperCase(Locale.ROOT), entry);
                }
            }
        }
        return new ArrayList<>(bySymbol.values());
    }

    private List<FmpStockListEntry> loadEntriesForExchange(String exchange) {
        try {
            List<FmpStockListEntry> entries = marketDataClient.listSymbols(exchange);
            if (entries != null && !entries.isEmpty()) {
                return entries;
            }
            log.warn("FMP universe list returned no symbols for exchange {}. Falling back to seeded securities.",
                    exchange);
        } catch (MarketDataException e) {
            log.warn("FMP universe list unavailable for exchange {}: {}. Falling back to seeded securities.",
                    exchange, e.getMessage());
        }
        return securityRepository.findAll().stream()
                .filter(Security::isActive)
                .filter(security -> exchangeMatches(security, exchange))
                .map(this::toStockListEntry)
                .toList();
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
            case VOLUME, VOLUME_DESC -> Comparator
                    .comparing(UniversePreviewRow::volume, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(UniversePreviewRow::symbol);
            case ALPHABETICAL, SYMBOL_ASC -> Comparator.comparing(UniversePreviewRow::symbol);
            case MARKET_CAP_ASC -> Comparator
                    .comparing(UniversePreviewRow::marketCap, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(UniversePreviewRow::symbol);
            case MARKET_CAP, MARKET_CAP_DESC -> Comparator
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

    private FmpStockListEntry toStockListEntry(Security security) {
        PriceQuote latestQuote = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security).orElse(null);
        BigDecimal marketCap = security.getMarketCap();
        if (marketCap == null && latestQuote != null && latestQuote.getClose() != null) {
            Long shares = fundamentalSnapshotRepository
                    .findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                    .map(FundamentalSnapshot::getSharesOutstanding)
                    .orElse(null);
            if (shares != null && shares > 0 && latestQuote.getClose().compareTo(BigDecimal.ZERO) > 0) {
                marketCap = latestQuote.getClose().multiply(BigDecimal.valueOf(shares));
            }
        }
        return new FmpStockListEntry(
                security.getSymbol(),
                security.getCompanyName(),
                security.getCountry(),
                security.getSector(),
                security.getExchange(),
                security.getExchange(),
                "stock",
                null,
                marketCap,
                latestQuote != null ? latestQuote.getVolume() : null);
    }

    private static boolean exchangeMatches(Security security, String exchange) {
        return security.getExchange() != null
                && !security.getExchange().isBlank()
                && security.getExchange().equalsIgnoreCase(exchange);
    }

    private static int normalizeMaxSymbols(Integer requested) {
        if (requested == null) return DEFAULT_MAX_SYMBOLS;
        return requested;
    }

    private static void validate(UniverseSelectionRequest criteria) {
        if (criteria.marketCapMin() != null && criteria.marketCapMin().signum() < 0
                || criteria.marketCapMax() != null && criteria.marketCapMax().signum() < 0
                || criteria.volumeMin() != null && criteria.volumeMin() < 0) {
            throw invalidCriteria("Market cap and volume thresholds cannot be negative.");
        }
        if (criteria.marketCapMin() != null && criteria.marketCapMax() != null
                && criteria.marketCapMin().compareTo(criteria.marketCapMax()) > 0) {
            throw invalidCriteria("Market cap minimum cannot exceed market cap maximum.");
        }
        if (criteria.maxSymbols() != null
                && (criteria.maxSymbols() < 1 || criteria.maxSymbols() > HARD_MAX_SYMBOLS)) {
            throw invalidCriteria("maxSymbols must be between 1 and " + HARD_MAX_SYMBOLS + ".");
        }
    }

    private static ResponseStatusException invalidCriteria(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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
