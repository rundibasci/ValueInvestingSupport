package it.mazzoni.vis.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Profile("!demo")
public class SeedController {

    private final SeedService seedService;
    private final List<String> defaultTickers;

    public SeedController(SeedService seedService,
                          @Value("${SEED_TICKERS:AAPL,MSFT,KO,JNJ}") String seedTickers) {
        this.seedService = seedService;
        this.defaultTickers = Arrays.asList(seedTickers.split(","));
    }

    @PostMapping("/seed")
    public ResponseEntity<List<SeedResult>> seed(
            @RequestParam(required = false) String tickers) {
        List<String> symbols = tickers != null
                ? Arrays.asList(tickers.split(","))
                : defaultTickers;
        return ResponseEntity.ok(seedService.seedTickers(symbols));
    }
}
