package it.mazzoni.vis.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/universe")
@Profile("!demo")
public class UniverseSeedController {

    private final SeedService seedService;

    public UniverseSeedController(SeedService seedService) {
        this.seedService = seedService;
    }

    @PostMapping("/seed")
    public ResponseEntity<List<SeedResult>> seed(@RequestParam String tickers) {
        List<String> symbols = Arrays.stream(tickers.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .toList();
        return ResponseEntity.ok(seedService.seedTickers(symbols));
    }
}
