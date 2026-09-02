package it.mazzoni.vis.thesis;

import it.mazzoni.vis.domain.entity.InvestmentThesisResult;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ThesisStatus;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.InvestmentThesisResultRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({ThesisGenerationService.class, ThesisGenerationServiceTest.Config.class})
class ThesisGenerationServiceTest {

    @Autowired ThesisGenerationService service;
    @Autowired InvestmentThesisResultRepository repository;
    @PersistenceContext EntityManager em;

    static InvestmentThesisClient fakeClient;
    static ThesisInputSource fakeInputBuilder;

    private Security security;
    private User user;

    @BeforeEach
    void setUp() {
        security = new Security();
        security.setSymbol("AAPL");
        security.setCompanyName("Apple Inc.");
        em.persist(security);

        user = new User();
        user.setEmail("investor@example.com");
        user.setRole(UserRole.INVESTOR);
        em.persist(user);
        em.flush();

        fakeInputBuilder = sec -> new ThesisInput(sec.getSymbol(), sec.getCompanyName(), LocalDate.now(),
                new BigDecimal("100"), new BigDecimal("120"), new BigDecimal("16.67"),
                new BigDecimal("70"), null, null, null, null, null, null, null, null,
                Trend.STABLE, Trend.STABLE, Trend.STABLE,
                DataQuality.COMPLETE, List.of());
    }

    @Test
    void runGeneration_persistsReady_onCleanSuccess() {
        UUID requestId = UUID.randomUUID();
        InvestmentThesisResult initial = initialRow(requestId);
        em.persist(initial);
        em.flush();

        fakeClient = request -> new ThesisGenerationResult.Success(
                request.requestId(), "gemini-2.5-flash", "gemini-2.5-flash", "system-prompt-v3", 500,
                new ThesisOutput(ThesisClassification.POTENTIALLY_UNDERVALUED, new BigDecimal("0.7"), "s",
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false));

        service.runGeneration(requestId, security);

        InvestmentThesisResult saved = repository.findByRequestId(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ThesisStatus.READY);
        assertThat(saved.getClassification()).isEqualTo("POTENTIALLY_UNDERVALUED");
    }

    @Test
    void runGeneration_persistsHumanReviewPending_whenOutputRequiresReview() {
        UUID requestId = UUID.randomUUID();
        em.persist(initialRow(requestId));
        em.flush();

        fakeClient = request -> new ThesisGenerationResult.Success(
                request.requestId(), "gemini-2.5-flash", "gemini-2.5-flash", "system-prompt-v3", 500,
                new ThesisOutput(ThesisClassification.UNDER_REVIEW, new BigDecimal("0.5"), "s",
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), true));

        service.runGeneration(requestId, security);

        InvestmentThesisResult saved = repository.findByRequestId(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ThesisStatus.HUMAN_REVIEW_PENDING);
        assertThat(saved.getHumanReviewRequired()).isTrue();
    }

    @Test
    void runGeneration_persistsFailed_withDeterministicFallbackBody_onFailure() {
        UUID requestId = UUID.randomUUID();
        em.persist(initialRow(requestId));
        em.flush();

        fakeClient = request -> new ThesisGenerationResult.Failure(
                request.requestId(), ThesisErrorCode.TIMEOUT, "deadline exceeded", false);

        service.runGeneration(requestId, security);

        InvestmentThesisResult saved = repository.findByRequestId(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ThesisStatus.FAILED);
        assertThat(saved.getClassification()).isEqualTo("UNDER_REVIEW");
        assertThat(saved.getHumanReviewRequired()).isTrue();
        assertThat(saved.getOutputJson()).contains("\"bullCase\":[]").contains("\"bearCase\":[]");
        assertThat(saved.getErrorCode()).isEqualTo("TIMEOUT");
    }

    private InvestmentThesisResult initialRow(UUID requestId) {
        InvestmentThesisResult row = new InvestmentThesisResult();
        row.setSecurity(security);
        row.setRequestId(requestId);
        row.setRequestedByUser(user);
        row.setModelId("gemini-2.5-flash");
        row.setModelVersion("gemini-2.5-flash");
        row.setPromptVersion("system-prompt-v3");
        row.setStatus(ThesisStatus.GENERATING);
        row.setInputSnapshot("{}");
        return row;
    }

    @TestConfiguration
    static class Config {
        @Bean
        InvestmentThesisClient investmentThesisClient() {
            return request -> fakeClient.generate(request);
        }

        @Bean
        ThesisInputSource thesisInputSource() {
            return security -> fakeInputBuilder.build(security);
        }

        @Bean
        ThesisProperties thesisProperties() {
            ThesisProperties properties = new ThesisProperties();
            properties.setGeminiModelId("gemini-2.5-flash");
            properties.setPromptVersion("system-prompt-v3");
            return properties;
        }
    }
}
