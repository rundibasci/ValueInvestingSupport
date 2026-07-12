package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.security.dto.RatioSnapshotItem;
import it.mazzoni.vis.security.dto.RatiosHistoryResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class RatiosController {

    private final SecurityRepository securityRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;

    public RatiosController(SecurityRepository securityRepository,
                            RatioSnapshotRepository ratioSnapshotRepository) {
        this.securityRepository = securityRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
    }

    @GetMapping("/{symbol}/ratios")
    public ResponseEntity<RatiosHistoryResponse> ratios(@PathVariable String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        List<RatioSnapshotItem> items = ratioSnapshotRepository
                .findBySecurity(security)
                .stream()
                .sorted(Comparator.comparing(RatioSnapshot::getReportDate).reversed())
                .collect(LinkedHashMap<Integer, RatioSnapshot>::new,
                        (byYear, snapshot) -> byYear.putIfAbsent(snapshot.getReportDate().getYear(), snapshot),
                        LinkedHashMap::putAll)
                .values()
                .stream()
                .limit(10)
                .map(RatioSnapshotItem::from)
                .toList();

        if (hasRepeatedCoreHistory(items)) {
            items = items.stream().limit(1).toList();
        }

        return ResponseEntity.ok(new RatiosHistoryResponse(symbol.toUpperCase(), items));
    }

    private static boolean hasRepeatedCoreHistory(List<RatioSnapshotItem> items) {
        return items.size() >= 6
                && distinctCount(items.stream().map(RatioSnapshotItem::pe).toList())
                + distinctCount(items.stream().map(RatioSnapshotItem::roic).toList())
                + distinctCount(items.stream().map(RatioSnapshotItem::roe).toList()) <= 3;
    }

    private static long distinctCount(List<BigDecimal> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(BigDecimal::stripTrailingZeros)
                .distinct()
                .count();
    }
}
