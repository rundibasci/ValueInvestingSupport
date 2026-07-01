package it.mazzoni.vis.professional;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.professional.dto.VerificationResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataVerificationService {
    private final SecurityRepository securities;
    private final FundamentalSnapshotRepository fundamentals;

    public DataVerificationService(SecurityRepository securities, FundamentalSnapshotRepository fundamentals) {
        this.securities = securities;
        this.fundamentals = fundamentals;
    }

    public VerificationResponse check(String symbol) {
        String upper = symbol.toUpperCase();
        Security security = securities.findBySymbol(upper).orElse(null);
        List<VerificationResponse.Flag> flags = new ArrayList<>();
        if (security == null) {
            flags.add(flag("symbol", "WARNING", "Symbol is not in the seeded universe."));
            return new VerificationResponse(upper, flags);
        }
        List<FundamentalSnapshot> annuals = fundamentals.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL);
        if (annuals.isEmpty()) {
            flags.add(flag("fundamentals", "WARNING", "No annual fundamental snapshots are available."));
            return new VerificationResponse(upper, flags);
        }
        FundamentalSnapshot latest = annuals.get(0);
        if (latest.getReportDate() == null || latest.getReportDate().isBefore(LocalDate.now().minusDays(90))) {
            flags.add(flag("reportDate", "INFO", "Latest annual fundamental data may be stale."));
        }
        if (latest.getEps() == null || latest.getTotalEquity() == null || latest.getFreeCashFlow() == null || latest.getSharesOutstanding() == null) {
            flags.add(flag("criticalFields", "WARNING", "One or more critical fields are missing: EPS, total equity/book-value proxy, FCF, or shares outstanding."));
        }
        if (annuals.size() >= 2) {
            addLargeChangeFlag(flags, "revenue", annuals.get(0).getRevenue(), annuals.get(1).getRevenue());
            addLargeChangeFlag(flags, "eps", annuals.get(0).getEps(), annuals.get(1).getEps());
        }
        if (flags.isEmpty()) {
            flags.add(flag("verification", "INFO", "No deterministic verification flags were triggered."));
        }
        return new VerificationResponse(upper, flags);
    }

    private void addLargeChangeFlag(List<VerificationResponse.Flag> flags, String field, BigDecimal current, BigDecimal prior) {
        if (current == null || prior == null || prior.compareTo(BigDecimal.ZERO) == 0) return;
        BigDecimal change = current.subtract(prior).abs().divide(prior.abs(), 4, java.math.RoundingMode.HALF_UP);
        if (change.compareTo(new BigDecimal("0.50")) > 0) {
            flags.add(flag(field, "INFO", field + " changed by more than 50% versus the prior annual snapshot; verify source filings."));
        }
    }

    private VerificationResponse.Flag flag(String field, String severity, String message) {
        return new VerificationResponse.Flag(field, severity, message);
    }
}
