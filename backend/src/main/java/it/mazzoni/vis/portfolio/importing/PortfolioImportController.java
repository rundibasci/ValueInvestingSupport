package it.mazzoni.vis.portfolio.importing;

import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportCommitRequest;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportCommitResponse;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportPreviewResponse;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportHistoryResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios/imports")
@Profile("!demo")
public class PortfolioImportController {
    private final PortfolioImportService service;
    public PortfolioImportController(PortfolioImportService service) { this.service = service; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PortfolioImportPreviewResponse preview(Authentication auth, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID portfolioId, @RequestParam(required = false) String baseCurrency,
            @RequestParam(required = false) ImportMode mode) {
        return service.preview(auth, file, portfolioId, baseCurrency, mode);
    }

    @PostMapping("/{importId}/commit")
    public PortfolioImportCommitResponse commit(Authentication auth, @PathVariable UUID importId,
            @Valid @RequestBody PortfolioImportCommitRequest request) {
        return service.commit(auth, importId, request);
    }

    @GetMapping
    public PortfolioImportHistoryResponse history(Authentication auth,
            @RequestParam(required = false) UUID portfolioId, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.history(auth, portfolioId, status, page, size);
    }

    @GetMapping("/{importId}")
    public PortfolioImportPreviewResponse detail(Authentication auth, @PathVariable UUID importId) {
        return service.detail(auth, importId);
    }

    @GetMapping(value = "/{importId}/report.csv", produces = "text/csv")
    public ResponseEntity<byte[]> report(Authentication auth, @PathVariable UUID importId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=portfolio-import-" + importId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(service.reconciliationReport(auth, importId));
    }
}
