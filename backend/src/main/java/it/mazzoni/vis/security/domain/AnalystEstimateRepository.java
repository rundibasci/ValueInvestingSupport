package it.mazzoni.vis.security.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnalystEstimateRepository extends JpaRepository<AnalystEstimate, Long> {
    List<AnalystEstimate> findBySecuritySymbolOrderByTargetDateDesc(String symbol);
}
