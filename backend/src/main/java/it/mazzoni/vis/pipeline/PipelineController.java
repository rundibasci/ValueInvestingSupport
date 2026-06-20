package it.mazzoni.vis.pipeline;

import it.mazzoni.vis.pipeline.dto.PipelineRunRequest;
import it.mazzoni.vis.pipeline.dto.PipelineRunResult;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Profile("!demo")
public class PipelineController {

    private final PipelineRunService pipelineRunService;

    public PipelineController(PipelineRunService pipelineRunService) {
        this.pipelineRunService = pipelineRunService;
    }

    @PostMapping("/pipeline-run")
    public ResponseEntity<List<PipelineRunResult>> run(@RequestBody PipelineRunRequest request) {
        return ResponseEntity.ok(pipelineRunService.run(request.tickers()));
    }
}
