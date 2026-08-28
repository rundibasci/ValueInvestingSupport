package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.InvestmentThesisResult;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ThesisStatus;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class InvestmentThesisResultRepositoryTest {

    @Autowired InvestmentThesisResultRepository repository;
    @PersistenceContext EntityManager em;

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
    }

    private InvestmentThesisResult row(ThesisStatus status, LocalDateTime generatedAt, boolean dataWarnings) {
        InvestmentThesisResult row = new InvestmentThesisResult();
        row.setSecurity(security);
        row.setRequestId(UUID.randomUUID());
        row.setRequestedByUser(user);
        row.setModelId("gemini-2.5-flash");
        row.setModelVersion("gemini-2.5-flash");
        row.setPromptVersion("system-prompt-v3");
        row.setInputSnapshot("{}");
        row.setStatus(status);
        row.setGeneratedAt(generatedAt);
        row.setDataWarningsPresent(dataWarnings);
        return row;
    }

    @Test
    void findTopBySecurity_returnsMostRecentByGeneratedAt() {
        em.persist(row(ThesisStatus.READY, LocalDateTime.now().minusDays(1), false));
        InvestmentThesisResult newer = row(ThesisStatus.READY, LocalDateTime.now(), false);
        em.persist(newer);
        em.flush();

        var found = repository.findTopBySecurityOrderByGeneratedAtDescCreatedAtDesc(security);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(newer.getId());
    }

    @Test
    void findByRequestId_findsExactRow() {
        InvestmentThesisResult saved = row(ThesisStatus.READY, LocalDateTime.now(), false);
        em.persist(saved);
        em.flush();

        assertThat(repository.findByRequestId(saved.getRequestId())).isPresent();
        assertThat(repository.findByRequestId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findReviewQueue_includesHumanReviewPendingAndDataWarningRows_excludesPlainReady() {
        em.persist(row(ThesisStatus.HUMAN_REVIEW_PENDING, LocalDateTime.now(), false));
        em.persist(row(ThesisStatus.FAILED, LocalDateTime.now(), true));
        em.persist(row(ThesisStatus.READY, LocalDateTime.now(), false));
        em.flush();

        List<InvestmentThesisResult> queue = repository.findReviewQueue(
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent();

        assertThat(queue).hasSize(2);
        assertThat(queue).noneMatch(r -> r.getStatus() == ThesisStatus.READY);
    }
}
