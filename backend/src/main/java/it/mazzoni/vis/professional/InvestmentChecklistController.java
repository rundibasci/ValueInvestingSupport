package it.mazzoni.vis.professional;

import it.mazzoni.vis.professional.dto.*;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checklists")
@Profile("!demo")
public class InvestmentChecklistController {
    private final InvestmentChecklistService service;

    public InvestmentChecklistController(InvestmentChecklistService service) {
        this.service = service;
    }

    @GetMapping
    public List<ChecklistResponse> list(Authentication auth) {
        return service.list(auth);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChecklistResponse create(Authentication auth, @Valid @RequestBody ChecklistRequest request) {
        return service.create(auth, request);
    }

    @PutMapping("/{id}")
    public ChecklistResponse update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ChecklistRequest request) {
        return service.update(auth, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID id) {
        service.delete(auth, id);
    }

    @PostMapping("/{id}/evaluate/{symbol}")
    @ResponseStatus(HttpStatus.CREATED)
    public ChecklistEvaluationResponse evaluate(Authentication auth, @PathVariable UUID id, @PathVariable String symbol) {
        return service.evaluate(auth, id, symbol);
    }
}
