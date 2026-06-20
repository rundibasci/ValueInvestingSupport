package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.InsiderTradeRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.security.dto.InsiderTradeItem;
import it.mazzoni.vis.security.dto.InsidersResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class InsidersController {

    private final SecurityRepository securityRepository;
    private final InsiderTradeRepository insiderTradeRepository;

    public InsidersController(SecurityRepository securityRepository,
                              InsiderTradeRepository insiderTradeRepository) {
        this.securityRepository = securityRepository;
        this.insiderTradeRepository = insiderTradeRepository;
    }

    @GetMapping("/{symbol}/insiders")
    public ResponseEntity<InsidersResponse> insiders(@PathVariable String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        LocalDate since = LocalDate.now().minusMonths(12);
        List<InsiderTradeItem> trades = insiderTradeRepository
                .findBySecurityAndTradeDateGreaterThanEqualOrderByTradeDateDesc(security, since)
                .stream()
                .map(InsiderTradeItem::from)
                .toList();

        return ResponseEntity.ok(new InsidersResponse(symbol.toUpperCase(), trades));
    }
}
