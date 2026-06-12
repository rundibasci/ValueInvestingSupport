package it.mazzoni.vis.demo;

import it.mazzoni.vis.demo.dto.DemoAnalysisResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoAnalysisController {

    private final DemoAnalysisService service;

    public DemoAnalysisController(DemoAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/analyze/{symbol}")
    public DemoAnalysisResponse analyze(@PathVariable String symbol) {
        return service.analyze(symbol);
    }
}
