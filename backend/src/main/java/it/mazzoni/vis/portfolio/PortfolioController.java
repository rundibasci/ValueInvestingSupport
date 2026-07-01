package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.portfolio.dto.AddHoldingRequest;
import it.mazzoni.vis.portfolio.dto.CreatePortfolioRequest;
import it.mazzoni.vis.portfolio.dto.HoldingDetailItem;
import it.mazzoni.vis.portfolio.dto.PortfolioDetailResponse;
import it.mazzoni.vis.portfolio.dto.PortfolioSummaryResponse;
import it.mazzoni.vis.portfolio.dto.UpdateHoldingRequest;
import it.mazzoni.vis.portfolio.dto.PortfolioSimulationResponse;
import it.mazzoni.vis.portfolio.dto.SimulationRequest;
import it.mazzoni.vis.portfolio.dto.RebalanceRequest;
import it.mazzoni.vis.portfolio.dto.RebalanceProposalResponse;
import it.mazzoni.vis.portfolio.dto.PortfolioAnalyticsResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final PortfolioRebalanceService portfolioRebalanceService;
    private final PortfolioAnalyticsService portfolioAnalyticsService;

    @Autowired
    public PortfolioController(PortfolioService portfolioService, PortfolioSimulationService portfolioSimulationService,
                               PortfolioRebalanceService portfolioRebalanceService,
                               PortfolioAnalyticsService portfolioAnalyticsService) {
        this.portfolioService = portfolioService;
        this.portfolioSimulationService = portfolioSimulationService;
        this.portfolioRebalanceService = portfolioRebalanceService;
        this.portfolioAnalyticsService = portfolioAnalyticsService;
    }

    public PortfolioController(PortfolioService portfolioService, PortfolioSimulationService portfolioSimulationService,
                               PortfolioRebalanceService portfolioRebalanceService) {
        this(portfolioService, portfolioSimulationService, portfolioRebalanceService, null);
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

    @GetMapping("/{id}/analytics")
    public PortfolioAnalyticsResponse analytics(Authentication auth, @PathVariable UUID id) {
        return portfolioAnalyticsService.analyze(auth, id);
    }

    @PostMapping("/{id}/simulate")
    public PortfolioSimulationResponse simulate(Authentication auth, @PathVariable UUID id,
                                                @Valid @RequestBody SimulationRequest request) {
        return portfolioSimulationService.simulate(auth, id, request);
    }

    /** Retained for existing controller tests; production wiring uses the three-argument constructor. */
    public PortfolioController(PortfolioService portfolioService, PortfolioSimulationService portfolioSimulationService) {
        this(portfolioService, portfolioSimulationService, null, null);
    }

    @PostMapping("/{id}/rebalance")
    @ResponseStatus(HttpStatus.CREATED)
    public RebalanceProposalResponse rebalance(Authentication auth, @PathVariable UUID id, @Valid @RequestBody RebalanceRequest request) {
        return portfolioRebalanceService.create(auth, id, request);
    }

    @GetMapping("/{id}/rebalances/{rebalanceId}")
    public RebalanceProposalResponse rebalanceDetail(Authentication auth, @PathVariable UUID id, @PathVariable UUID rebalanceId) {
        return portfolioRebalanceService.get(auth, id, rebalanceId);
    }

    @PostMapping("/{id}/rebalances/{rebalanceId}/apply")
    public RebalanceProposalResponse applyRebalance(Authentication auth, @PathVariable UUID id, @PathVariable UUID rebalanceId) {
        return portfolioRebalanceService.apply(auth, id, rebalanceId);
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
