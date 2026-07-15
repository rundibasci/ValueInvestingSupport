package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.MarketDataFallbackEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface MarketDataFallbackEventRepository extends
        JpaRepository<MarketDataFallbackEvent, UUID>,
        JpaSpecificationExecutor<MarketDataFallbackEvent> {
}

