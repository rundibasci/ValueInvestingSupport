package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.Holding;
import it.mazzoni.vis.domain.entity.Portfolio;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.HoldingRepository;
import it.mazzoni.vis.domain.repository.PortfolioRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.professional.ResearchDecisionAuditService;
import it.mazzoni.vis.portfolio.dto.AddHoldingRequest;
import it.mazzoni.vis.portfolio.dto.ConcentrationWarning;
import it.mazzoni.vis.portfolio.dto.CreatePortfolioRequest;
import it.mazzoni.vis.portfolio.dto.HoldingDetailItem;
import it.mazzoni.vis.portfolio.dto.PortfolioDetailResponse;
import it.mazzoni.vis.portfolio.dto.PortfolioSummaryResponse;
import it.mazzoni.vis.portfolio.dto.UpdateHoldingRequest;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;
    private final SecurityRepository securityRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final ResearchDecisionAuditService auditService;
    private static final BigDecimal HOLDING_CONCENTRATION_THRESHOLD = new BigDecimal("20.00");
    private static final BigDecimal SECTOR_CONCENTRATION_THRESHOLD = new BigDecimal("35.00");

    public PortfolioService(PortfolioRepository portfolioRepository,
                            HoldingRepository holdingRepository,
                            UserRepository userRepository,
                            SecurityRepository securityRepository,
                            PriceQuoteRepository priceQuoteRepository,
                            ValuationResultRepository valuationResultRepository) {
        this(portfolioRepository, holdingRepository, userRepository, securityRepository,
                priceQuoteRepository, valuationResultRepository, null);
    }

    @Autowired
    public PortfolioService(PortfolioRepository portfolioRepository,
                            HoldingRepository holdingRepository,
                            UserRepository userRepository,
                            SecurityRepository securityRepository,
                            PriceQuoteRepository priceQuoteRepository,
                            ValuationResultRepository valuationResultRepository,
                            ResearchDecisionAuditService auditService) {
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
        this.securityRepository = securityRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PortfolioSummaryResponse> listPortfolios(Authentication auth) {
        User user = resolveUser(auth);
        return portfolioRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(PortfolioSummaryResponse::from)
                .toList();
    }

    public PortfolioSummaryResponse createPortfolio(Authentication auth, CreatePortfolioRequest req) {
        User user = resolveUser(auth);
        Portfolio p = new Portfolio();
        p.setUser(user);
        p.setName(req.name());
        p.setDescription(req.description());
        return PortfolioSummaryResponse.from(portfolioRepository.save(p));
    }

    public PortfolioDetailResponse getPortfolioDetail(Authentication auth, UUID id) {
        User user = resolveUser(auth);
        Portfolio portfolio = resolvePortfolio(id, user);
        List<Holding> holdings = holdingRepository.findByPortfolioOrderByAddedAtDesc(portfolio);

        Set<String> symbols = holdings.stream().map(Holding::getSymbol).collect(Collectors.toSet());
        Map<String, BigDecimal> priceMap = new HashMap<>();
        Map<String, BigDecimal> fairValueMap = new HashMap<>();
        Map<String, BigDecimal> mosMap = new HashMap<>();
        Map<String, String> recMap = new HashMap<>();
        Map<String, String> sectorMap = new HashMap<>();

        for (String symbol : symbols) {
            Optional<Security> secOpt = securityRepository.findBySymbol(symbol);
            if (secOpt.isPresent()) {
                Security sec = secOpt.get();
                sectorMap.put(symbol, sec.getSector());
                priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(sec)
                        .ifPresent(pq -> priceMap.put(symbol, pq.getClose()));
                valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(sec)
                        .ifPresent(vr -> {
                            fairValueMap.put(symbol, vr.getCompositeFairValue());
                            mosMap.put(symbol, vr.getMarginOfSafety());
                            if (vr.getRecommendation() != null) {
                                recMap.put(symbol, vr.getRecommendation().name());
                            }
                        });
            }
        }

        BigDecimal totalValue = holdings.stream()
                .map(h -> {
                    BigDecimal price = priceMap.get(h.getSymbol());
                    return price != null ? h.getQuantity().multiply(price) : null;
                })
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean anyPriced = holdings.stream().anyMatch(h -> priceMap.containsKey(h.getSymbol()));
        BigDecimal resolvedTotal = anyPriced ? totalValue : null;

        List<HoldingDetailItem> items = holdings.stream()
                .map(h -> buildHoldingItem(h, priceMap, fairValueMap, mosMap, recMap, sectorMap, resolvedTotal))
                .toList();

        BigDecimal weightedMoS = null;
        List<HoldingDetailItem> valued = items.stream()
                .filter(i -> i.weightPercent() != null && i.marginOfSafety() != null)
                .toList();
        if (!valued.isEmpty()) {
            weightedMoS = valued.stream()
                    .map(i -> i.weightPercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                            .multiply(i.marginOfSafety()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new PortfolioDetailResponse(portfolio.getId(), portfolio.getName(),
                portfolio.getDescription(), resolvedTotal, weightedMoS, items,
                concentrationWarnings(items),
                portfolio.getCreatedAt(), portfolio.getUpdatedAt());
    }

    public HoldingDetailItem addHolding(Authentication auth, UUID portfolioId, AddHoldingRequest req) {
        User user = resolveUser(auth);
        Portfolio portfolio = resolvePortfolio(portfolioId, user);

        Holding h = new Holding();
        h.setPortfolio(portfolio);
        h.setSymbol(req.symbol().toUpperCase());
        h.setQuantity(req.quantity());
        h.setAverageCostBasis(req.averageCostBasis());
        h.setCurrency(req.currency());

        Holding saved = holdingRepository.save(h);
        captureAudit(user, saved.getSymbol(), "ADD_HOLDING", null);
        return enrichSingle(saved);
    }

    public HoldingDetailItem updateHolding(Authentication auth, UUID portfolioId, UUID holdingId,
                                           UpdateHoldingRequest req) {
        User user = resolveUser(auth);
        Portfolio portfolio = resolvePortfolio(portfolioId, user);
        Holding h = holdingRepository.findByIdAndPortfolio(holdingId, portfolio)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Holding not found: " + holdingId));

        h.setQuantity(req.quantity());
        h.setAverageCostBasis(req.averageCostBasis());
        h.setCurrency(req.currency());

        return enrichSingle(holdingRepository.save(h));
    }

    public void removeHolding(Authentication auth, UUID portfolioId, UUID holdingId) {
        User user = resolveUser(auth);
        Portfolio portfolio = resolvePortfolio(portfolioId, user);
        Holding h = holdingRepository.findByIdAndPortfolio(holdingId, portfolio)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Holding not found: " + holdingId));
        String symbol = h.getSymbol();
        holdingRepository.delete(h);
        captureAudit(user, symbol, "REMOVE_HOLDING", null);
    }

    private User resolveUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Portfolio resolvePortfolio(UUID id, User user) {
        return portfolioRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Portfolio not found: " + id));
    }

    private void captureAudit(User user, String symbol, String actionType, String rationale) {
        if (auditService != null) {
            auditService.capture(user, symbol, actionType, rationale);
        }
    }

    private HoldingDetailItem enrichSingle(Holding h) {
        String symbol = h.getSymbol();
        BigDecimal currentPrice = null;
        BigDecimal compositeFairValue = null;
        BigDecimal marginOfSafety = null;
        String recommendation = null;
        String sector = null;

        Optional<Security> secOpt = securityRepository.findBySymbol(symbol);
        if (secOpt.isPresent()) {
            Security sec = secOpt.get();
            sector = sec.getSector();
            currentPrice = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(sec)
                    .map(PriceQuote::getClose).orElse(null);
            Optional<ValuationResult> vrOpt =
                    valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(sec);
            if (vrOpt.isPresent()) {
                ValuationResult vr = vrOpt.get();
                compositeFairValue = vr.getCompositeFairValue();
                marginOfSafety = vr.getMarginOfSafety();
                if (vr.getRecommendation() != null) {
                    recommendation = vr.getRecommendation().name();
                }
            }
        }

        BigDecimal currentValue = currentPrice != null ? h.getQuantity().multiply(currentPrice) : null;
        return new HoldingDetailItem(h.getId(), symbol, sector, h.getQuantity(), h.getAverageCostBasis(),
                h.getCurrency(), currentPrice, currentValue, null,
                compositeFairValue, marginOfSafety, recommendation,
                secOpt.isPresent()
                        ? (compositeFairValue != null ? "AVAILABLE" : "MISSING_INTERNAL_COMPUTATION")
                        : "MISSING_SEEDED_HISTORY",
                h.getAddedAt());
    }

    private HoldingDetailItem buildHoldingItem(Holding h,
                                               Map<String, BigDecimal> priceMap,
                                               Map<String, BigDecimal> fairValueMap,
                                               Map<String, BigDecimal> mosMap,
                                               Map<String, String> recMap,
                                               Map<String, String> sectorMap,
                                               BigDecimal totalValue) {
        String symbol = h.getSymbol();
        BigDecimal price = priceMap.get(symbol);
        BigDecimal currentValue = price != null ? h.getQuantity().multiply(price) : null;
        BigDecimal weightPercent = null;
        if (currentValue != null && totalValue != null && totalValue.compareTo(BigDecimal.ZERO) > 0) {
            weightPercent = currentValue
                    .divide(totalValue, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return new HoldingDetailItem(h.getId(), symbol, sectorMap.get(symbol), h.getQuantity(), h.getAverageCostBasis(),
                h.getCurrency(), price, currentValue, weightPercent,
                fairValueMap.get(symbol), mosMap.get(symbol), recMap.get(symbol),
                fairValueMap.containsKey(symbol) ? "AVAILABLE" : "MISSING_INTERNAL_COMPUTATION",
                h.getAddedAt());
    }

    private List<ConcentrationWarning> concentrationWarnings(List<HoldingDetailItem> items) {
        List<ConcentrationWarning> warnings = new java.util.ArrayList<>();
        for (HoldingDetailItem item : items) {
            if (item.weightPercent() != null
                    && item.weightPercent().compareTo(HOLDING_CONCENTRATION_THRESHOLD) > 0) {
                warnings.add(new ConcentrationWarning(
                        "HOLDING",
                        item.symbol(),
                        item.weightPercent(),
                        HOLDING_CONCENTRATION_THRESHOLD,
                        item.symbol() + " is above the model holding concentration threshold."
                ));
            }
        }

        Map<String, BigDecimal> sectorWeights = items.stream()
                .filter(i -> i.sector() != null && i.weightPercent() != null)
                .collect(Collectors.groupingBy(
                        HoldingDetailItem::sector,
                        Collectors.mapping(HoldingDetailItem::weightPercent,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));
        sectorWeights.forEach((sector, weight) -> {
            if (weight.compareTo(SECTOR_CONCENTRATION_THRESHOLD) > 0) {
                warnings.add(new ConcentrationWarning(
                        "SECTOR",
                        sector,
                        weight.setScale(2, RoundingMode.HALF_UP),
                        SECTOR_CONCENTRATION_THRESHOLD,
                        sector + " exposure is above the model sector concentration threshold."
                ));
            }
        });

        boolean missingWeights = items.stream().anyMatch(i -> i.weightPercent() == null);
        if (missingWeights) {
            warnings.add(new ConcentrationWarning(
                    "DATA_UNAVAILABLE",
                    "Portfolio",
                    null,
                    null,
                    "Some holdings are missing current prices, so concentration cannot be fully calculated."
            ));
        }
        return warnings;
    }
}
