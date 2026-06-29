package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.GrahamChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GrahamChecklistItemRepository extends JpaRepository<GrahamChecklistItem, UUID> {}
