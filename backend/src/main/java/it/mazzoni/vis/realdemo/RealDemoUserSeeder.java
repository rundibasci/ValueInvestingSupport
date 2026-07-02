package it.mazzoni.vis.realdemo;

import it.mazzoni.vis.domain.entity.MonitoringReason;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.entity.Watchlist;
import it.mazzoni.vis.domain.entity.WatchlistItem;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.repository.WatchlistItemRepository;
import it.mazzoni.vis.domain.repository.WatchlistRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("realDemo")
public class RealDemoUserSeeder {

    public static final String ADMIN_EMAIL = "admin@realdemo.local";
    public static final String INVESTOR_EMAIL = "investor@realdemo.local";
    public static final String DEMO_PASSWORD = "admin";

    private final UserRepository userRepository;
    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final RealDemoProperties properties;

    public RealDemoUserSeeder(UserRepository userRepository,
                              WatchlistRepository watchlistRepository,
                              WatchlistItemRepository watchlistItemRepository,
                              PasswordEncoder passwordEncoder,
                              RealDemoProperties properties) {
        this.userRepository = userRepository;
        this.watchlistRepository = watchlistRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    @Transactional
    public void seed() {
        createUser(ADMIN_EMAIL, UserRole.ADMIN);
        User investor = createUser(INVESTOR_EMAIL, UserRole.INVESTOR);
        Watchlist watchlist = watchlistRepository.findFirstByUser(investor)
                .orElseGet(() -> {
                    Watchlist created = new Watchlist();
                    created.setUser(investor);
                    created.setName("Real demo curated universe");
                    return watchlistRepository.save(created);
                });
        for (String symbol : properties.tickers()) {
            if (watchlistItemRepository.findBySymbolAndWatchlist_User(symbol, investor).isPresent()) {
                continue;
            }
            WatchlistItem item = new WatchlistItem();
            item.setWatchlist(watchlist);
            item.setSymbol(symbol);
            item.setMonitoringReason(MonitoringReason.DATA_QUALITY_GAP);
            item.setRationaleNote("Real demo startup universe member for conservative workflow validation.");
            watchlistItemRepository.save(item);
        }
    }

    private User createUser(String email, UserRole role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            user.setRole(role);
            return userRepository.save(user);
        });
    }
}
