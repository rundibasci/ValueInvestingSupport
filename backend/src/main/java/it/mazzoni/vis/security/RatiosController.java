package it.mazzoni.vis.security;

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

import java.util.List;

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
                .findTop10BySecurityOrderByReportDateDesc(security)
                .stream()
                .map(RatioSnapshotItem::from)
                .toList();

        return ResponseEntity.ok(new RatiosHistoryResponse(symbol.toUpperCase(), items));
    }
}
