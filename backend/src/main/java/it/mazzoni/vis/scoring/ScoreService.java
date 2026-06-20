package it.mazzoni.vis.scoring;

import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.scoring.dto.ValueScoreResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreService {

    private final ValueScoreRepository valueScoreRepository;
    private final SecurityRepository securityRepository;
    private final ValueScoreService valueScoreService;

    public ScoreService(ValueScoreRepository valueScoreRepository,
                        SecurityRepository securityRepository,
                        ValueScoreService valueScoreService) {
        this.valueScoreRepository = valueScoreRepository;
        this.securityRepository = securityRepository;
        this.valueScoreService = valueScoreService;
    }

    @Transactional
    public ValueScoreResponse getScore(String symbol) {
        String upper = symbol.toUpperCase();

        ValueScore existing = valueScoreRepository
                .findTopBySecuritySymbolOrderByScoreDateDesc(upper)
                .orElse(null);

        if (existing != null) {
            return ValueScoreResponse.from(existing);
        }

        if (!securityRepository.existsBySymbol(upper)) {
            throw new SymbolNotFoundException(symbol);
        }

        return ValueScoreResponse.from(valueScoreService.compute(upper));
    }
}
