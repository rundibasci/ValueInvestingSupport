package it.mazzoni.vis.screener;

import it.mazzoni.vis.common.dto.AvailabilityResponse;
import it.mazzoni.vis.common.SectorClassifier;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PiotroskiResult;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.MoatResult;
import it.mazzoni.vis.domain.entity.MoatStrength;
import it.mazzoni.vis.domain.entity.CapitalAllocationResult;
import it.mazzoni.vis.domain.entity.SharesOutstandingTrend;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.entity.AltmanResult;
import it.mazzoni.vis.domain.entity.AltmanZone;
import it.mazzoni.vis.domain.repository.AltmanResultRepository;
import it.mazzoni.vis.domain.repository.CapitalAllocationResultRepository;
import it.mazzoni.vis.domain.repository.MoatResultRepository;
import it.mazzoni.vis.domain.repository.PiotroskiResultRepository;
import it.mazzoni.vis.screener.dto.ScreenerRequest;
import it.mazzoni.vis.screener.dto.ScreenerResponse;
import it.mazzoni.vis.screener.dto.ScreenerResultItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ScreenerService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> VALID_SORT_FIELDS =
            Set.of("totalScore", "marginOfSafety", "symbol", "companyName", "sector", "exchange", "priceToFfo");

    @PersistenceContext
    private EntityManager em;

    private final PiotroskiResultRepository piotroskiResultRepository;
    private final AltmanResultRepository altmanResultRepository;
    private final MoatResultRepository moatResultRepository;
    private final CapitalAllocationResultRepository capitalAllocationResultRepository;

    public ScreenerService(PiotroskiResultRepository piotroskiResultRepository,
                           AltmanResultRepository altmanResultRepository,
                           MoatResultRepository moatResultRepository,
                           CapitalAllocationResultRepository capitalAllocationResultRepository) {
        this.piotroskiResultRepository = piotroskiResultRepository;
        this.altmanResultRepository = altmanResultRepository;
        this.moatResultRepository = moatResultRepository;
        this.capitalAllocationResultRepository = capitalAllocationResultRepository;
    }

    public ScreenerResponse search(ScreenerRequest request) {
        request = normalize(request);
        int page = request.page() != null ? Math.max(request.page(), 0) : 0;
        int pageSize = request.pageSize() != null
                ? Math.min(Math.max(request.pageSize(), 1), MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;
        String sortField = request.sortField() != null && VALID_SORT_FIELDS.contains(request.sortField())
                ? request.sortField() : "totalScore";
        boolean sortDesc = !"ASC".equalsIgnoreCase(request.sortDirection());

        List<ScreenerResultItem> items = runDataQuery(request, page, pageSize, sortField, sortDesc);
        long totalElements = runCountQuery(request);
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return new ScreenerResponse(items, page, pageSize, totalElements, totalPages);
    }

    private ScreenerRequest normalize(ScreenerRequest request) {
        if (request == null) {
            request = new ScreenerRequest(
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null);
        }
        validatePercentThreshold("minMarginOfSafety", request.minMarginOfSafety());
        validatePercentThreshold("maxMarginOfSafety", request.maxMarginOfSafety());
        validatePercentThreshold("minRoic", request.minRoic());
        validatePercentThreshold("minDividendYield", request.minDividendYield());
        validatePercentThreshold("minRevenueGrowth", request.minRevenueGrowth());
        validateEnum("altmanZone", request.altmanZone(), AltmanZone.class);
        validateEnum("moatStrength", request.moatStrength(), MoatStrength.class);
        validateEnum("sharesOutstandingTrend", request.sharesOutstandingTrend(), SharesOutstandingTrend.class);
        return new ScreenerRequest(
                clean(request.sector()),
                clean(request.exchange()),
                request.minMarginOfSafety(),
                request.maxMarginOfSafety(),
                request.minValueScore(),
                request.minRoic(),
                request.maxDebtToEquity(),
                request.minDividendYield(),
                request.minRevenueGrowth(),
                request.piotroskiMin(),
                request.piotroskiMax(),
                clean(request.altmanZone()),
                clean(request.moatStrength()),
                clean(request.sharesOutstandingTrend()),
                request.maxPriceToFfo(),
                request.maxNetDebtToEbitda(),
                request.maxAffoPayoutRatio(),
                clean(request.sortField()),
                clean(request.sortDirection()),
                request.page(),
                request.pageSize());
    }

    private String clean(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private void validatePercentThreshold(String field, BigDecimal value) {
        if (value != null
                && value.compareTo(BigDecimal.ZERO) > 0
                && value.abs().compareTo(BigDecimal.ONE) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " expects percentages such as 15, not fractions such as 0.15");
        }
    }

    private <T extends Enum<T>> void validateEnum(String field, String value, Class<T> enumType) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported " + field + ": " + value);
        }
    }

    private List<ScreenerResultItem> runDataQuery(
            ScreenerRequest request, int page, int pageSize,
            String sortField, boolean sortDesc) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();

        Root<Security> sec = query.from(Security.class);
        Root<ValueScore> vs = query.from(ValueScore.class);
        Root<ValuationResult> vr = query.from(ValuationResult.class);
        Root<RatioSnapshot> rs = query.from(RatioSnapshot.class);
        Root<PiotroskiResult> pr = needsPiotroski(request) ? query.from(PiotroskiResult.class) : null;
        Root<AltmanResult> ar = needsAltman(request) ? query.from(AltmanResult.class) : null;
        Root<MoatResult> mr = needsMoat(request) ? query.from(MoatResult.class) : null;
        Root<CapitalAllocationResult> car = needsCapitalAllocation(request) ? query.from(CapitalAllocationResult.class) : null;

        query.multiselect(
                sec.get("symbol").alias("symbol"),
                sec.get("companyName").alias("companyName"),
                sec.get("sector").alias("sector"),
                sec.get("exchange").alias("exchange"),
                vs.get("totalScore").alias("totalScore"),
                vs.get("mosScore").alias("mosScore"),
                vs.get("qualityScore").alias("qualityScore"),
                vs.get("safetyScore").alias("safetyScore"),
                vs.get("growthScore").alias("growthScore"),
                vs.get("dividendScore").alias("dividendScore"),
                vs.get("scoreDate").alias("scoreDate"),
                vr.get("compositeFairValue").alias("compositeFairValue"),
                vr.get("currentPrice").alias("currentPrice"),
                vr.get("marginOfSafety").alias("marginOfSafety"),
                vr.get("recommendation").alias("recommendation"),
                rs.get("ffoPerShare").alias("ffoPerShare"),
                rs.get("affoPerShare").alias("affoPerShare"),
                rs.get("priceToFfo").alias("priceToFfo"),
                rs.get("priceToAffo").alias("priceToAffo"),
                rs.get("netDebtToEbitda").alias("netDebtToEbitda"),
                rs.get("interestCoverageEbitda").alias("interestCoverageEbitda"),
                rs.get("affoPayoutRatio").alias("affoPayoutRatio")
        );

        List<Predicate> predicates = buildPredicates(cb, query, sec, vs, vr, rs, pr, ar, mr, car, request);
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(sortDesc
                ? cb.desc(sortExpression(sortField, sec, vs, vr, rs))
                : cb.asc(sortExpression(sortField, sec, vs, vr, rs)));

        List<Tuple> tuples = em.createQuery(query)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        return tuples.stream().map(this::toItem).toList();
    }

    private long runCountQuery(ScreenerRequest request) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);

        Root<Security> sec = query.from(Security.class);
        Root<ValueScore> vs = query.from(ValueScore.class);
        Root<ValuationResult> vr = query.from(ValuationResult.class);
        Root<RatioSnapshot> rs = query.from(RatioSnapshot.class);
        Root<PiotroskiResult> pr = needsPiotroski(request) ? query.from(PiotroskiResult.class) : null;
        Root<AltmanResult> ar = needsAltman(request) ? query.from(AltmanResult.class) : null;
        Root<MoatResult> mr = needsMoat(request) ? query.from(MoatResult.class) : null;
        Root<CapitalAllocationResult> car = needsCapitalAllocation(request) ? query.from(CapitalAllocationResult.class) : null;

        query.select(cb.count(sec));
        List<Predicate> predicates = buildPredicates(cb, query, sec, vs, vr, rs, pr, ar, mr, car, request);
        query.where(predicates.toArray(new Predicate[0]));

        return em.createQuery(query).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb, CriteriaQuery<?> query,
            Root<Security> sec, Root<ValueScore> vs,
            Root<ValuationResult> vr, Root<RatioSnapshot> rs,
            Root<PiotroskiResult> pr, Root<AltmanResult> ar,
            Root<MoatResult> mr, Root<CapitalAllocationResult> car,
            ScreenerRequest request) {

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.isTrue(sec.get("active")));

        // --- INNER JOIN: Security ↔ most-recent ValueScore ---
        predicates.add(cb.equal(vs.get("security"), sec));
        Subquery<LocalDate> maxScoreDate = query.subquery(LocalDate.class);
        Root<ValueScore> vsMax = maxScoreDate.from(ValueScore.class);
        maxScoreDate.select(cb.greatest(vsMax.<LocalDate>get("scoreDate")))
                    .where(cb.equal(vsMax.get("security"), sec));
        predicates.add(cb.equal(vs.get("scoreDate"), maxScoreDate));

        // --- INNER JOIN: Security ↔ most-recent ValuationResult ---
        predicates.add(cb.equal(vr.get("security"), sec));
        Subquery<LocalDate> maxValDate = query.subquery(LocalDate.class);
        Root<ValuationResult> vrMax = maxValDate.from(ValuationResult.class);
        maxValDate.select(cb.greatest(vrMax.<LocalDate>get("valuationDate")))
                  .where(cb.equal(vrMax.get("security"), sec));
        predicates.add(cb.equal(vr.get("valuationDate"), maxValDate));

        // --- INNER JOIN: Security ↔ most-recent TTM RatioSnapshot ---
        predicates.add(cb.equal(rs.get("security"), sec));
        predicates.add(cb.equal(rs.get("period"), Period.TTM));
        Subquery<LocalDate> maxRatioDate = query.subquery(LocalDate.class);
        Root<RatioSnapshot> rsMax = maxRatioDate.from(RatioSnapshot.class);
        maxRatioDate.select(cb.greatest(rsMax.<LocalDate>get("reportDate")))
                    .where(
                        cb.equal(rsMax.get("security"), sec),
                        cb.equal(rsMax.get("period"), Period.TTM)
                    );
        predicates.add(cb.equal(rs.get("reportDate"), maxRatioDate));

        // --- User filter predicates ---
        if (request.sector() != null) {
            predicates.add(cb.equal(sec.get("sector"), request.sector()));
        }
        if (request.exchange() != null) {
            predicates.add(cb.equal(sec.get("exchange"), request.exchange()));
        }
        if (request.minValueScore() != null) {
            predicates.add(cb.greaterThanOrEqualTo(vs.get("totalScore"), request.minValueScore()));
        }
        if (request.minMarginOfSafety() != null) {
            predicates.add(cb.greaterThanOrEqualTo(vr.get("marginOfSafety"), request.minMarginOfSafety()));
        }
        if (request.maxMarginOfSafety() != null) {
            predicates.add(cb.lessThanOrEqualTo(vr.get("marginOfSafety"), request.maxMarginOfSafety()));
        }
        if (request.minRoic() != null) {
            // ROIC stored as decimal (0.10 = 10%); request sends percentage (10.0 = 10%)
            BigDecimal roicDecimal = request.minRoic().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            predicates.add(cb.greaterThanOrEqualTo(rs.get("roic"), roicDecimal));
        }
        if (request.maxDebtToEquity() != null) {
            // D/E stored as ratio — same units as request
            predicates.add(cb.lessThanOrEqualTo(rs.get("debtToEquity"), request.maxDebtToEquity()));
        }
        if (request.minDividendYield() != null) {
            // Yield stored as decimal (0.02 = 2%); request sends percentage (2.0 = 2%)
            BigDecimal yieldDecimal = request.minDividendYield().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            predicates.add(cb.greaterThanOrEqualTo(rs.get("dividendYield"), yieldDecimal));
        }
        // minRevenueGrowth is not applied in the query (requires self-join on FundamentalSnapshot)
        // --- RM3 REIT-only filters (specs/2026-09-02-rm3-screener-security-detail-surfacing/) ---
        // priceToFfo/netDebtToEbitda/affoPayoutRatio are null for every non-REIT security (RM2
        // only populates them via SectorClassifier.isReit), so setting any of these three filters
        // implicitly restricts results to REIT-classified securities — a null <= threshold
        // comparison excludes the row, the same implicit-scoping behavior minRoic/maxDebtToEquity
        // already have for a symbol missing that ratio. Documented in requirements.md Decision 2.
        if (request.maxPriceToFfo() != null) {
            predicates.add(cb.lessThanOrEqualTo(rs.get("priceToFfo"), request.maxPriceToFfo()));
        }
        if (request.maxNetDebtToEbitda() != null) {
            predicates.add(cb.lessThanOrEqualTo(rs.get("netDebtToEbitda"), request.maxNetDebtToEbitda()));
        }
        if (request.maxAffoPayoutRatio() != null) {
            predicates.add(cb.lessThanOrEqualTo(rs.get("affoPayoutRatio"), request.maxAffoPayoutRatio()));
        }
        if (pr != null) {
            predicates.add(cb.equal(pr.get("security"), sec));
            Subquery<LocalDate> maxPiotroskiDate = query.subquery(LocalDate.class);
            Root<PiotroskiResult> prMax = maxPiotroskiDate.from(PiotroskiResult.class);
            maxPiotroskiDate.select(cb.greatest(prMax.<LocalDate>get("resultDate")))
                    .where(cb.equal(prMax.get("security"), sec));
            predicates.add(cb.equal(pr.get("resultDate"), maxPiotroskiDate));
            if (request.piotroskiMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(pr.get("totalScore"), request.piotroskiMin()));
            }
            if (request.piotroskiMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(pr.get("totalScore"), request.piotroskiMax()));
            }
        }
        if (ar != null) {
            predicates.add(cb.equal(ar.get("security"), sec));
            Subquery<LocalDate> maxAltmanDate = query.subquery(LocalDate.class);
            Root<AltmanResult> arMax = maxAltmanDate.from(AltmanResult.class);
            maxAltmanDate.select(cb.greatest(arMax.<LocalDate>get("resultDate")))
                    .where(cb.equal(arMax.get("security"), sec));
            predicates.add(cb.equal(ar.get("resultDate"), maxAltmanDate));
            predicates.add(cb.equal(ar.get("zone"), AltmanZone.valueOf(request.altmanZone().toUpperCase())));
        }
        if (mr != null) {
            predicates.add(cb.equal(mr.get("security"), sec));
            Subquery<LocalDate> maxMoatDate = query.subquery(LocalDate.class);
            Root<MoatResult> mrMax = maxMoatDate.from(MoatResult.class);
            maxMoatDate.select(cb.greatest(mrMax.<LocalDate>get("resultDate")))
                    .where(cb.equal(mrMax.get("security"), sec));
            predicates.add(cb.equal(mr.get("resultDate"), maxMoatDate));
            predicates.add(cb.equal(mr.get("moatStrength"), MoatStrength.valueOf(request.moatStrength().toUpperCase())));
        }
        if (car != null) {
            predicates.add(cb.equal(car.get("security"), sec));
            Subquery<LocalDate> maxCapitalDate = query.subquery(LocalDate.class);
            Root<CapitalAllocationResult> carMax = maxCapitalDate.from(CapitalAllocationResult.class);
            maxCapitalDate.select(cb.greatest(carMax.<LocalDate>get("resultDate")))
                    .where(cb.equal(carMax.get("security"), sec));
            predicates.add(cb.equal(car.get("resultDate"), maxCapitalDate));
            predicates.add(cb.equal(car.get("sharesOutstandingTrend"),
                    SharesOutstandingTrend.valueOf(request.sharesOutstandingTrend().toUpperCase())));
        }

        return predicates;
    }

    private boolean needsPiotroski(ScreenerRequest request) {
        return request.piotroskiMin() != null || request.piotroskiMax() != null;
    }

    private boolean needsAltman(ScreenerRequest request) {
        return request.altmanZone() != null && !request.altmanZone().isBlank();
    }

    private boolean needsMoat(ScreenerRequest request) {
        return request.moatStrength() != null && !request.moatStrength().isBlank();
    }

    private boolean needsCapitalAllocation(ScreenerRequest request) {
        return request.sharesOutstandingTrend() != null && !request.sharesOutstandingTrend().isBlank();
    }

    @SuppressWarnings("unchecked")
    private Expression<?> sortExpression(String field,
                                          Root<Security> sec,
                                          Root<ValueScore> vs,
                                          Root<ValuationResult> vr,
                                          Root<RatioSnapshot> rs) {
        return switch (field) {
            case "totalScore"      -> vs.get("totalScore");
            case "marginOfSafety"  -> vr.get("marginOfSafety");
            case "companyName"     -> sec.get("companyName");
            case "sector"          -> sec.get("sector");
            case "exchange"        -> sec.get("exchange");
            case "priceToFfo"      -> rs.get("priceToFfo");
            default                -> sec.get("symbol");
        };
    }

    private ScreenerResultItem toItem(Tuple t) {
        Recommendation rec = t.get("recommendation", Recommendation.class);
        String symbol = t.get("symbol", String.class);
        PiotroskiResult piotroski = piotroskiResultRepository.findTopBySecuritySymbolOrderByResultDateDesc(symbol).orElse(null);
        AltmanResult altman = altmanResultRepository.findTopBySecuritySymbolOrderByResultDateDesc(symbol).orElse(null);
        MoatResult moat = moatResultRepository.findTopBySecuritySymbolOrderByResultDateDesc(symbol).orElse(null);
        CapitalAllocationResult capitalAllocation = capitalAllocationResultRepository.findTopBySecuritySymbolOrderByResultDateDesc(symbol).orElse(null);
        String sector = t.get("sector", String.class);
        return new ScreenerResultItem(
                symbol,
                t.get("companyName", String.class),
                sector,
                t.get("exchange", String.class),
                t.get("currentPrice", BigDecimal.class),
                t.get("compositeFairValue", BigDecimal.class),
                t.get("marginOfSafety", BigDecimal.class),
                t.get("totalScore", BigDecimal.class),
                t.get("mosScore", BigDecimal.class),
                t.get("qualityScore", BigDecimal.class),
                t.get("safetyScore", BigDecimal.class),
                t.get("growthScore", BigDecimal.class),
                t.get("dividendScore", BigDecimal.class),
                rec != null ? rec.name() : null,
                t.get("scoreDate", LocalDate.class),
                AvailabilityResponse.available(t.get("scoreDate", LocalDate.class)),
                AvailabilityResponse.available(null),
                piotroski != null ? piotroski.getTotalScore() : null,
                piotroski != null ? piotroski.getAvailabilityStatus().name() : "MISSING_INTERNAL_COMPUTATION",
                altman != null ? altman.getZone().name() : null,
                altman != null ? altman.getAvailabilityStatus().name() : "MISSING_INTERNAL_COMPUTATION",
                moat != null ? moat.getMoatStrength().name() : null,
                capitalAllocation != null ? capitalAllocation.getSharesOutstandingTrend().name() : null,
                SectorClassifier.isReitOrUtility(sector) ? SectorClassifier.REIT_UTILITY_METRIC_CAVEAT : null,
                t.get("ffoPerShare", BigDecimal.class),
                t.get("affoPerShare", BigDecimal.class),
                t.get("priceToFfo", BigDecimal.class),
                t.get("priceToAffo", BigDecimal.class),
                t.get("netDebtToEbitda", BigDecimal.class),
                t.get("interestCoverageEbitda", BigDecimal.class),
                t.get("affoPayoutRatio", BigDecimal.class)
        );
    }
}
