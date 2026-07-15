package it.mazzoni.vis.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/universe")
@Profile("!demo")
public class UniverseSelectionController {

    private final UniverseSelectionService universeSelectionService;
    private final SeedRunService seedRunService;

    public UniverseSelectionController(UniverseSelectionService universeSelectionService, SeedRunService seedRunService) {
        this.universeSelectionService = universeSelectionService;
        this.seedRunService = seedRunService;
    }

    @GetMapping("/templates")
    public ResponseEntity<List<UniverseTemplateResponse>> templates() {
        return ResponseEntity.ok(universeSelectionService.templates());
    }

    @PostMapping("/preview")
    public ResponseEntity<UniversePreviewResponse> preview(@RequestBody(required = false) UniverseSelectionRequest request) {
        return ResponseEntity.ok(universeSelectionService.preview(request));
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seed(Authentication auth, @RequestBody(required = false) UniverseSelectionRequest request) {
        UniversePreviewResponse preview = universeSelectionService.preview(request);
        List<String> symbols = preview.symbols().stream().map(UniversePreviewRow::symbol).toList();
        SeedSubmissionResult submission = seedRunService.submit(auth, symbols, "UNIVERSE_CURATION");
        return submission.asynchronous()
                ? ResponseEntity.accepted().body(new UniverseSeedAsyncResponse(preview, submission.accepted()))
                : ResponseEntity.ok(new UniverseSeedCriteriaResponse(preview, submission.synchronousResults()));
    }
}
