package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.portfolio.dto.AddHoldingRequest;
import it.mazzoni.vis.portfolio.dto.CreatePortfolioRequest;
import it.mazzoni.vis.portfolio.dto.HoldingDetailItem;
import it.mazzoni.vis.portfolio.dto.PortfolioDetailResponse;
import it.mazzoni.vis.portfolio.dto.PortfolioSummaryResponse;
import it.mazzoni.vis.portfolio.dto.UpdateHoldingRequest;
import it.mazzoni.vis.portfolio.dto.PortfolioSimulationResponse;
import it.mazzoni.vis.portfolio.dto.SimulationRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios")
@Profile("!demo")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioSimulationService portfolioSimulationService;

    public PortfolioController(PortfolioService portfolioService, PortfolioSimulationService portfolioSimulationService) {
        this.portfolioService = portfolioService;
        this.portfolioSimulationService = portfolioSimulationService;
    }

    @GetMapping
    public List<PortfolioSummaryResponse> list(Authentication auth) {
        return portfolioService.listPortfolios(auth);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioSummaryResponse create(Authentication auth,
                                           @Valid @RequestBody CreatePortfolioRequest req) {
        return portfolioService.createPortfolio(auth, req);
    }

    @GetMapping("/{id}")
    public PortfolioDetailResponse detail(Authentication auth, @PathVariable UUID id) {
        return portfolioService.getPortfolioDetail(auth, id);
    }

    @PostMapping("/{id}/simulate")
    public PortfolioSimulationResponse simulate(Authentication auth, @PathVariable UUID id,
                                                @Valid @RequestBody SimulationRequest request) {
        return portfolioSimulationService.simulate(auth, id, request);
    }

    @PostMapping("/{id}/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldingDetailItem addHolding(Authentication auth,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody AddHoldingRequest req) {
        return portfolioService.addHolding(auth, id, req);
    }

    @PutMapping("/{id}/holdings/{holdingId}")
    public HoldingDetailItem updateHolding(Authentication auth,
                                           @PathVariable UUID id,
                                           @PathVariable UUID holdingId,
                                           @Valid @RequestBody UpdateHoldingRequest req) {
        return portfolioService.updateHolding(auth, id, holdingId, req);
    }

    @DeleteMapping("/{id}/holdings/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeHolding(Authentication auth,
                               @PathVariable UUID id,
                               @PathVariable UUID holdingId) {
        portfolioService.removeHolding(auth, id, holdingId);
    }
}
