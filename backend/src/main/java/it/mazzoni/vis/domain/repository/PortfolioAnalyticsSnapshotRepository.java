package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.PortfolioAnalyticsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PortfolioAnalyticsSnapshotRepository extends JpaRepository<PortfolioAnalyticsSnapshot, UUID> {
}
