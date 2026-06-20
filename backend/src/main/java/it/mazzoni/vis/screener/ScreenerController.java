package it.mazzoni.vis.screener;

import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.screener.dto.ScreenerRequest;
import it.mazzoni.vis.screener.dto.ScreenerResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/screener")
@Profile("!demo")
public class ScreenerController {

    private final ScreenerService screenerService;
    private final SecurityRepository securityRepository;

    public ScreenerController(ScreenerService screenerService,
                               SecurityRepository securityRepository) {
        this.screenerService = screenerService;
        this.securityRepository = securityRepository;
    }

    @PostMapping
    public ResponseEntity<ScreenerResponse> screen(@RequestBody ScreenerRequest request) {
        return ResponseEntity.ok(screenerService.search(request));
    }

    @GetMapping("/presets")
    public ResponseEntity<Map<String, ScreenerRequest>> presets() {
        return ResponseEntity.ok(Map.of(
                "graham",   ScreenerPresets.GRAHAM,
                "dividend", ScreenerPresets.DIVIDEND,
                "quality",  ScreenerPresets.QUALITY
        ));
    }

    @GetMapping("/sectors")
    public ResponseEntity<List<String>> sectors() {
        return ResponseEntity.ok(securityRepository.findDistinctSectors());
    }

    @GetMapping("/exchanges")
    public ResponseEntity<List<String>> exchanges() {
        return ResponseEntity.ok(securityRepository.findDistinctExchanges());
    }
}
