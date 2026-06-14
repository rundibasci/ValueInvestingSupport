package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Watchlist;
import it.mazzoni.vis.domain.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {
    List<WatchlistItem> findByWatchlist(Watchlist watchlist);
    List<WatchlistItem> findBySymbol(String symbol);
}
