package it.mazzoni.vis.thesis;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.thesis.dto.ThesisGenerationAcceptedResponse;
import it.mazzoni.vis.thesis.dto.ThesisResponse;
import it.mazzoni.vis.thesis.dto.ThesisRunStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Plain unit test (mocked repositories/collaborators, no Spring context) — the controller's
 * own logic (symbol resolution, 404 on cross-security thesisRunId, NOT_GENERATED fallback,
 * stale computation) is what's under test, not Spring wiring (already covered structurally
 * by the app-context-loading in the full mvn test run). */
class ThesisControllerTest {

    private final SecurityRepository securityRepository = mock(SecurityRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final InvestmentThesisResultRepository thesisRepository = mock(InvestmentThesisResultRepository.class);
    private final ValuationResultRepository valuationResultRepository = mock(ValuationResultRepository.class);
    private final ValueScoreRepository valueScoreRepository = mock(ValueScoreRepository.class);
    private final ThesisGenerationService generationService = mock(ThesisGenerationService.class);
    private final ThesisRateLimiter rateLimiter = mock(ThesisRateLimiter.class);
    private ThesisProperties properties;
    private ThesisController controller;

    private Security security;
    private User user;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        properties = new ThesisProperties();
        properties.setAgentEnabled(true);
        properties.setGeminiModelId("gemini-2.5-flash");

        controller = new ThesisController(securityRepository, userRepository, thesisRepository,
                valuationResultRepository, valueScoreRepository, generationService, rateLimiter, properties);

        security = new Security();
        security.setId(UUID.randomUUID());
        security.setSymbol("AAPL");
        security.setCompanyName("Apple Inc.");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("investor@example.com");
        user.setRole(UserRole.INVESTOR);

        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("investor@example.com");

        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(security));
        when(userRepository.findByEmail("investor@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void generate_returnsAcceptedWithPollableRunId() {
        UUID requestId = UUID.randomUUID();
        when(generationService.startGeneration(security, user)).thenReturn(requestId);

        var response = controller.generate(authentication, "aapl");

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        ThesisGenerationAcceptedResponse body = response.getBody();
        assertThat(body.thesisRunId()).isEqualTo(requestId);
        assertThat(body.statusUrl()).contains(requestId.toString());
        verify(rateLimiter).checkAndConsume(user.getId());
    }

    @Test
    void generate_rejectsWithServiceUnavailable_whenAgentDisabled() {
        properties.setAgentEnabled(false);

        assertThatThrownBy(() -> controller.generate(authentication, "AAPL"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void generate_propagatesSymbolNotFound_forUnknownSymbol() {
        when(securityRepository.findBySymbol("ZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.generate(authentication, "zzzz"))
                .isInstanceOf(SymbolNotFoundException.class);
    }

    @Test
    void status_returns404_whenRunBelongsToADifferentSecurity() {
        Security otherSecurity = new Security();
        otherSecurity.setId(UUID.randomUUID());
        InvestmentThesisResult row = new InvestmentThesisResult();
        row.setSecurity(otherSecurity);
        UUID runId = UUID.randomUUID();
        when(thesisRepository.findByRequestId(runId)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> controller.status("AAPL", runId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void status_returnsRunDetails_whenFound() {
        InvestmentThesisResult row = new InvestmentThesisResult();
        row.setSecurity(security);
        row.setRequestId(UUID.randomUUID());
        row.setStatus(ThesisStatus.READY);
        row.setClassification("POTENTIALLY_UNDERVALUED");
        when(thesisRepository.findByRequestId(row.getRequestId())).thenReturn(Optional.of(row));

        ThesisRunStatusResponse response = controller.status("AAPL", row.getRequestId());

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.classification()).isEqualTo("POTENTIALLY_UNDERVALUED");
    }

    @Test
    void latest_returnsNotGenerated_whenNoThesisExists() {
        when(thesisRepository.findTopBySecurityOrderByGeneratedAtDescCreatedAtDesc(security))
                .thenReturn(Optional.empty());

        ThesisResponse response = controller.latest("AAPL");

        assertThat(response.status()).isEqualTo("NOT_GENERATED");
        assertThat(response.symbol()).isEqualTo("AAPL");
    }

    @Test
    void latest_marksStale_whenValuationIsNewerThanThesis() {
        InvestmentThesisResult row = new InvestmentThesisResult();
        row.setId(UUID.randomUUID());
        row.setSecurity(security);
        row.setStatus(ThesisStatus.READY);
        row.setGeneratedAt(LocalDateTime.now().minusDays(2));
        row.setOutputJson("""
                {"classification":"FAIRLY_VALUED","confidence":0.6,"summary":"s","bullCase":[],
                 "bearCase":[],"keyRisks":[],"keyAssumptions":[],"invalidationConditions":[],
                 "dataWarnings":[],"humanReviewRequired":false}
                """);
        when(thesisRepository.findTopBySecurityOrderByGeneratedAtDescCreatedAtDesc(security))
                .thenReturn(Optional.of(row));

        ValuationResult newerValuation = new ValuationResult();
        newerValuation.setValuationDate(LocalDate.now());
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.of(newerValuation));
        when(valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.empty());

        ThesisResponse response = controller.latest("AAPL");

        assertThat(response.stale()).isTrue();
        assertThat(response.output().classification()).isEqualTo(ThesisClassification.FAIRLY_VALUED);
    }
}
