package it.mazzoni.vis.portfolio.analysis;

import it.mazzoni.vis.admin.*;
import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.portfolio.PortfolioAnalyticsService;
import it.mazzoni.vis.security.SecurityReviewService;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;

@Service
@Profile("!demo")
public class PortfolioAnalysisService {
    static final String ANALYSIS_VERSION = "FI3-1";
    private static final Set<String> ACTIVE = Set.of("QUEUED", "RUNNING");
    private static final Set<String> TERMINAL = Set.of("COMPLETE", "PARTIAL", "FAILED");
    private final SeedService seedService;
    private final SecurityReviewService reviews;
    private final PortfolioAnalyticsService analytics;
    private final PortfolioAnalysisRunRepository runs;
    private final PortfolioAnalysisOutcomeRepository outcomes;
    private final PortfolioRepository portfolios;
    private final PortfolioImportRepository imports;
    private final PortfolioImportRowRepository importRows;
    private final HoldingRepository holdings;
    private final PortfolioCashBalanceRepository cash;
    private final UserRepository users;
    private final PortfolioAnalyticsSnapshotRepository snapshots;
    private final PortfolioAnalysisProperties properties;
    private final Executor executor;
    private final TransactionTemplate transaction;

    public PortfolioAnalysisService(SeedService seedService, SecurityReviewService reviews,
            PortfolioAnalyticsService analytics, PortfolioAnalysisRunRepository runs,
            PortfolioAnalysisOutcomeRepository outcomes, PortfolioRepository portfolios,
            PortfolioImportRepository imports, PortfolioImportRowRepository importRows,
            HoldingRepository holdings, PortfolioCashBalanceRepository cash, UserRepository users,
            PortfolioAnalyticsSnapshotRepository snapshots, PortfolioAnalysisProperties properties,
            @Qualifier("portfolioAnalysisExecutor") Executor executor,
            PlatformTransactionManager transactionManager) {
        this.seedService=seedService; this.reviews=reviews; this.analytics=analytics; this.runs=runs;
        this.outcomes=outcomes; this.portfolios=portfolios; this.imports=imports; this.importRows=importRows;
        this.holdings=holdings; this.cash=cash; this.users=users; this.snapshots=snapshots;
        this.properties=properties; this.executor=executor; this.transaction=new TransactionTemplate(transactionManager);
    }

    @Transactional
    public synchronized PortfolioAnalysisAcceptedResponse submit(Authentication auth, UUID portfolioId, UUID importId) {
        User user = user(auth);
        Portfolio portfolio = ownedPortfolio(user, portfolioId);
        PortfolioImport sourceImport = null;
        if (importId != null) {
            sourceImport = imports.findByIdAndUser(importId, user).filter(i -> "COMMITTED".equals(i.getStatus())
                    && i.getPortfolio() != null && portfolioId.equals(i.getPortfolio().getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Committed portfolio import not found"));
        }
        List<String> symbols = sourceImport == null ? symbolsFromHoldings(portfolio) : symbolsFromImport(sourceImport);
        if (symbols.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Portfolio has no resolved non-cash securities to analyze");
        String fingerprint = fingerprint(user.getId()+"\n"+portfolioId+"\n"+(importId == null ? "CURRENT" : importId)+"\n"+ANALYSIS_VERSION+"\n"+String.join("\n", symbols));
        var active = runs.findFirstByUserAndPortfolioAndRequestFingerprintAndStatusInOrderByCreatedAtDesc(user, portfolio, fingerprint, ACTIVE);
        if (active.isPresent()) return accepted(active.get(), true);
        return createRun(user, portfolio, sourceImport, null, symbols, fingerprint);
    }

    private PortfolioAnalysisAcceptedResponse createRun(User user, Portfolio portfolio, PortfolioImport sourceImport,
            PortfolioAnalysisRun retryOf, List<String> symbols, String fingerprint) {
        LocalDateTime now = LocalDateTime.now();
        PortfolioAnalysisRun run = new PortfolioAnalysisRun();
        run.setId(UUID.randomUUID()); run.setUser(user); run.setPortfolio(portfolio); run.setPortfolioImport(sourceImport);
        run.setRetryOf(retryOf); run.setRequestFingerprint(fingerprint); run.setAnalysisVersion(ANALYSIS_VERSION);
        run.setSymbols(String.join(",", symbols)); run.setInputSnapshot(snapshot(portfolio, sourceImport));
        run.setStatus("QUEUED"); run.setPhase("QUEUED"); run.setTotalCount(symbols.size());
        run.setCreatedAt(now); run.setUpdatedAt(now); runs.saveAndFlush(run);
        Map<String, PortfolioImportRow> sourceRows = sourceImport == null ? Map.of() : importRows.findByPortfolioImportOrderByRowNumber(sourceImport).stream()
                .filter(r -> r.getResolvedSecurity()!=null && "SECURITY".equals(r.getClassification()))
                .collect(java.util.stream.Collectors.toMap(r -> r.getResolvedSecurity().getSymbol().toUpperCase(Locale.ROOT), r -> r,
                        (first, next) -> first.getBaseValue()!=null && next.getBaseValue()!=null && next.getBaseValue().compareTo(first.getBaseValue())>0 ? next : first));
        for (int i=0; i<symbols.size(); i++) {
            PortfolioAnalysisOutcome outcome = new PortfolioAnalysisOutcome();
            outcome.setId(UUID.randomUUID()); outcome.setAnalysisRun(run); outcome.setPosition(i);
            outcome.setSymbol(symbols.get(i)); outcome.setStatus("QUEUED"); outcome.setCalculationVersion(ANALYSIS_VERSION);
            PortfolioImportRow sourceRow=sourceRows.get(symbols.get(i)); if(sourceRow!=null){outcome.setSourceLastPrice(sourceRow.getSourceLastPrice());outcome.setSourceBaseValue(sourceRow.getBaseValue());}
            outcomes.save(outcome);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { schedule(run.getId()); }
        });
        return accepted(run, false);
    }

    @Transactional(readOnly = true)
    public PortfolioAnalysisStatusResponse status(Authentication auth, UUID portfolioId, UUID id) { return response(owned(auth, portfolioId, id)); }

    @Transactional(readOnly = true)
    public PortfolioAnalysisStatusResponse latest(Authentication auth, UUID portfolioId) {
        User user=user(auth); Portfolio portfolio=ownedPortfolio(user, portfolioId);
        return runs.findFirstByUserAndPortfolioOrderByCreatedAtDesc(user, portfolio).map(this::response)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No portfolio analysis run found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<PortfolioAnalysisOutcomeResponse> outcomes(Authentication auth, UUID portfolioId, UUID id, int page, int size) {
        PortfolioAnalysisRun run=owned(auth, portfolioId, id);
        return PageResponse.from(outcomes.findByAnalysisRunOrderByPosition(run,
                PageRequest.of(Math.max(page,0), Math.min(Math.max(size,1),100))).map(this::response));
    }

    @Transactional
    public PortfolioAnalysisAcceptedResponse retry(Authentication auth, UUID portfolioId, UUID id) {
        PortfolioAnalysisRun previous=owned(auth, portfolioId, id);
        if (!TERMINAL.contains(previous.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only terminal analysis runs can be retried");
        List<String> retrySymbols=outcomes.findByAnalysisRunOrderByPosition(previous).stream()
                .filter(o -> "FAILED".equals(o.getStatus()) || "PARTIAL".equals(o.getStatus()))
                .map(PortfolioAnalysisOutcome::getSymbol).distinct().toList();
        if (retrySymbols.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This analysis run has no failed or partial symbols");
        String fingerprint=fingerprint(previous.getRequestFingerprint()+"\nRETRY\n"+String.join("\n", retrySymbols));
        return createRun(previous.getUser(), previous.getPortfolio(), previous.getPortfolioImport(), previous, retrySymbols, fingerprint);
    }

    private void schedule(UUID id) {
        try { executor.execute(() -> process(id)); }
        catch (RejectedExecutionException ex) { transaction.executeWithoutResult(s -> fail(id, "Portfolio analysis worker queue is full; retry later.")); }
    }

    void process(UUID id) {
        if (!Boolean.TRUE.equals(transaction.execute(s -> runs.claimQueued(id, LocalDateTime.now()) == 1))) return;
        MDC.put("analysis.run.id", id.toString());
        try {
            PortfolioAnalysisRun run=runs.findById(id).orElseThrow();
            Authentication auth=new UsernamePasswordAuthenticationToken(run.getUser().getEmail(), "analysis-worker");
            for (PortfolioAnalysisOutcome queued : outcomes.findByAnalysisRunOrderByPosition(run)) {
                transaction.executeWithoutResult(s -> startOutcome(id, queued.getSymbol()));
                SeedResult seeded;
                try { seeded=seedService.seedTickers(List.of(queued.getSymbol())).getFirst(); }
                catch (RuntimeException ex) { transaction.executeWithoutResult(s -> finishOutcome(id, queued.getSymbol(), null, ex)); continue; }
                transaction.executeWithoutResult(s -> calculating(id, queued.getSymbol()));
                RuntimeException reviewError=null;
                try { reviews.getReview(queued.getSymbol()); } catch (RuntimeException ex) { reviewError=ex; }
                RuntimeException finalReviewError=reviewError;
                transaction.executeWithoutResult(s -> finishOutcome(id, queued.getSymbol(), seeded, finalReviewError));
            }
            transaction.executeWithoutResult(s -> phase(id, "PORTFOLIO_ANALYTICS"));
            UUID analyticsId=null; RuntimeException analyticsError=null;
            try { analyticsId=analytics.analyze(auth, run.getPortfolio().getId()).snapshotId(); }
            catch (RuntimeException ex) { analyticsError=ex; }
            UUID finalAnalyticsId=analyticsId; RuntimeException finalAnalyticsError=analyticsError;
            transaction.executeWithoutResult(s -> complete(id, finalAnalyticsId, finalAnalyticsError));
        } catch (RuntimeException ex) { transaction.executeWithoutResult(s -> fail(id, "Analysis execution stopped unexpectedly; retry unfinished symbols.")); }
        finally { MDC.remove("analysis.run.id"); }
    }

    private void startOutcome(UUID id, String symbol) {
        PortfolioAnalysisRun run=runs.findById(id).orElseThrow(); run.setCurrentSymbol(symbol); run.setPhase("SEEDING"); run.setUpdatedAt(LocalDateTime.now()); runs.save(run);
        outcomes.findByAnalysisRunAndSymbol(run,symbol).ifPresent(o->{o.setStatus("SEEDING");o.setStartedAt(LocalDateTime.now());outcomes.save(o);});
    }
    private void calculating(UUID id,String symbol){PortfolioAnalysisRun run=runs.findById(id).orElseThrow();run.setPhase("CALCULATING");run.setUpdatedAt(LocalDateTime.now());runs.save(run);outcomes.findByAnalysisRunAndSymbol(run,symbol).ifPresent(o->{o.setStatus("CALCULATING");outcomes.save(o);});}
    private void finishOutcome(UUID id,String symbol,SeedResult result,RuntimeException error){
        PortfolioAnalysisRun run=runs.findById(id).orElseThrow(); PortfolioAnalysisOutcome o=outcomes.findByAnalysisRunAndSymbol(run,symbol).orElseThrow();
        String status;
        if(result==null || result.error()!=null || "failed".equals(result.status()) || "unavailable".equals(result.status())) status="FAILED";
        else if(error!=null || "seeded_partial".equals(result.status())) status="PARTIAL"; else status="COMPLETE";
        o.setStatus(status); o.setSource(result==null?null:shorten(result.source(),60)); o.setRefreshedAt(result==null?null:result.refreshedAt());
        o.setRefreshedPrice(result==null?null:result.currentPrice());
        if(o.getSourceLastPrice()!=null && o.getSourceLastPrice().signum()!=0 && o.getRefreshedPrice()!=null) o.setPriceVariancePercent(o.getRefreshedPrice().subtract(o.getSourceLastPrice()).divide(o.getSourceLastPrice(),8,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(4,RoundingMode.HALF_UP));
        o.setReasonCode(result==null?"analysis_failure":shorten(result.reasonCode(),80));
        o.setReason(result==null?"Security ingestion failed":shorten(result.reason(),500)); o.setFallbackReason(result==null?null:shorten(result.fallbackReason(),1000));
        o.setErrorMessage(error!=null?safe(error):result==null?"Security ingestion failed":shorten(result.error(),500));
        if(!"FAILED".equals(status)) o.setReviewPath("/securities/"+symbol+"/review"); o.setCompletedAt(LocalDateTime.now()); outcomes.save(o);
        run.setProcessedCount(run.getProcessedCount()+1); if("COMPLETE".equals(status))run.setSucceededCount(run.getSucceededCount()+1);else if("PARTIAL".equals(status))run.setPartialCount(run.getPartialCount()+1);else run.setFailedCount(run.getFailedCount()+1);
        run.setUpdatedAt(LocalDateTime.now()); runs.save(run);
    }
    private void phase(UUID id,String value){runs.findById(id).ifPresent(r->{r.setPhase(value);r.setCurrentSymbol(null);r.setUpdatedAt(LocalDateTime.now());runs.save(r);});}
    private void complete(UUID id,UUID snapshotId,RuntimeException analyticsError){runs.findById(id).ifPresent(r->{
        if(snapshotId!=null)snapshots.findById(snapshotId).ifPresent(r::setAnalyticsSnapshot);
        if(r.getFailedCount()==r.getTotalCount() || snapshotId==null)r.setStatus("FAILED"); else if(r.getFailedCount()>0||r.getPartialCount()>0||analyticsError!=null)r.setStatus("PARTIAL"); else r.setStatus("COMPLETE");
        r.setPhase("COMPLETE"); if(analyticsError!=null)r.setTerminalReason("Portfolio analytics unavailable: "+safe(analyticsError)); LocalDateTime now=LocalDateTime.now();r.setCurrentSymbol(null);r.setCompletedAt(now);r.setUpdatedAt(now);runs.save(r);});}
    private void fail(UUID id,String reason){runs.findById(id).ifPresent(r->{r.setStatus("FAILED");r.setPhase("COMPLETE");r.setTerminalReason(shorten(reason,500));r.setCurrentSymbol(null);r.setCompletedAt(LocalDateTime.now());r.setUpdatedAt(LocalDateTime.now());runs.save(r);});}

    @EventListener(ApplicationReadyEvent.class) @Transactional
    public void recover(){runs.findByStatusIn(ACTIVE).forEach(r->{r.setStatus("FAILED");r.setPhase("COMPLETE");r.setTerminalReason("Execution was interrupted by an application restart; retry failed or partial symbols.");r.setCurrentSymbol(null);r.setCompletedAt(LocalDateTime.now());r.setUpdatedAt(LocalDateTime.now());runs.save(r);});}

    private User user(Authentication auth){return users.findByEmail(auth.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));}
    private Portfolio ownedPortfolio(User user,UUID id){return portfolios.findByIdAndUser(id,user).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Portfolio not found"));}
    private PortfolioAnalysisRun owned(Authentication auth,UUID portfolioId,UUID id){User user=user(auth);Portfolio portfolio=ownedPortfolio(user,portfolioId);return runs.findByIdAndUserAndPortfolio(id,user,portfolio).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Portfolio analysis run not found"));}
    private List<String> symbolsFromHoldings(Portfolio p){return holdings.findByPortfolio(p).stream().map(Holding::getSymbol).filter(Objects::nonNull).map(s->s.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();}
    private List<String> symbolsFromImport(PortfolioImport i){return importRows.findByPortfolioImportOrderByRowNumber(i).stream().filter(r->"SECURITY".equals(r.getClassification())&&r.getResolvedSecurity()!=null&&r.getCommittedOutcome()!=null&&!"SKIPPED".equals(r.getCommittedOutcome())).map(r->r.getResolvedSecurity().getSymbol().toUpperCase(Locale.ROOT)).distinct().sorted().toList();}
    private String snapshot(Portfolio p,PortfolioImport i){String holdingText=holdings.findByPortfolio(p).stream().map(h->h.getSymbol()+":"+h.getQuantity()).sorted().reduce((a,b)->a+","+b).orElse("");String cashText=cash.findByPortfolio(p).stream().map(c->c.getCurrency()+":"+c.getNativeAmount()+":"+c.getBaseAmount()).sorted().reduce((a,b)->a+","+b).orElse("");return "import="+(i==null?"current":i.getId())+";holdings="+holdingText+";cash="+cashText;}
    private PortfolioAnalysisAcceptedResponse accepted(PortfolioAnalysisRun r,boolean joined){String base="/api/v1/portfolios/"+r.getPortfolio().getId()+"/analysis-runs/"+r.getId();return new PortfolioAnalysisAcceptedResponse(r.getId(),r.getStatus(),r.getTotalCount(),base,base+"/outcomes",properties.pollingIntervalMs(),joined);}
    private PortfolioAnalysisStatusResponse response(PortfolioAnalysisRun r){return new PortfolioAnalysisStatusResponse(r.getId(),r.getPortfolio().getId(),r.getPortfolioImport()==null?null:r.getPortfolioImport().getId(),r.getStatus(),r.getPhase(),r.getTotalCount(),r.getProcessedCount(),r.getSucceededCount(),r.getPartialCount(),r.getFailedCount(),r.getCurrentSymbol(),r.getTerminalReason(),r.getAnalyticsSnapshot()==null?null:r.getAnalyticsSnapshot().getId(),r.getAnalysisVersion(),r.getCreatedAt(),r.getStartedAt(),r.getUpdatedAt(),r.getCompletedAt(),properties.pollingIntervalMs());}
    private PortfolioAnalysisOutcomeResponse response(PortfolioAnalysisOutcome o){return new PortfolioAnalysisOutcomeResponse(o.getPosition(),o.getSymbol(),o.getStatus(),o.getSource(),o.getRefreshedAt(),o.getSourceLastPrice(),o.getSourceBaseValue(),o.getRefreshedPrice(),o.getPriceVariancePercent(),o.getReasonCode(),o.getReason(),o.getFallbackReason(),o.getErrorMessage(),o.getReviewPath(),o.getCalculationVersion(),o.getStartedAt(),o.getCompletedAt());}
    private static String fingerprint(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String safe(Throwable e){return shorten(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(),500);}
    private static String shorten(String v,int max){if(v==null)return null;String safe=v.replaceAll("(?i)(api[_-]?key|password|token)\\s*[=:]\\s*\\S+","$1=[redacted]");return safe.length()<=max?safe:safe.substring(0,max);}
}
