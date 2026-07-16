package it.mazzoni.vis.portfolio.analysis;

import it.mazzoni.vis.admin.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/analysis-runs")
@Profile("!demo")
public class PortfolioAnalysisController {
    private final PortfolioAnalysisService service;
    public PortfolioAnalysisController(PortfolioAnalysisService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public PortfolioAnalysisAcceptedResponse start(Authentication auth,@PathVariable UUID portfolioId,@RequestBody(required=false) StartPortfolioAnalysisRequest request){return service.submit(auth,portfolioId,request==null?null:request.importId());}
    @GetMapping("/latest") public PortfolioAnalysisStatusResponse latest(Authentication auth,@PathVariable UUID portfolioId){return service.latest(auth,portfolioId);}
    @GetMapping("/{id}") public PortfolioAnalysisStatusResponse status(Authentication auth,@PathVariable UUID portfolioId,@PathVariable UUID id){return service.status(auth,portfolioId,id);}
    @GetMapping("/{id}/outcomes") public PageResponse<PortfolioAnalysisOutcomeResponse> outcomes(Authentication auth,@PathVariable UUID portfolioId,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return service.outcomes(auth,portfolioId,id,page,size);}
    @PostMapping("/{id}/retry-failures") @ResponseStatus(HttpStatus.ACCEPTED) public PortfolioAnalysisAcceptedResponse retry(Authentication auth,@PathVariable UUID portfolioId,@PathVariable UUID id){return service.retry(auth,portfolioId,id);}
}
