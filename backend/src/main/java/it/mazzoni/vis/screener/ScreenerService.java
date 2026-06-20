package it.mazzoni.vis.screener;

import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
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
            Set.of("totalScore", "marginOfSafety", "symbol", "companyName", "sector", "exchange");

    @PersistenceContext
    private EntityManager em;

    public ScreenerResponse search(ScreenerRequest request) {
        int page = request.page() != null ? Math.max(request.page(), 0) : 0;
        int pageSize = request.pageSize() != null
                ? Math.min(Math.max(request.pageSize(), 1), MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;
        String sortField = VALID_SORT_FIELDS.contains(request.sortField())
                ? request.sortField() : "totalScore";
        boolean sortDesc = !"ASC".equalsIgnoreCase(request.sortDirection());

        List<ScreenerResultItem> items = runDataQuery(request, page, pageSize, sortField, sortDesc);
        long totalElements = runCountQuery(request);
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return new ScreenerResponse(items, page, pageSize, totalElements, totalPages);
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
                vr.get("recommendation").alias("recommendation")
        );

        List<Predicate> predicates = buildPredicates(cb, query, sec, vs, vr, rs, request);
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(sortDesc
                ? cb.desc(sortExpression(sortField, sec, vs, vr))
                : cb.asc(sortExpression(sortField, sec, vs, vr)));

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

        query.select(cb.count(sec));
        List<Predicate> predicates = buildPredicates(cb, query, sec, vs, vr, rs, request);
        query.where(predicates.toArray(new Predicate[0]));

        return em.createQuery(query).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb, CriteriaQuery<?> query,
            Root<Security> sec, Root<ValueScore> vs,
            Root<ValuationResult> vr, Root<RatioSnapshot> rs,
            ScreenerRequest request) {

        List<Predicate> predicates = new ArrayList<>();

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

        return predicates;
    }

    @SuppressWarnings("unchecked")
    private Expression<?> sortExpression(String field,
                                          Root<Security> sec,
                                          Root<ValueScore> vs,
                                          Root<ValuationResult> vr) {
        return switch (field) {
            case "totalScore"      -> vs.get("totalScore");
            case "marginOfSafety"  -> vr.get("marginOfSafety");
            case "companyName"     -> sec.get("companyName");
            case "sector"          -> sec.get("sector");
            case "exchange"        -> sec.get("exchange");
            default                -> sec.get("symbol");
        };
    }

    private ScreenerResultItem toItem(Tuple t) {
        Recommendation rec = t.get("recommendation", Recommendation.class);
        return new ScreenerResultItem(
                t.get("symbol", String.class),
                t.get("companyName", String.class),
                t.get("sector", String.class),
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
                t.get("scoreDate", LocalDate.class)
        );
    }
}
