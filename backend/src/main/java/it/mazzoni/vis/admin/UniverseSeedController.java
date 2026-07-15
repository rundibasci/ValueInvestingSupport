package it.mazzoni.vis.admin;

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
@RequestMapping("/api/v1/universe")
@Profile("!demo")
public class UniverseSeedController {

    private final SeedRunService seedRunService;

    public UniverseSeedController(SeedRunService seedRunService) {
        this.seedRunService = seedRunService;
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seed(Authentication auth, @RequestParam String tickers) {
        List<String> symbols = Arrays.stream(tickers.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .toList();
        SeedSubmissionResult submission = seedRunService.submit(auth, symbols, "CSV");
        return submission.asynchronous()
                ? ResponseEntity.accepted().body(submission.accepted())
                : ResponseEntity.ok(submission.synchronousResults());
    }
}
