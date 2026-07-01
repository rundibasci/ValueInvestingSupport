package it.mazzoni.vis.professional;

import it.mazzoni.vis.domain.entity.AdvisorAcknowledgement;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserCompetencePreferences;
import it.mazzoni.vis.domain.repository.AdvisorAcknowledgementRepository;
import it.mazzoni.vis.domain.repository.UserCompetencePreferencesRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.professional.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class ProfessionalPreferenceService {
    public static final String ADVISOR_DISCLAIMER = "This tool supports your research process. Suitability assessment, client risk profiling, and regulatory record-keeping remain your responsibility.";

    private final UserRepository users;
    private final UserCompetencePreferencesRepository competencePreferences;
    private final AdvisorAcknowledgementRepository acknowledgements;

    public ProfessionalPreferenceService(UserRepository users,
                                         UserCompetencePreferencesRepository competencePreferences,
                                         AdvisorAcknowledgementRepository acknowledgements) {
        this.users = users;
        this.competencePreferences = competencePreferences;
        this.acknowledgements = acknowledgements;
    }

    @Transactional(readOnly = true)
    public CompetencePreferencesResponse getCompetence(Authentication auth) {
        User user = resolveUser(auth);
        return competencePreferences.findByUser(user)
                .map(p -> new CompetencePreferencesResponse(split(p.getPreferredSectors()), split(p.getCompetenceIndustries()), p.getUpdatedAt()))
                .orElse(new CompetencePreferencesResponse(List.of(), List.of(), null));
    }

    @Transactional
    public CompetencePreferencesResponse updateCompetence(Authentication auth, CompetencePreferencesRequest request) {
        User user = resolveUser(auth);
        UserCompetencePreferences prefs = competencePreferences.findByUser(user).orElseGet(() -> {
            UserCompetencePreferences created = new UserCompetencePreferences();
            created.setUser(user);
            return created;
        });
        prefs.setPreferredSectors(join(request.preferredSectors()));
        prefs.setCompetenceIndustries(join(request.competenceIndustries()));
        UserCompetencePreferences saved = competencePreferences.save(prefs);
        return new CompetencePreferencesResponse(split(saved.getPreferredSectors()), split(saved.getCompetenceIndustries()), saved.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public AdvisorAcknowledgementResponse getAdvisorAcknowledgement(Authentication auth) {
        User user = resolveUser(auth);
        return acknowledgements.findByUser(user)
                .map(a -> new AdvisorAcknowledgementResponse(true, a.getAcknowledgedAt(), a.getSessionKey(), ADVISOR_DISCLAIMER))
                .orElse(new AdvisorAcknowledgementResponse(false, null, null, ADVISOR_DISCLAIMER));
    }

    @Transactional
    public AdvisorAcknowledgementResponse acknowledgeAdvisor(Authentication auth, AdvisorAcknowledgementRequest request) {
        User user = resolveUser(auth);
        AdvisorAcknowledgement acknowledgement = acknowledgements.findByUser(user).orElseGet(() -> {
            AdvisorAcknowledgement created = new AdvisorAcknowledgement();
            created.setUser(user);
            return created;
        });
        acknowledgement.setAcknowledgedAt(LocalDateTime.now());
        acknowledgement.setSessionKey(request.sessionKey());
        AdvisorAcknowledgement saved = acknowledgements.save(acknowledgement);
        return new AdvisorAcknowledgementResponse(true, saved.getAcknowledgedAt(), saved.getSessionKey(), ADVISOR_DISCLAIMER);
    }

    private User resolveUser(Authentication auth) {
        return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return String.join("\n", values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList());
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\R")).filter(v -> !v.isBlank()).toList();
    }
}
