package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {
    List<Watchlist> findByUser(User user);
    Optional<Watchlist> findFirstByUser(User user);
}
