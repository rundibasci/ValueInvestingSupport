package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Portfolio;
import it.mazzoni.vis.domain.entity.RebalanceProposal;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface RebalanceProposalRepository extends JpaRepository<RebalanceProposal, UUID> {

    Optional<RebalanceProposal> findByIdAndPortfolio(UUID id, Portfolio portfolio);
    List<RebalanceProposal> findByPortfolio_UserAndStatus(User user, String status);
}
