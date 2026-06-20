package it.mazzoni.vis.scoring;

import it.mazzoni.vis.scoring.dto.ValueScoreResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @GetMapping("/{symbol}/score")
    public ResponseEntity<ValueScoreResponse> score(@PathVariable String symbol) {
        return ResponseEntity.ok(scoreService.getScore(symbol));
    }
}
