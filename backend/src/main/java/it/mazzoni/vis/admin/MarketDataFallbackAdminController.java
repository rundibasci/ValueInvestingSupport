package it.mazzoni.vis.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/market-data-fallbacks")
public class MarketDataFallbackAdminController {

    private final MarketDataFallbackAdminService service;

    public MarketDataFallbackAdminController(MarketDataFallbackAdminService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<MarketDataFallbackEventResponse> events(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String triggerReason,
            @RequestParam(required = false) UUID jobRunId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.events(symbol, operation, eventType, outcome, triggerReason, jobRunId, from, to, page, size);
    }

    @GetMapping("/summary")
    MarketDataFallbackSummaryResponse summary(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String triggerReason,
            @RequestParam(required = false) UUID jobRunId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return service.summary(symbol, operation, eventType, outcome, triggerReason, jobRunId, from, to);
    }
}
