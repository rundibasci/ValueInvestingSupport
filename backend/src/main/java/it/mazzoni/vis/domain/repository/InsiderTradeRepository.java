package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.InsiderTrade;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InsiderTradeRepository extends JpaRepository<InsiderTrade, UUID> {
    List<InsiderTrade> findBySecurityOrderByTradeDateDesc(Security security);
}
