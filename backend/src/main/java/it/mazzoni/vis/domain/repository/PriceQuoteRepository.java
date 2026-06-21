package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceQuoteRepository extends JpaRepository<PriceQuote, UUID> {
    List<PriceQuote> findBySecurityAndQuoteDateBetweenOrderByQuoteDateDesc(Security security, LocalDate from, LocalDate to);
    Optional<PriceQuote> findTopBySecurityOrderByQuoteDateDesc(Security security);
    List<PriceQuote> findTop2BySecurityOrderByQuoteDateDesc(Security security);
    boolean existsBySecurityAndQuoteDate(Security security, LocalDate quoteDate);
}
