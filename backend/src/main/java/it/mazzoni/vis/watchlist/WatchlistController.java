package it.mazzoni.vis.watchlist;

import it.mazzoni.vis.watchlist.dto.AddWatchlistItemRequest;
import it.mazzoni.vis.watchlist.dto.AlertResponse;
import it.mazzoni.vis.watchlist.dto.UpdateWatchlistThresholdRequest;
import it.mazzoni.vis.watchlist.dto.WatchlistItemResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchlist")
@Profile("!demo")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItemResponse> list(Authentication auth) {
        return watchlistService.list(auth);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistItemResponse add(Authentication auth,
                                     @Valid @RequestBody AddWatchlistItemRequest request) {
        return watchlistService.add(auth, request);
    }

    @PutMapping("/{id}")
    public WatchlistItemResponse updateThresholds(Authentication auth,
                                                  @PathVariable UUID id,
                                                  @RequestBody UpdateWatchlistThresholdRequest request) {
        return watchlistService.updateThresholds(auth, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable UUID id) {
        watchlistService.remove(auth, id);
    }

    @GetMapping("/alerts")
    public List<AlertResponse> listAlerts(Authentication auth) {
        return watchlistService.listActiveAlerts(auth);
    }
}
