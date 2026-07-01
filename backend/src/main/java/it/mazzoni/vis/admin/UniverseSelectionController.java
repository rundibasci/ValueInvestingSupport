package it.mazzoni.vis.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/universe")
@Profile("!demo")
public class UniverseSelectionController {

    private final UniverseSelectionService universeSelectionService;

    public UniverseSelectionController(UniverseSelectionService universeSelectionService) {
        this.universeSelectionService = universeSelectionService;
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
    public ResponseEntity<UniverseSeedCriteriaResponse> seed(@RequestBody(required = false) UniverseSelectionRequest request) {
        return ResponseEntity.ok(universeSelectionService.seed(request));
    }
}
