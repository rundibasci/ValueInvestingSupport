package it.mazzoni.vis.thesis;

import it.mazzoni.vis.domain.entity.InvestmentThesisResult;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.repository.InvestmentThesisResultRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.thesis.dto.ThesisGenerationAcceptedResponse;
import it.mazzoni.vis.thesis.dto.ThesisResponse;
import it.mazzoni.vis.thesis.dto.ThesisRunStatusResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * On-demand AI Investment Thesis endpoints (specs/tech-stack.md's "Generate AI Thesis"
 * panel backing API, Group TA Phase TA4). Every endpoint here reads {@code {symbol}} the
 * same way {@code InsidersController} does — no duplicated symbol-resolution logic.
 */
@RestController
@RequestMapping("/api/v1/securities/{symbol}/thesis")
@Profile("!demo")
public class ThesisController {

    private final SecurityRepository securityRepository;
    private final UserRepository userRepository;
    private final InvestmentThesisResultRepository thesisRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final ValueScoreRepository valueScoreRepository;
    private final ThesisGenerationService generationService;
    private final ThesisRateLimiter rateLimiter;
    private final ThesisProperties properties;

    public ThesisController(SecurityRepository securityRepository, UserRepository userRepository,
                            InvestmentThesisResultRepository thesisRepository,
                            ValuationResultRepository valuationResultRepository,
                            ValueScoreRepository valueScoreRepository,
                            ThesisGenerationService generationService, ThesisRateLimiter rateLimiter,
                            ThesisProperties properties) {
        this.securityRepository = securityRepository;
        this.userRepository = userRepository;
        this.thesisRepository = thesisRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.valueScoreRepository = valueScoreRepository;
        this.generationService = generationService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/generate")
    public ResponseEntity<ThesisGenerationAcceptedResponse> generate(Authentication authentication,
                                                                       @PathVariable String symbol) {
        if (!properties.isAgentEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Investment Thesis is not enabled");
        }
        Security security = resolveSecurity(symbol);
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + authentication.getName()));

        rateLimiter.checkAndConsume(user.getId());

        UUID thesisRunId = generationService.startGeneration(security, user);
        String statusUrl = "/api/v1/securities/" + symbol.toUpperCase() + "/thesis/runs/" + thesisRunId + "/status";
        return ResponseEntity.accepted().body(
                new ThesisGenerationAcceptedResponse(thesisRunId, "GENERATING", 1500, statusUrl));
    }

    @GetMapping("/runs/{thesisRunId}/status")
    public ThesisRunStatusResponse status(@PathVariable String symbol, @PathVariable UUID thesisRunId) {
        Security security = resolveSecurity(symbol);
        InvestmentThesisResult row = thesisRepository.findByRequestId(thesisRunId)
                .filter(r -> r.getSecurity().getId().equals(security.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown thesis run: " + thesisRunId));

        return new ThesisRunStatusResponse(
                row.getRequestId(), row.getStatus().name(), row.getClassification(), row.getConfidence(),
                row.getHumanReviewRequired(), row.getErrorCode(), row.getGeneratedAt());
    }

    @GetMapping
    public ThesisResponse latest(@PathVariable String symbol) {
        Security security = resolveSecurity(symbol);
        return thesisRepository.findTopBySecurityOrderByGeneratedAtDescCreatedAtDesc(security)
                .map(row -> toResponse(row, security))
                .orElseGet(() -> ThesisResponse.notGenerated(symbol.toUpperCase()));
    }

    private ThesisResponse toResponse(InvestmentThesisResult row, Security security) {
        boolean stale = isStale(row, security);
        ThesisOutput output = parseOutput(row.getOutputJson());
        return new ThesisResponse(row.getId(), security.getSymbol(), row.getStatus().name(), row.getModelId(),
                row.getModelVersion(), row.getPromptVersion(), output, row.getGeneratedAt(), stale);
    }

    private boolean isStale(InvestmentThesisResult row, Security security) {
        if (row.getGeneratedAt() == null) return false;
        LocalDate thesisDate = row.getGeneratedAt().toLocalDate();
        boolean valuationNewer = valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)
                .map(v -> v.getValuationDate().isAfter(thesisDate))
                .orElse(false);
        boolean scoreNewer = valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)
                .map(s -> s.getScoreDate().isAfter(thesisDate))
                .orElse(false);
        return valuationNewer || scoreNewer;
    }

    private ThesisOutput parseOutput(String outputJson) {
        if (outputJson == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .readValue(outputJson, ThesisOutput.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse persisted thesis output", e);
        }
    }

    private Security resolveSecurity(String symbol) {
        return securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));
    }
}
