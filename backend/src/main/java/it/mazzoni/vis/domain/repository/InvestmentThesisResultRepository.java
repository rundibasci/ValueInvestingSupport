package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.InvestmentThesisResult;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ThesisStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface InvestmentThesisResultRepository extends JpaRepository<InvestmentThesisResult, UUID> {

    Optional<InvestmentThesisResult> findTopBySecurityOrderByGeneratedAtDescCreatedAtDesc(Security security);

    Optional<InvestmentThesisResult> findByRequestId(UUID requestId);

    // Review queue (TRAIN-12.5's audit-retention scope): HUMAN_REVIEW_PENDING status or
    // non-empty dataWarnings — not just the narrower humanReviewRequired=true case. Backs
    // idx_thesis_review_queue (V27).
    @Query("""
        SELECT t FROM InvestmentThesisResult t
        WHERE t.status = :pending OR t.dataWarningsPresent = true
        ORDER BY t.generatedAt DESC, t.createdAt DESC
        """)
    Page<InvestmentThesisResult> findReviewQueue(ThesisStatus pending, Pageable pageable);

    default Page<InvestmentThesisResult> findReviewQueue(Pageable pageable) {
        return findReviewQueue(ThesisStatus.HUMAN_REVIEW_PENDING, pageable);
    }
}
