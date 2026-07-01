package it.mazzoni.vis.professional;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.professional.dto.ChecklistCriterionRequest;
import it.mazzoni.vis.professional.dto.ChecklistRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvestmentChecklistServiceTest {
    @Test
    void evaluatesQuantitativeCriterionAgainstPersistedMetric() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setRole(UserRole.INVESTOR);
        Security security = new Security();
        security.setSymbol("JNJ");

        InvestmentChecklist checklist = new InvestmentChecklist();
        checklist.setUser(user);
        checklist.setName("Value");
        ChecklistCriterion criterion = new ChecklistCriterion();
        criterion.setChecklist(checklist);
        criterion.setLabel("MoS above 10");
        criterion.setCriterionType("QUANTITATIVE");
        criterion.setMetricKey("marginOfSafety");
        criterion.setOperator(">=");
        criterion.setThreshold(new BigDecimal("10"));
        checklist.getCriteria().add(criterion);

        InvestmentChecklistRepository checklists = mock(InvestmentChecklistRepository.class);
        ChecklistEvaluationRepository evaluations = mock(ChecklistEvaluationRepository.class);
        UserRepository users = mock(UserRepository.class);
        SecurityRepository securities = mock(SecurityRepository.class);
        FundamentalSnapshotRepository fundamentals = mock(FundamentalSnapshotRepository.class);
        RatioSnapshotRepository ratios = mock(RatioSnapshotRepository.class);
        ValuationResultRepository valuations = mock(ValuationResultRepository.class);
        ValueScoreRepository scores = mock(ValueScoreRepository.class);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(checklists.findByIdAndUser(any(), eq(user))).thenReturn(Optional.of(checklist));
        when(securities.findBySymbol("JNJ")).thenReturn(Optional.of(security));
        ValuationResult valuation = new ValuationResult();
        valuation.setMarginOfSafety(new BigDecimal("15"));
        when(valuations.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.of(valuation));
        when(evaluations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var service = new InvestmentChecklistService(checklists, evaluations, users, securities, fundamentals, ratios, valuations, scores);
        var response = service.evaluate(new TestingAuthenticationToken("user@example.com", "n/a"), java.util.UUID.randomUUID(), "jnj");

        assertThat(response.items()).singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("PASS"));
    }

    @Test
    void createsChecklistWithCriteria() {
        User user = new User();
        user.setEmail("user@example.com");
        InvestmentChecklistRepository checklists = mock(InvestmentChecklistRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(checklists.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var service = new InvestmentChecklistService(checklists, mock(ChecklistEvaluationRepository.class), users,
                mock(SecurityRepository.class), mock(FundamentalSnapshotRepository.class), mock(RatioSnapshotRepository.class),
                mock(ValuationResultRepository.class), mock(ValueScoreRepository.class));
        var request = new ChecklistRequest("Value", null,
                List.of(new ChecklistCriterionRequest("ROIC", "QUANTITATIVE", "roic", ">", BigDecimal.TEN)));

        var response = service.create(new TestingAuthenticationToken("user@example.com", "n/a"), request);

        assertThat(response.criteria()).hasSize(1);
        assertThat(response.criteria().getFirst().metricKey()).isEqualTo("roic");
    }
}
