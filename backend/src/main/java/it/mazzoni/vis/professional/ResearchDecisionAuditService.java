package it.mazzoni.vis.professional;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.professional.dto.ResearchSnapshotResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResearchDecisionAuditService {
    private final ResearchSnapshotRepository snapshots;
    private final UserRepository users;
    private final SecurityRepository securities;
    private final PriceQuoteRepository quotes;
    private final ValuationResultRepository valuations;
    private final ValueScoreRepository scores;
    private final WaccResultRepository waccResults;
    private final PiotroskiResultRepository piotroskiResults;
    private final MoatResultRepository moatResults;

    public ResearchDecisionAuditService(ResearchSnapshotRepository snapshots, UserRepository users,
                                        SecurityRepository securities, PriceQuoteRepository quotes,
                                        ValuationResultRepository valuations, ValueScoreRepository scores,
                                        WaccResultRepository waccResults, PiotroskiResultRepository piotroskiResults,
                                        MoatResultRepository moatResults) {
        this.snapshots = snapshots;
        this.users = users;
        this.securities = securities;
        this.quotes = quotes;
        this.valuations = valuations;
        this.scores = scores;
        this.waccResults = waccResults;
        this.piotroskiResults = piotroskiResults;
        this.moatResults = moatResults;
    }

    @Transactional
    public void capture(User user, String symbol, String actionType, String rationale) {
        String upper = symbol.toUpperCase();
        ResearchSnapshot snapshot = new ResearchSnapshot();
        snapshot.setUser(user);
        snapshot.setSymbol(upper);
        snapshot.setActionType(actionType);
        snapshot.setRationale(rationale);
        securities.findBySymbol(upper).ifPresent(security -> populate(snapshot, security));
        snapshots.save(snapshot);
    }

    @Transactional(readOnly = true)
    public List<ResearchSnapshotResponse> list(Authentication auth, String symbol, LocalDate from, LocalDate to) {
        User user = resolveUser(auth);
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDate.now().minusYears(10).atStartOfDay();
        LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay() : LocalDate.now().plusDays(1).atStartOfDay();
        boolean admin = user.getRole() == UserRole.ADMIN;
        List<ResearchSnapshot> rows;
        if (admin && symbol != null && !symbol.isBlank()) {
            rows = snapshots.findBySymbolIgnoreCaseAndCapturedAtBetweenOrderByCapturedAtDesc(symbol, start, end);
        } else if (admin) {
            rows = snapshots.findByCapturedAtBetweenOrderByCapturedAtDesc(start, end);
        } else if (symbol != null && !symbol.isBlank()) {
            rows = snapshots.findByUserAndSymbolIgnoreCaseAndCapturedAtBetweenOrderByCapturedAtDesc(user, symbol, start, end);
        } else {
            rows = snapshots.findByUserAndCapturedAtBetweenOrderByCapturedAtDesc(user, start, end);
        }
        return rows.stream().map(ResearchSnapshotResponse::from).toList();
    }

    private void populate(ResearchSnapshot snapshot, Security security) {
        snapshot.setSecurity(security);
        quotes.findTopBySecurityOrderByQuoteDateDesc(security).ifPresent(q -> snapshot.setCurrentPrice(q.getClose()));
        valuations.findTopBySecurityOrderByValuationDateDesc(security).ifPresent(v -> {
            snapshot.setCompositeFairValue(v.getCompositeFairValue());
            snapshot.setMarginOfSafety(v.getMarginOfSafety());
            snapshot.setDataSource(v.getSource());
            waccResults.findByValuationResult(v).ifPresent(w -> snapshot.setWaccUsed(w.getWacc()));
        });
        scores.findTopBySecurityOrderByScoreDateDesc(security).ifPresent(s -> snapshot.setValueScore(s.getTotalScore()));
        piotroskiResults.findTopBySecurityOrderByResultDateDesc(security).ifPresent(p -> snapshot.setPiotroskiScore(p.getTotalScore()));
        moatResults.findTopBySecurityOrderByResultDateDesc(security).ifPresent(m -> {
            if (m.getMoatStrength() != null) snapshot.setMoatClassification(m.getMoatStrength().name());
        });
    }

    private User resolveUser(Authentication auth) {
        return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
