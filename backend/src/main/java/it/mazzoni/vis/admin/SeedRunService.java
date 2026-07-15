package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.entity.SeedRun;
import it.mazzoni.vis.domain.entity.SeedRunOutcome;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.repository.SeedRunOutcomeRepository;
import it.mazzoni.vis.domain.repository.SeedRunRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
@Profile("!demo")
public class SeedRunService {
    private static final Set<String> ACTIVE = Set.of("QUEUED", "RUNNING");
    private static final Set<String> TERMINAL = Set.of("SUCCESS", "PARTIAL_SUCCESS", "FAILED");

    private final SeedService seedService;
    private final SeedRunRepository runs;
    private final SeedRunOutcomeRepository outcomes;
    private final UserRepository users;
    private final SeedRunProperties properties;
    private final Executor executor;
    private final TransactionTemplate transaction;

    public SeedRunService(SeedService seedService, SeedRunRepository runs, SeedRunOutcomeRepository outcomes,
                          UserRepository users, SeedRunProperties properties,
                          @Qualifier("seedRunExecutor") Executor executor,
                          PlatformTransactionManager transactionManager) {
        this.seedService = seedService;
        this.runs = runs;
        this.outcomes = outcomes;
        this.users = users;
        this.properties = properties;
        this.executor = executor;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public SeedSubmissionResult submit(Authentication auth, List<String> requested, String scope) {
        List<String> symbols = normalize(requested);
        if (symbols.size() <= properties.asyncThreshold()) {
            return SeedSubmissionResult.synchronous(seedService.seedTickers(symbols));
        }
        return SeedSubmissionResult.asynchronous(transaction.execute(status -> submitAsync(auth, symbols, scope)));
    }

    @Transactional
    public synchronized SeedRunAcceptedResponse submitAsync(Authentication auth, List<String> symbols, String scope) {
        User user = user(auth);
        String fingerprint = fingerprint(symbols);
        var active = runs.findFirstByUserAndScopeAndRequestFingerprintAndStatusInOrderByCreatedAtDesc(
                user, scope, fingerprint, ACTIVE);
        if (active.isPresent()) return accepted(active.get(), true);

        LocalDateTime now = LocalDateTime.now();
        SeedRun run = new SeedRun();
        run.setId(UUID.randomUUID());
        run.setUser(user);
        run.setScope(scope);
        run.setRequestFingerprint(fingerprint);
        run.setSymbols(String.join(",", symbols));
        run.setStatus("QUEUED");
        run.setTotalCount(symbols.size());
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runs.saveAndFlush(run);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { schedule(run.getId()); }
        });
        return accepted(run, false);
    }

    private void schedule(UUID id) {
        try { executor.execute(() -> process(id)); }
        catch (RejectedExecutionException exception) {
            transaction.executeWithoutResult(status -> failRun(id, "Seed worker queue is full; retry this run later."));
        }
    }

    public SeedRunStatusResponse status(Authentication auth, UUID id) {
        return response(owned(auth, id));
    }

    public PageResponse<SeedRunOutcomeResponse> outcomes(Authentication auth, UUID id, int page, int size) {
        SeedRun run = owned(auth, id);
        var result = outcomes.findBySeedRunOrderByPosition(run,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))).map(this::response);
        return PageResponse.from(result);
    }

    public SeedRunAcceptedResponse retryFailures(Authentication auth, UUID id) {
        SeedRun previous = owned(auth, id);
        if (!TERMINAL.contains(previous.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only terminal seed runs can be retried");
        }
        List<SeedRunOutcome> completed = outcomes.findBySeedRunOrderByPosition(previous);
        Set<String> retry = new LinkedHashSet<>();
        completed.stream().filter(item -> "FAILED".equals(item.getStatus()))
                .map(SeedRunOutcome::getSymbol).forEach(retry::add);
        Set<String> completedSymbols = new LinkedHashSet<>();
        completed.forEach(item -> completedSymbols.add(item.getSymbol()));
        symbols(previous).stream().filter(symbol -> !completedSymbols.contains(symbol)).forEach(retry::add);
        if (retry.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This seed run has no failed symbols");
        return transaction.execute(status -> submitAsync(auth, List.copyOf(retry), previous.getScope() + "_RETRY"));
    }

    void process(UUID id) {
        Boolean claimed = transaction.execute(status -> claim(id));
        if (!Boolean.TRUE.equals(claimed)) return;
        MDC.put("job.run.id", id.toString());
        MDC.put("job.name", "seed-run");
        try {
            SeedRun run = runs.findById(id).orElse(null);
            if (run == null) return;
            List<String> symbols = symbols(run);
            for (int index = 0; index < symbols.size(); index++) {
                String symbol = symbols.get(index);
                transaction.executeWithoutResult(status -> setCurrent(id, symbol));
                SeedResult result = seedService.seedTickers(List.of(symbol)).getFirst();
                int position = index;
                transaction.executeWithoutResult(status -> record(id, position, result));
            }
            transaction.executeWithoutResult(status -> complete(id));
        } catch (RuntimeException exception) {
            transaction.executeWithoutResult(status -> failRun(id, "Seed execution stopped unexpectedly; retry unfinished symbols."));
        } finally {
            MDC.remove("job.run.id");
            MDC.remove("job.name");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(UUID id) {
        return runs.claimQueued(id, LocalDateTime.now()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setCurrent(UUID id, String symbol) {
        runs.findById(id).ifPresent(run -> {
            run.setCurrentSymbol(symbol);
            run.setUpdatedAt(LocalDateTime.now());
            runs.save(run);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID id, int position, SeedResult result) {
        SeedRun run = runs.findById(id).orElseThrow();
        SeedRunOutcome outcome = new SeedRunOutcome();
        outcome.setId(UUID.randomUUID());
        outcome.setSeedRun(run);
        outcome.setPosition(position);
        outcome.setSymbol(result.symbol());
        String status = result.error() != null || "failed".equals(result.status()) || "unavailable".equals(result.status())
                ? "FAILED" : "seeded_partial".equals(result.status()) ? "PARTIAL" : "SUCCESS";
        outcome.setStatus(status);
        outcome.setSource(shorten(result.source(), 60));
        outcome.setReasonCode(shorten(result.reasonCode(), 80));
        outcome.setReason(shorten(result.reason(), 500));
        outcome.setFallbackReason(shorten(result.fallbackReason(), 1000));
        outcome.setErrorMessage(shorten(result.error(), 500));
        outcome.setCompletedAt(LocalDateTime.now());
        outcomes.save(outcome);
        run.setProcessedCount(run.getProcessedCount() + 1);
        if ("SUCCESS".equals(status)) run.setSucceededCount(run.getSucceededCount() + 1);
        else if ("PARTIAL".equals(status)) run.setPartialCount(run.getPartialCount() + 1);
        else run.setFailedCount(run.getFailedCount() + 1);
        run.setUpdatedAt(LocalDateTime.now());
        runs.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID id) {
        runs.findById(id).ifPresent(run -> {
            if (run.getFailedCount() == run.getTotalCount()) run.setStatus("FAILED");
            else if (run.getFailedCount() > 0 || run.getPartialCount() > 0) run.setStatus("PARTIAL_SUCCESS");
            else run.setStatus("SUCCESS");
            LocalDateTime now = LocalDateTime.now();
            run.setCurrentSymbol(null);
            run.setCompletedAt(now);
            run.setUpdatedAt(now);
            runs.save(run);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRun(UUID id, String reason) {
        runs.findById(id).ifPresent(run -> {
            run.setStatus("FAILED");
            run.setTerminalReason(shorten(reason, 500));
            run.setCurrentSymbol(null);
            run.setCompletedAt(LocalDateTime.now());
            run.setUpdatedAt(LocalDateTime.now());
            runs.save(run);
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverAndClean() {
        runs.findByStatusIn(ACTIVE).forEach(run -> {
            run.setStatus("FAILED");
            run.setTerminalReason("Execution was interrupted by an application restart; retry unfinished symbols.");
            run.setCurrentSymbol(null);
            run.setCompletedAt(LocalDateTime.now());
            run.setUpdatedAt(LocalDateTime.now());
            runs.save(run);
        });
        var expired = runs.findByStatusNotInAndCompletedAtBefore(ACTIVE,
                LocalDateTime.now().minusDays(properties.retentionDays()));
        runs.deleteAll(expired);
    }

    private List<String> normalize(List<String> requested) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (requested != null) requested.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT)).forEach(normalized::add);
        if (normalized.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one ticker is required");
        if (normalized.size() > properties.maxSymbols()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ticker list exceeds the maximum of " + properties.maxSymbols());
        return List.copyOf(normalized);
    }

    private User user(Authentication auth) {
        return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    private SeedRun owned(Authentication auth, UUID id) {
        return runs.findByIdAndUser(id, user(auth))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seed run not found"));
    }
    private List<String> symbols(SeedRun run) {
        if (run.getSymbols().isBlank()) return List.of();
        return List.of(run.getSymbols().split(","));
    }
    private SeedRunAcceptedResponse accepted(SeedRun run, boolean joined) {
        String base = "/api/v1/seed/runs/" + run.getId();
        return new SeedRunAcceptedResponse(run.getId(), run.getStatus(), run.getTotalCount(), base,
                base + "/outcomes", properties.pollingIntervalMs(), joined);
    }
    private SeedRunStatusResponse response(SeedRun run) {
        return new SeedRunStatusResponse(run.getId(), run.getScope(), run.getStatus(), run.getTotalCount(),
                run.getProcessedCount(), run.getSucceededCount(), run.getPartialCount(), run.getFailedCount(),
                run.getCurrentSymbol(), run.getTerminalReason(), run.getCreatedAt(), run.getStartedAt(),
                run.getUpdatedAt(), run.getCompletedAt(), properties.pollingIntervalMs());
    }
    private SeedRunOutcomeResponse response(SeedRunOutcome item) {
        return new SeedRunOutcomeResponse(item.getPosition(), item.getSymbol(), item.getStatus(), item.getSource(),
                item.getReasonCode(), item.getReason(), item.getFallbackReason(), item.getErrorMessage(), item.getCompletedAt());
    }
    private static String fingerprint(List<String> symbols) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", symbols).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static String shorten(String value, int max) {
        if (value == null) return null;
        String safe = value.replaceAll("(?i)(api[_-]?key|password|token)\\s*[=:]\\s*\\S+", "$1=[redacted]");
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
