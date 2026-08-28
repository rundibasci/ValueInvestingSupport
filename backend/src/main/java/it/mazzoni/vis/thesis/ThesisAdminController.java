package it.mazzoni.vis.thesis;

import it.mazzoni.vis.admin.PageResponse;
import it.mazzoni.vis.domain.entity.InvestmentThesisResult;
import it.mazzoni.vis.domain.repository.InvestmentThesisResultRepository;
import it.mazzoni.vis.thesis.dto.ThesisReviewQueueItemResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only review queue (TRAIN-12.5's audit-retention scope). {@code /api/v1/admin/**} is
 * already restricted to ROLE_ADMIN by SecurityConfig's path matcher — no extra
 * method-level annotation needed, matching this codebase's existing admin-endpoint convention.
 */
@RestController
@RequestMapping("/api/v1/admin/thesis")
@Profile("!demo")
public class ThesisAdminController {

    private final InvestmentThesisResultRepository repository;

    public ThesisAdminController(InvestmentThesisResultRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/review-queue")
    public PageResponse<ThesisReviewQueueItemResponse> reviewQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<InvestmentThesisResult> results = repository.findReviewQueue(PageRequest.of(page, size));
        return PageResponse.from(results.map(this::toItem));
    }

    private ThesisReviewQueueItemResponse toItem(InvestmentThesisResult row) {
        return new ThesisReviewQueueItemResponse(
                row.getId(), row.getSecurity().getSymbol(), row.getSecurity().getCompanyName(),
                row.getStatus().name(), row.getClassification(), row.getHumanReviewRequired(),
                row.isDataWarningsPresent(), row.getGeneratedAt());
    }
}
