package it.mazzoni.vis.security;

import it.mazzoni.vis.security.dto.SecurityReviewResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class SecurityReviewController {

    private final SecurityReviewService securityReviewService;

    public SecurityReviewController(SecurityReviewService securityReviewService) {
        this.securityReviewService = securityReviewService;
    }

    @GetMapping("/{symbol}/review")
    public ResponseEntity<SecurityReviewResponse> review(@PathVariable String symbol) {
        return ResponseEntity.ok(securityReviewService.getReview(symbol));
    }
}
