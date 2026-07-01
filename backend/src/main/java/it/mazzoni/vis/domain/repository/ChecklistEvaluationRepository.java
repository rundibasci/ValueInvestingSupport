package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.ChecklistEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChecklistEvaluationRepository extends JpaRepository<ChecklistEvaluation, UUID> {
}
