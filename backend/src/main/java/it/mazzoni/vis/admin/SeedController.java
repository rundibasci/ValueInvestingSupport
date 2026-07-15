package it.mazzoni.vis.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Profile("!demo")
public class SeedController {

    private final List<String> defaultTickers;
    private final SeedRunService seedRunService;

    public SeedController(SeedRunService seedRunService,
                          @Value("${SEED_TICKERS:AAPL,MSFT,KO,JNJ}") String seedTickers) {
        this.seedRunService = seedRunService;
        this.defaultTickers = Arrays.asList(seedTickers.split(","));
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seed(Authentication auth,
            @RequestParam(required = false) String tickers) {
        List<String> symbols = tickers != null
                ? Arrays.asList(tickers.split(","))
                : defaultTickers;
        SeedSubmissionResult submission = seedRunService.submit(auth, symbols, "ADMIN_PACK");
        return submission.asynchronous()
                ? ResponseEntity.accepted().body(submission.accepted())
                : ResponseEntity.ok(submission.synchronousResults());
    }
}
