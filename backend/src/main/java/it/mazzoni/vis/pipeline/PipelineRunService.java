package it.mazzoni.vis.pipeline;

import it.mazzoni.vis.admin.SeedResult;
import it.mazzoni.vis.admin.SeedService;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.pipeline.dto.PipelineRunResult;
import it.mazzoni.vis.scoring.ValueScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Profile("!demo")
public class PipelineRunService {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunService.class);

    private final SeedService seedService;
    private final ValueScoreService valueScoreService;

    public PipelineRunService(SeedService seedService, ValueScoreService valueScoreService) {
        this.seedService = seedService;
        this.valueScoreService = valueScoreService;
    }

    public List<PipelineRunResult> run(List<String> tickers) {
        List<SeedResult> seedResults = seedService.seedTickers(tickers);
        List<PipelineRunResult> results = new ArrayList<>();

        for (SeedResult seed : seedResults) {
            if (seed.error() != null) {
                results.add(PipelineRunResult.failed(seed.symbol(), seed.error()));
                continue;
            }
            try {
                ValueScore score = valueScoreService.compute(seed.symbol());
                results.add(PipelineRunResult.success(
                        seed.symbol(), seed.companyName(),
                        seed.compositeFairValue(), seed.marginOfSafety(),
                        score.getTotalScore(), seed.recommendation()));
            } catch (Exception e) {
                log.warn("Score computation failed for {}: {}", seed.symbol(), e.getMessage());
                results.add(PipelineRunResult.failed(seed.symbol(), e.getMessage()));
            }
        }

        results.sort(Comparator.comparing(PipelineRunResult::totalScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return results;
    }
}
