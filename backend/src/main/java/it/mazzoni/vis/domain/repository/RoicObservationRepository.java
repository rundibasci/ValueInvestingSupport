package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.RoicObservation;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoicObservationRepository extends JpaRepository<RoicObservation, UUID> {
    List<RoicObservation> findBySecurityOrderByFiscalYearDesc(Security security);
    long deleteBySecurity(Security security);
}
