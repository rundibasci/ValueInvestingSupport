package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.InvestmentChecklist;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestmentChecklistRepository extends JpaRepository<InvestmentChecklist, UUID> {
    List<InvestmentChecklist> findByUserOrderByUpdatedAtDesc(User user);
    Optional<InvestmentChecklist> findByIdAndUser(UUID id, User user);
}
