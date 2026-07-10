package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.HistoricalPriceQuote;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.security.dto.PriceHistoryItem;
import it.mazzoni.vis.security.dto.PriceHistoryResponse;
import it.mazzoni.vis.valuation.ValuationDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/securities")
public class PriceHistoryController {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryController.class);

    private final SecurityRepository securityRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final MarketDataClient marketDataClient;

    public PriceHistoryController(SecurityRepository securityRepository,
                                  PriceQuoteRepository priceQuoteRepository,
                                  MarketDataClient marketDataClient) {
        this.securityRepository = securityRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.marketDataClient = marketDataClient;
    }

    @GetMapping("/{symbol}/prices")
    public ResponseEntity<PriceHistoryResponse> prices(@PathVariable String symbol,
                                                       @RequestParam(defaultValue = "10y") String range) {
        String upper = symbol.toUpperCase(Locale.ROOT);
        Security security = securityRepository.findBySymbol(upper)
                .orElseThrow(() -> new ValuationDataUnavailableException(upper));
        LocalDate to = LocalDate.now();
        LocalDate from = fromDate(range, to);
        String normalizedRange = normalizeRange(range);
        try {
            List<PriceHistoryItem> live = marketDataClient.getHistoricalPrices(upper, from, to).stream()
                    .sorted(Comparator.comparing(HistoricalPriceQuote::date))
                    .map(PriceHistoryItem::from)
                    .toList();
            if (!live.isEmpty()) {
                return ResponseEntity.ok(new PriceHistoryResponse(upper, normalizedRange, from, to, "FMP", live));
            }
        } catch (MarketDataException | UnsupportedOperationException e) {
            log.warn("Price history provider unavailable for {} range {}: {}", upper, normalizedRange, e.getMessage());
        }

        List<PriceHistoryItem> local = priceQuoteRepository
                .findBySecurityAndQuoteDateBetweenOrderByQuoteDateDesc(security, from, to)
                .stream()
                .sorted(Comparator.comparing(PriceQuote::getQuoteDate))
                .map(PriceHistoryItem::from)
                .toList();
        return ResponseEntity.ok(new PriceHistoryResponse(upper, normalizedRange, from, to, "LOCAL", local));
    }

    private static LocalDate fromDate(String range, LocalDate to) {
        return switch (normalizeRange(range)) {
            case "1y" -> to.minusYears(1);
            case "3y" -> to.minusYears(3);
            case "5y" -> to.minusYears(5);
            case "max" -> LocalDate.of(1970, 1, 1);
            default -> to.minusYears(10);
        };
    }

    private static String normalizeRange(String range) {
        return switch (range == null ? "" : range.trim().toLowerCase(Locale.ROOT)) {
            case "1y", "3y", "5y", "max" -> range.trim().toLowerCase(Locale.ROOT);
            default -> "10y";
        };
    }
}
