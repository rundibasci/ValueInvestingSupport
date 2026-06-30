package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.GrahamChecklistItem;
import it.mazzoni.vis.domain.entity.ValuationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GrahamChecklistItemRepository extends JpaRepository<GrahamChecklistItem, UUID> {
    List<GrahamChecklistItem> findByValuationResultOrderByCriterionCodeAsc(ValuationResult valuationResult);
}
