package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.security.dto.DividendItem;
import it.mazzoni.vis.security.dto.DividendsResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class DividendsController {

    private final SecurityRepository securityRepository;
    private final DividendRecordRepository dividendRecordRepository;
    private final DividendsService dividendsService;

    public DividendsController(SecurityRepository securityRepository,
                               DividendRecordRepository dividendRecordRepository,
                               DividendsService dividendsService) {
        this.securityRepository = securityRepository;
        this.dividendRecordRepository = dividendRecordRepository;
        this.dividendsService = dividendsService;
    }

    @GetMapping("/{symbol}/dividends")
    public ResponseEntity<DividendsResponse> dividends(@PathVariable String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        List<DividendRecord> records = dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security);

        List<DividendItem> history = records.stream().map(DividendItem::from).toList();
        int streak = dividendsService.computeStreak(records);

        return ResponseEntity.ok(new DividendsResponse(
                symbol.toUpperCase(),
                history,
                streak,
                dividendsService.computeCagr(records, 3),
                dividendsService.computeCagr(records, 5),
                dividendsService.computeCagr(records, 10)
        ));
    }
}
