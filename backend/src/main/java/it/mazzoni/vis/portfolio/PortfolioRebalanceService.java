package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.Holding;
import it.mazzoni.vis.domain.entity.Portfolio;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RebalanceLine;
import it.mazzoni.vis.domain.entity.RebalanceProposal;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.repository.HoldingRepository;
import it.mazzoni.vis.domain.repository.PortfolioRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RebalanceProposalRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.portfolio.dto.PortfolioSimulationResponse;
import it.mazzoni.vis.portfolio.dto.RebalanceLineResponse;
import it.mazzoni.vis.portfolio.dto.RebalanceProposalResponse;
import it.mazzoni.vis.portfolio.dto.RebalanceRequest;
import it.mazzoni.vis.portfolio.dto.RebalanceTarget;
import it.mazzoni.vis.portfolio.dto.SimulationProposalItem;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class PortfolioRebalanceService {

    private static final String DISCLAIMER = "This is a decision-support tool, not investment advice (MiFID II).";

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final UserRepository users;
    private final SecurityRepository securities;
    private final PriceQuoteRepository quotes;
    private final RebalanceProposalRepository proposals;
    private final PortfolioSimulationService simulation;

    public PortfolioRebalanceService(PortfolioRepository portfolios, HoldingRepository holdings,
                                     UserRepository users, SecurityRepository securities,
                                     PriceQuoteRepository quotes, RebalanceProposalRepository proposals,
                                     PortfolioSimulationService simulation) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.users = users;
        this.securities = securities;
        this.quotes = quotes;
        this.proposals = proposals;
        this.simulation = simulation;
    }

    @Transactional
    public RebalanceProposalResponse create(Authentication auth, UUID portfolioId, RebalanceRequest req) {
        Portfolio portfolio = portfolio(auth, portfolioId);
        boolean simulated = req.simulation() != null;
        boolean explicit = req.targets() != null && !req.targets().isEmpty();
        if (simulated == explicit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide exactly one of simulation or targets");
        }

        Map<String, BigDecimal> targets = simulated
                ? simulationTargets(auth, portfolioId, req)
                : explicitTargets(portfolio, req);
        Map<String, BigDecimal> current = quantities(portfolio);
        Set<String> symbols = new TreeSet<>();
        symbols.addAll(current.keySet());
        symbols.addAll(targets.keySet());

        RebalanceProposal proposal = new RebalanceProposal();
        proposal.setPortfolio(portfolio);
        proposal.setStatus("PENDING");
        proposal.setHoldingsFingerprint(fingerprint(current));
        BigDecimal minimumTrade = req.minimumTradeValue() == null ? BigDecimal.ZERO : req.minimumTradeValue();
        for (String symbol : symbols) {
            addLine(proposal, symbol, current.getOrDefault(symbol, BigDecimal.ZERO),
                    targets.getOrDefault(symbol, BigDecimal.ZERO), minimumTrade);
        }
        return response(proposals.save(proposal));
    }

    @Transactional(readOnly = true)
    public RebalanceProposalResponse get(Authentication auth, UUID portfolioId, UUID id) {
        return response(proposal(auth, portfolioId, id));
    }

    @Transactional
    public RebalanceProposalResponse apply(Authentication auth, UUID portfolioId, UUID id) {
        RebalanceProposal proposal = proposal(auth, portfolioId, id);
        if (!"PENDING".equals(proposal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal has already been applied");
        }
        if (!fingerprint(quantities(proposal.getPortfolio())).equals(proposal.getHoldingsFingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Portfolio holdings changed since proposal creation");
        }
        for (RebalanceLine line : proposal.getLines()) {
            applyLine(proposal.getPortfolio(), line);
        }
        proposal.setStatus("APPLIED");
        proposal.setAppliedAt(LocalDateTime.now());
        return response(proposals.save(proposal));
    }

    private Map<String, BigDecimal> simulationTargets(Authentication auth, UUID portfolioId, RebalanceRequest req) {
        PortfolioSimulationResponse response = simulation.simulate(auth, portfolioId, req.simulation());
        Map<String, BigDecimal> targets = new TreeMap<>();
        for (SimulationProposalItem item : response.proposals()) {
            targets.put(item.symbol(), BigDecimal.valueOf(item.proposedShares()));
        }
        return targets;
    }

    private Map<String, BigDecimal> explicitTargets(Portfolio portfolio, RebalanceRequest req) {
        BigDecimal weightTotal = req.targets().stream().map(RebalanceTarget::targetWeightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightTotal.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target weights cannot exceed 100");
        }
        BigDecimal total = currentValue(portfolio);
        if (total.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Portfolio needs priced holdings for explicit targets");
        }
        Map<String, BigDecimal> targets = new TreeMap<>();
        for (RebalanceTarget target : req.targets()) {
            BigDecimal price = price(target.symbol());
            BigDecimal quantity = total.multiply(target.targetWeightPercent()).divide(BigDecimal.valueOf(100), 8,
                    RoundingMode.DOWN).divide(price, 0, RoundingMode.DOWN);
            targets.put(target.symbol().toUpperCase(), quantity);
        }
        return targets;
    }

    private void addLine(RebalanceProposal proposal, String symbol, BigDecimal current, BigDecimal target,
                         BigDecimal minimumTrade) {
        BigDecimal capturedPrice = price(symbol);
        if (target.subtract(current).abs().multiply(capturedPrice).compareTo(minimumTrade) < 0) {
            target = current;
        }
        RebalanceLine line = new RebalanceLine();
        line.setProposal(proposal);
        line.setSymbol(symbol);
        line.setCapturedPrice(capturedPrice);
        line.setCurrentQuantity(current);
        line.setTargetQuantity(target);
        proposal.getLines().add(line);
    }

    private void applyLine(Portfolio portfolio, RebalanceLine line) {
        List<Holding> matching = holdings.findByPortfolioAndSymbol(portfolio, line.getSymbol());
        if (line.getTargetQuantity().signum() == 0) {
            holdings.deleteAll(matching);
            return;
        }
        Holding holding = matching.stream().findFirst().orElseGet(() -> newHolding(portfolio, line.getSymbol()));
        holding.setQuantity(line.getTargetQuantity());
        holdings.save(holding);
    }

    private Holding newHolding(Portfolio portfolio, String symbol) {
        Holding holding = new Holding();
        holding.setPortfolio(portfolio);
        holding.setSymbol(symbol);
        holding.setCurrency("USD");
        return holding;
    }

    private Portfolio portfolio(Authentication auth, UUID id) {
        User user = users.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return portfolios.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + id));
    }

    private RebalanceProposal proposal(Authentication auth, UUID portfolioId, UUID id) {
        return proposals.findByIdAndPortfolio(id, portfolio(auth, portfolioId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rebalance proposal not found"));
    }

    private Map<String, BigDecimal> quantities(Portfolio portfolio) {
        Map<String, BigDecimal> result = new TreeMap<>();
        holdings.findByPortfolio(portfolio)
                .forEach(holding -> result.merge(holding.getSymbol(), holding.getQuantity(), BigDecimal::add));
        return result;
    }

    private BigDecimal currentValue(Portfolio portfolio) {
        return quantities(portfolio).entrySet().stream()
                .map(entry -> entry.getValue().multiply(price(entry.getKey())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal price(String symbol) {
        Security security = securities.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Security not found: " + symbol));
        return quotes.findTopBySecurityOrderByQuoteDateDesc(security).map(PriceQuote::getClose)
                .filter(value -> value.signum() > 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Price unavailable: " + symbol));
    }

    private String fingerprint(Map<String, BigDecimal> quantities) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(quantities.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private RebalanceProposalResponse response(RebalanceProposal proposal) {
        List<RebalanceLineResponse> lines = proposal.getLines().stream()
                .sorted(Comparator.comparing(RebalanceLine::getSymbol))
                .map(this::lineResponse)
                .toList();
        BigDecimal buys = lines.stream().filter(line -> "BUY".equals(line.side()))
                .map(RebalanceLineResponse::estimatedTradeValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sells = lines.stream().filter(line -> "SELL".equals(line.side()))
                .map(RebalanceLineResponse::estimatedTradeValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RebalanceProposalResponse(proposal.getId(), proposal.getStatus(), lines, money(buys), money(sells),
                proposal.getCreatedAt(), proposal.getAppliedAt(), DISCLAIMER);
    }

    private RebalanceLineResponse lineResponse(RebalanceLine line) {
        BigDecimal delta = line.getTargetQuantity().subtract(line.getCurrentQuantity());
        String side = delta.signum() > 0 ? "BUY" : delta.signum() < 0 ? "SELL" : "HOLD";
        return new RebalanceLineResponse(line.getSymbol(), money(line.getCapturedPrice()), line.getCurrentQuantity(),
                line.getTargetQuantity(), delta, money(delta.abs().multiply(line.getCapturedPrice())), side);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
