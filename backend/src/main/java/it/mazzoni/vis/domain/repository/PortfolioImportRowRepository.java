package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.PortfolioImport;
import it.mazzoni.vis.domain.entity.PortfolioImportRow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PortfolioImportRowRepository extends JpaRepository<PortfolioImportRow, UUID> {
    List<PortfolioImportRow> findByPortfolioImportOrderByRowNumber(PortfolioImport portfolioImport);
}
