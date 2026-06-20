package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.security.dto.SecuritySearchItem;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class SecuritySearchController {

    private final SecurityRepository securityRepository;

    public SecuritySearchController(SecurityRepository securityRepository) {
        this.securityRepository = securityRepository;
    }

    @GetMapping("/search")
    public ResponseEntity<List<SecuritySearchItem>> search(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<SecuritySearchItem> results = securityRepository
                .findTop10BySymbolContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(q, q)
                .stream()
                .map(SecuritySearchItem::from)
                .toList();
        return ResponseEntity.ok(results);
    }
}
