package it.mazzoni.vis.thesis;

import it.mazzoni.vis.domain.entity.InvestmentThesisResult;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ThesisStatus;
import it.mazzoni.vis.domain.repository.InvestmentThesisResultRepository;
import it.mazzoni.vis.thesis.dto.ThesisReviewQueueItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@code /api/v1/admin/**} is already ADMIN-restricted by SecurityConfig's path matcher
 * (verified there, not re-tested here) — this covers the controller's own mapping logic. */
class ThesisAdminControllerTest {

    @Test
    void reviewQueue_mapsRepositoryPageToResponseDtos() {
        InvestmentThesisResultRepository repository = mock(InvestmentThesisResultRepository.class);
        ThesisAdminController controller = new ThesisAdminController(repository);

        Security security = new Security();
        security.setSymbol("AAPL");
        security.setCompanyName("Apple Inc.");
        InvestmentThesisResult row = new InvestmentThesisResult();
        row.setId(UUID.randomUUID());
        row.setSecurity(security);
        row.setStatus(ThesisStatus.HUMAN_REVIEW_PENDING);
        row.setClassification("UNDER_REVIEW");
        row.setHumanReviewRequired(true);

        when(repository.findReviewQueue(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(row)));

        var response = controller.reviewQueue(0, 20);

        assertThat(response.content()).hasSize(1);
        ThesisReviewQueueItemResponse item = response.content().get(0);
        assertThat(item.symbol()).isEqualTo("AAPL");
        assertThat(item.status()).isEqualTo("HUMAN_REVIEW_PENDING");
        assertThat(item.humanReviewRequired()).isTrue();
    }
}
