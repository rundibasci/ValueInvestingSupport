package it.mazzoni.vis.professional;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.professional.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class InvestmentChecklistService {
    private final InvestmentChecklistRepository checklists;
    private final ChecklistEvaluationRepository evaluations;
    private final UserRepository users;
    private final SecurityRepository securities;
    private final FundamentalSnapshotRepository fundamentals;
    private final RatioSnapshotRepository ratios;
    private final ValuationResultRepository valuations;
    private final ValueScoreRepository scores;

    public InvestmentChecklistService(InvestmentChecklistRepository checklists, ChecklistEvaluationRepository evaluations,
                                      UserRepository users, SecurityRepository securities,
                                      FundamentalSnapshotRepository fundamentals, RatioSnapshotRepository ratios,
                                      ValuationResultRepository valuations, ValueScoreRepository scores) {
        this.checklists = checklists;
        this.evaluations = evaluations;
        this.users = users;
        this.securities = securities;
        this.fundamentals = fundamentals;
        this.ratios = ratios;
        this.valuations = valuations;
        this.scores = scores;
    }

    @Transactional(readOnly = true)
    public List<ChecklistResponse> list(Authentication auth) {
        User user = resolveUser(auth);
        return checklists.findByUserOrderByUpdatedAtDesc(user).stream().map(ChecklistResponse::from).toList();
    }

    @Transactional
    public ChecklistResponse create(Authentication auth, ChecklistRequest request) {
        User user = resolveUser(auth);
        InvestmentChecklist checklist = new InvestmentChecklist();
        checklist.setUser(user);
        apply(checklist, request);
        return ChecklistResponse.from(checklists.save(checklist));
    }

    @Transactional
    public ChecklistResponse update(Authentication auth, UUID id, ChecklistRequest request) {
        InvestmentChecklist checklist = resolveChecklist(auth, id);
        checklist.getCriteria().clear();
        apply(checklist, request);
        return ChecklistResponse.from(checklists.save(checklist));
    }

    @Transactional
    public void delete(Authentication auth, UUID id) {
        checklists.delete(resolveChecklist(auth, id));
    }

    @Transactional
    public ChecklistEvaluationResponse evaluate(Authentication auth, UUID id, String symbol) {
        InvestmentChecklist checklist = resolveChecklist(auth, id);
        String upper = symbol.toUpperCase();
        Optional<Security> security = securities.findBySymbol(upper);
        ChecklistEvaluation evaluation = new ChecklistEvaluation();
        evaluation.setChecklist(checklist);
        evaluation.setSymbol(upper);
        security.ifPresent(evaluation::setSecurity);
        Map<String, BigDecimal> metrics = security.map(this::metricsFor).orElse(Map.of());
        for (ChecklistCriterion criterion : checklist.getCriteria()) {
            ChecklistEvaluationItem item = evaluateCriterion(evaluation, criterion, metrics, security.isPresent());
            evaluation.getItems().add(item);
        }
        return ChecklistEvaluationResponse.from(evaluations.save(evaluation));
    }

    private void apply(InvestmentChecklist checklist, ChecklistRequest request) {
        checklist.setName(request.name());
        checklist.setDescription(request.description());
        List<ChecklistCriterionRequest> criteria = request.criteria() != null ? request.criteria() : List.of();
        for (int i = 0; i < criteria.size(); i++) {
            ChecklistCriterionRequest source = criteria.get(i);
            ChecklistCriterion criterion = new ChecklistCriterion();
            criterion.setChecklist(checklist);
            criterion.setLabel(source.label());
            criterion.setCriterionType(source.criterionType().toUpperCase());
            criterion.setMetricKey(source.metricKey());
            criterion.setOperator(source.operator());
            criterion.setThreshold(source.threshold());
            criterion.setDisplayOrder(i);
            checklist.getCriteria().add(criterion);
        }
    }

    private ChecklistEvaluationItem evaluateCriterion(ChecklistEvaluation evaluation, ChecklistCriterion criterion,
                                                     Map<String, BigDecimal> metrics, boolean securityPresent) {
        ChecklistEvaluationItem item = new ChecklistEvaluationItem();
        item.setEvaluation(evaluation);
        item.setCriterion(criterion);
        item.setLabel(criterion.getLabel());
        if (!"QUANTITATIVE".equals(criterion.getCriterionType())) {
            item.setStatus("MANUAL_REQUIRED");
            item.setMessage("Manual qualitative assessment is required.");
            return item;
        }
        if (!securityPresent) {
            item.setStatus("NO_DATA");
            item.setMessage("Symbol is not in the seeded universe.");
            return item;
        }
        BigDecimal actual = metrics.get(normalize(criterion.getMetricKey()));
        item.setActualValue(actual);
        if (actual == null || criterion.getThreshold() == null || criterion.getOperator() == null) {
            item.setStatus("NO_DATA");
            item.setMessage("Metric, operator, or threshold is unavailable.");
            return item;
        }
        boolean passed = compare(actual, criterion.getOperator(), criterion.getThreshold());
        item.setStatus(passed ? "PASS" : "FAIL");
        item.setMessage("Evaluated from persisted platform data.");
        return item;
    }

    private Map<String, BigDecimal> metricsFor(Security security) {
        Map<String, BigDecimal> out = new HashMap<>();
        fundamentals.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL).ifPresent(f -> {
            out.put("eps", f.getEps());
            out.put("freeCashFlow", f.getFreeCashFlow());
            out.put("revenue", f.getRevenue());
            out.put("netIncome", f.getNetIncome());
            out.put("totalDebt", f.getTotalDebt());
        });
        ratios.findTopBySecurityOrderByReportDateDesc(security).ifPresent(r -> {
            out.put("roic", r.getRoic());
            out.put("roe", r.getRoe());
            out.put("currentRatio", r.getCurrentRatio());
            out.put("debtToEquity", r.getDebtToEquity());
            out.put("dividendYield", r.getDividendYield());
        });
        valuations.findTopBySecurityOrderByValuationDateDesc(security).ifPresent(v -> {
            out.put("marginOfSafety", v.getMarginOfSafety());
            out.put("compositeFairValue", v.getCompositeFairValue());
        });
        scores.findTopBySecurityOrderByScoreDateDesc(security).ifPresent(s -> out.put("valueScore", s.getTotalScore()));
        return out;
    }

    private boolean compare(BigDecimal actual, String operator, BigDecimal threshold) {
        int cmp = actual.compareTo(threshold);
        return switch (operator.trim()) {
            case ">", "GT" -> cmp > 0;
            case ">=", "GTE" -> cmp >= 0;
            case "<", "LT" -> cmp < 0;
            case "<=", "LTE" -> cmp <= 0;
            case "=", "==", "EQ" -> cmp == 0;
            default -> false;
        };
    }

    private String normalize(String key) {
        if (key == null) return "";
        return key.trim();
    }

    private InvestmentChecklist resolveChecklist(Authentication auth, UUID id) {
        User user = resolveUser(auth);
        return checklists.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist not found: " + id));
    }

    private User resolveUser(Authentication auth) {
        return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
