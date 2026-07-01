package it.mazzoni.vis.watchlist;

import it.mazzoni.vis.domain.entity.Alert;
import it.mazzoni.vis.domain.entity.AlertStatus;
import it.mazzoni.vis.domain.entity.MonitoringReason;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.Watchlist;
import it.mazzoni.vis.domain.entity.WatchlistItem;
import it.mazzoni.vis.domain.repository.AlertRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.repository.WatchlistItemRepository;
import it.mazzoni.vis.domain.repository.WatchlistRepository;
import it.mazzoni.vis.professional.ResearchDecisionAuditService;
import it.mazzoni.vis.watchlist.dto.AddWatchlistItemRequest;
import it.mazzoni.vis.watchlist.dto.AlertResponse;
import it.mazzoni.vis.watchlist.dto.UpdateWatchlistThresholdRequest;
import it.mazzoni.vis.watchlist.dto.WatchlistItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final UserRepository userRepository;
    private final AlertRepository alertRepository;
    private final ResearchDecisionAuditService auditService;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            WatchlistItemRepository watchlistItemRepository,
                            UserRepository userRepository,
                            AlertRepository alertRepository) {
        this(watchlistRepository, watchlistItemRepository, userRepository, alertRepository, null);
    }

    @Autowired
    public WatchlistService(WatchlistRepository watchlistRepository,
                            WatchlistItemRepository watchlistItemRepository,
                            UserRepository userRepository,
                            AlertRepository alertRepository,
                            ResearchDecisionAuditService auditService) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.userRepository = userRepository;
        this.alertRepository = alertRepository;
        this.auditService = auditService;
    }

    public List<WatchlistItemResponse> list(Authentication auth) {
        User user = resolveUser(auth);
        return watchlistItemRepository.findByWatchlist_UserOrderByAddedAtDesc(user)
                .stream()
                .map(WatchlistItemResponse::from)
                .toList();
    }

    public WatchlistItemResponse add(Authentication auth, AddWatchlistItemRequest request) {
        User user = resolveUser(auth);
        String symbol = request.symbol().toUpperCase();

        watchlistItemRepository.findBySymbolAndWatchlist_User(symbol, user).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Symbol already in watchlist: " + symbol);
        });

        Watchlist watchlist = getOrCreateWatchlist(user);

        WatchlistItem item = new WatchlistItem();
        item.setWatchlist(watchlist);
        item.setSymbol(symbol);
        item.setMosAlertMin(request.mosAlertMin());
        item.setMosAlertMax(request.mosAlertMax());
        item.setFundamentalDegradeThreshold(request.fundamentalDegradeThreshold());
        item.setMonitoringReason(parseReason(request.monitoringReason()));
        item.setRationaleNote(normalizeNote(request.rationaleNote()));

        WatchlistItem saved = watchlistItemRepository.save(item);
        captureAudit(user, symbol, "ADD_WATCHLIST", saved.getRationaleNote());
        return WatchlistItemResponse.from(saved);
    }

    public WatchlistItemResponse updateThresholds(Authentication auth, UUID id,
                                                  UpdateWatchlistThresholdRequest request) {
        User user = resolveUser(auth);
        WatchlistItem item = watchlistItemRepository.findByIdAndWatchlist_User(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Watchlist item not found: " + id));

        item.setMosAlertMin(request.mosAlertMin());
        item.setMosAlertMax(request.mosAlertMax());
        item.setFundamentalDegradeThreshold(request.fundamentalDegradeThreshold());
        item.setMonitoringReason(parseReason(request.monitoringReason()));
        item.setRationaleNote(normalizeNote(request.rationaleNote()));

        return WatchlistItemResponse.from(watchlistItemRepository.save(item));
    }

    public void remove(Authentication auth, UUID id) {
        User user = resolveUser(auth);
        WatchlistItem item = watchlistItemRepository.findByIdAndWatchlist_User(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Watchlist item not found: " + id));
        String symbol = item.getSymbol();
        String rationale = item.getRationaleNote();
        watchlistItemRepository.delete(item);
        captureAudit(user, symbol, "REMOVE_WATCHLIST", rationale);
    }

    public List<AlertResponse> listActiveAlerts(Authentication auth) {
        User user = resolveUser(auth);
        List<Alert> alerts = alertRepository.findByUserAndStatus(user, AlertStatus.ACTIVE);
        return alerts.stream().map(AlertResponse::from).toList();
    }

    private User resolveUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Watchlist getOrCreateWatchlist(User user) {
        return watchlistRepository.findFirstByUser(user).orElseGet(() -> {
            Watchlist wl = new Watchlist();
            wl.setUser(user);
            wl.setName("My Watchlist");
            return watchlistRepository.save(wl);
        });
    }

    private void captureAudit(User user, String symbol, String actionType, String rationale) {
        if (auditService != null) {
            auditService.capture(user, symbol, actionType, rationale);
        }
    }

    private MonitoringReason parseReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MonitoringReason.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown monitoring reason: " + value);
        }
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
