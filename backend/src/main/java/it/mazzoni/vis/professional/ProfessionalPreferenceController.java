package it.mazzoni.vis.professional;

import it.mazzoni.vis.professional.dto.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!demo")
public class ProfessionalPreferenceController {
    private final ProfessionalPreferenceService service;

    public ProfessionalPreferenceController(ProfessionalPreferenceService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/preferences/competence")
    public CompetencePreferencesResponse getCompetence(Authentication auth) {
        return service.getCompetence(auth);
    }

    @PutMapping("/api/v1/preferences/competence")
    public CompetencePreferencesResponse updateCompetence(Authentication auth, @RequestBody CompetencePreferencesRequest request) {
        return service.updateCompetence(auth, request);
    }

    @GetMapping("/api/v1/advisor/acknowledgement")
    public AdvisorAcknowledgementResponse getAdvisorAcknowledgement(Authentication auth) {
        return service.getAdvisorAcknowledgement(auth);
    }

    @PutMapping("/api/v1/advisor/acknowledgement")
    public AdvisorAcknowledgementResponse acknowledgeAdvisor(Authentication auth, @RequestBody AdvisorAcknowledgementRequest request) {
        return service.acknowledgeAdvisor(auth, request);
    }
}
