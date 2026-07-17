package it.mazzoni.vis.portfolio.importing;

import it.mazzoni.vis.admin.SecurityIsinService;
import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.portfolio.importing.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PortfolioImportService {
    private static final Set<String> ALLOWED_TYPES = Set.of("text/csv", "application/csv", "application/vnd.ms-excel", "text/plain");
    private final PortfolioImportProperties properties;
    private final PortfolioCsvParser parser;
    private final UserRepository users;
    private final PortfolioRepository portfolios;
    private final PortfolioImportRepository imports;
    private final PortfolioImportRowRepository rows;
    private final SecurityRepository securities;
    private final HoldingRepository holdings;
    private final PortfolioCashBalanceRepository cashBalances;
    private final SecurityIsinService securityIsinService;

    public PortfolioImportService(PortfolioImportProperties properties, PortfolioCsvParser parser,
            UserRepository users, PortfolioRepository portfolios, PortfolioImportRepository imports,
            PortfolioImportRowRepository rows, SecurityRepository securities, HoldingRepository holdings,
            PortfolioCashBalanceRepository cashBalances, SecurityIsinService securityIsinService) {
        this.properties = properties; this.parser = parser; this.users = users; this.portfolios = portfolios;
        this.imports = imports; this.rows = rows; this.securities = securities; this.holdings = holdings;
        this.cashBalances = cashBalances; this.securityIsinService = securityIsinService;
    }

    @Transactional
    public PortfolioImportPreviewResponse preview(Authentication auth, MultipartFile file, UUID portfolioId,
                                                   String baseCurrency, ImportMode mode) {
        User user = user(auth);
        validateUpload(file);
        Portfolio portfolio = portfolioId == null ? null : ownedPortfolio(user, portfolioId);
        String normalizedBase = currency(baseCurrency == null ? properties.defaultBaseCurrency() : baseCurrency);
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException ex) { throw badRequest("Unable to read CSV upload"); }
        List<ParsedPortfolioRow> parsed = parser.parse(bytes, properties.maxRows());
        LocalDateTime now = LocalDateTime.now();
        PortfolioImport entity = new PortfolioImport();
        entity.setUser(user); entity.setPortfolio(portfolio);
        entity.setOriginalFilename(filename(file.getOriginalFilename()));
        entity.setChecksum(sha256(bytes)); entity.setMode((mode == null ? ImportMode.MERGE : mode).name());
        entity.setBaseCurrency(normalizedBase); entity.setStatus("PREVIEW");
        entity.setCreatedAt(now); entity.setExpiresAt(now.plusHours(properties.previewRetentionHours()));
        // Batch-resolve all candidate ISINs once instead of one findByIsin query per row.
        List<String> candidateIsins = parsed.stream()
                .filter(r -> "SECURITY".equals(r.classification()) && r.status() == ImportRowStatus.NEEDS_MAPPING && r.isin() != null)
                .map(ParsedPortfolioRow::isin)
                .distinct()
                .toList();
        Map<String, Security> resolvedByIsin = securities.findByIsinIn(candidateIsins).stream()
                .collect(Collectors.toMap(Security::getIsin, Function.identity()));
        int warnings = 0, errors = 0, ready = 0;
        Set<String> seen = new HashSet<>();
        for (ParsedPortfolioRow parsedRow : parsed) {
            PortfolioImportRow row = toEntity(parsedRow);
            if ("SECURITY".equals(row.getClassification()) && row.getStatus().equals(ImportRowStatus.NEEDS_MAPPING.name())) {
                Security security = resolvedByIsin.get(row.getIsin());
                if (security != null) {
                    row.setResolvedSecurity(security);
                    row.setStatus(row.getWarningText() == null ? ImportRowStatus.READY.name() : ImportRowStatus.WARNING.name());
                }
            }
            String duplicateKey = "CASH".equals(row.getClassification()) ? "CASH:" + row.getNativeCurrency()
                    : row.getResolvedSecurity() == null ? null : "SECURITY:" + row.getResolvedSecurity().getId();
            if (duplicateKey != null && !seen.add(duplicateKey)) {
                row.setWarningText(append(row.getWarningText(), "Duplicate position will be consolidated"));
                if (!ImportRowStatus.INVALID.name().equals(row.getStatus())) row.setStatus(ImportRowStatus.WARNING.name());
            }
            if (ImportRowStatus.INVALID.name().equals(row.getStatus())) errors++;
            else if (row.getWarningText() != null || ImportRowStatus.NEEDS_MAPPING.name().equals(row.getStatus())) warnings++;
            if (isCommitReady(row)) ready++;
            entity.addRow(row);
        }
        entity.setSourceRowCount(parsed.size()); entity.setReadyRowCount(ready);
        entity.setWarningCount(warnings); entity.setErrorCount(errors);
        return previewResponse(imports.save(entity));
    }

    @Transactional
    public PortfolioImportCommitResponse commit(Authentication auth, UUID importId, PortfolioImportCommitRequest request) {
        User user = user(auth);
        PortfolioImport portfolioImport = imports.findByIdAndUser(importId, user)
                .orElseThrow(() -> notFound("Portfolio import not found"));
        if (portfolioImport.getExpiresAt().isBefore(LocalDateTime.now())) throw conflict("Portfolio import preview has expired");
        if ("COMMITTED".equals(portfolioImport.getStatus())) return commitResponse(portfolioImport);
        Portfolio portfolio = portfolioImport.getPortfolio();
        if (portfolio == null) {
            if (request == null || request.newPortfolioName() == null || request.newPortfolioName().isBlank())
                throw badRequest("New portfolio name is required");
            portfolio = new Portfolio(); portfolio.setUser(user); portfolio.setName(request.newPortfolioName().strip());
            portfolio.setDescription("Created from portfolio CSV import");
            portfolio = portfolios.save(portfolio); portfolioImport.setPortfolio(portfolio);
        } else {
            portfolio = ownedPortfolio(user, portfolio.getId());
        }
        PortfolioImportCommitRequest req = request == null
                ? new PortfolioImportCommitRequest(null, false, Set.of(), List.of()) : request;
        if (ImportMode.REPLACE.name().equals(portfolioImport.getMode()) && !req.replaceConfirmed())
            throw badRequest("REPLACE requires explicit confirmation");
        Map<UUID, PortfolioImportRow> byId = portfolioImport.getRows().stream()
                .collect(Collectors.toMap(PortfolioImportRow::getId, Function.identity()));
        applyMappings(user, req.mappings(), byId);
        Set<UUID> skipped = req.skippedRowIds();
        if (!byId.keySet().containsAll(skipped)) throw badRequest("Skipped row does not belong to this import");
        for (PortfolioImportRow row : portfolioImport.getRows()) {
            if (!skipped.contains(row.getId()) && !isCommitReady(row))
                throw conflict("Resolve or skip row " + row.getRowNumber() + " before commit");
        }
        if (ImportMode.REPLACE.name().equals(portfolioImport.getMode())) {
            holdings.deleteByPortfolio(portfolio); cashBalances.deleteByPortfolio(portfolio);
        }
        Map<String, List<PortfolioImportRow>> securityGroups = portfolioImport.getRows().stream()
                .filter(r -> !skipped.contains(r.getId()) && "SECURITY".equals(r.getClassification()))
                .collect(Collectors.groupingBy(r -> r.getResolvedSecurity().getSymbol(), LinkedHashMap::new, Collectors.toList()));
        for (var entry : securityGroups.entrySet()) synchronizeHolding(portfolio, entry.getKey(), entry.getValue(), portfolioImport.getBaseCurrency());
        Map<String, List<PortfolioImportRow>> cashGroups = portfolioImport.getRows().stream()
                .filter(r -> !skipped.contains(r.getId()) && "CASH".equals(r.getClassification()))
                .collect(Collectors.groupingBy(PortfolioImportRow::getNativeCurrency, LinkedHashMap::new, Collectors.toList()));
        for (var entry : cashGroups.entrySet()) synchronizeCash(portfolio, entry.getKey(), entry.getValue(), portfolioImport);
        for (PortfolioImportRow row : portfolioImport.getRows())
            row.setCommittedOutcome(skipped.contains(row.getId()) ? "SKIPPED" : "COMMITTED");
        portfolioImport.setStatus("COMMITTED"); portfolioImport.setCommittedAt(LocalDateTime.now());
        imports.save(portfolioImport);
        return commitResponse(portfolioImport);
    }

    @Transactional(readOnly = true)
    public PortfolioImportHistoryResponse history(Authentication auth, UUID portfolioId, String status, int page, int size) {
        User user = user(auth);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Portfolio portfolio = portfolioId == null ? null : ownedPortfolio(user, portfolioId);
        String normalizedStatus = status == null || status.isBlank() ? null : status.strip().toUpperCase(Locale.ROOT);
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        Page<PortfolioImport> result;
        if (portfolio != null && normalizedStatus != null)
            result = imports.findByUserAndPortfolioAndStatusOrderByCreatedAtDesc(user, portfolio, normalizedStatus, pageable);
        else if (portfolio != null)
            result = imports.findByUserAndPortfolioOrderByCreatedAtDesc(user, portfolio, pageable);
        else if (normalizedStatus != null)
            result = imports.findByUserAndStatusOrderByCreatedAtDesc(user, normalizedStatus, pageable);
        else result = imports.findByUserOrderByCreatedAtDesc(user, pageable);
        return new PortfolioImportHistoryResponse(result.getContent().stream().map(this::historyItem).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PortfolioImportPreviewResponse detail(Authentication auth, UUID importId) {
        PortfolioImport portfolioImport = ownedImport(auth, importId);
        return previewResponse(portfolioImport);
    }

    @Transactional(readOnly = true)
    public byte[] reconciliationReport(Authentication auth, UUID importId) {
        PortfolioImport portfolioImport = ownedImport(auth, importId);
        return PortfolioImportReportWriter.write(portfolioImport);
    }

    @Scheduled(cron = "${app.portfolio-import.cleanup-cron:0 15 1 * * *}")
    @Transactional
    public void cleanExpiredPreviews() {
        imports.deleteByStatusAndExpiresAtBefore("PREVIEW", LocalDateTime.now());
    }

    private void applyMappings(User user, List<IsinMappingRequest> mappings, Map<UUID, PortfolioImportRow> rowsById) {
        for (IsinMappingRequest mapping : mappings) {
            PortfolioImportRow row = rowsById.get(mapping.rowId());
            if (row == null || !"SECURITY".equals(row.getClassification()) || row.getIsin() == null)
                throw badRequest("Invalid import row mapping");
            Security target = securities.findById(mapping.securityId()).orElseThrow(() -> badRequest("Mapped security not found"));
            securities.findByIsin(row.getIsin()).ifPresent(existing -> {
                if (!existing.getId().equals(target.getId())) throw conflict("ISIN is already assigned to another security");
            });
            boolean isNewBinding = target.getIsin() == null;
            if (isNewBinding && user.getRole() != UserRole.ADMIN) {
                // Security rows are shared, platform-wide reference data. Only an admin may create a brand-new
                // ISIN<->Security binding; a regular user's mapping is flagged for admin review instead of applied.
                row.setStatus(ImportRowStatus.NEEDS_ADMIN_MAPPING.name());
                row.setWarningText(append(row.getWarningText(), "Binding a new ISIN to this security requires admin approval"));
                continue;
            }
            Security saved = securityIsinService.assignIsin(target.getId(), row.getIsin());
            row.setResolvedSecurity(saved);
            row.setStatus(row.getWarningText() == null ? ImportRowStatus.READY.name() : ImportRowStatus.WARNING.name());
        }
    }

    private void synchronizeHolding(Portfolio portfolio, String symbol, List<PortfolioImportRow> source, String baseCurrency) {
        BigDecimal quantity = source.stream().map(PortfolioImportRow::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Holding> existing = holdings.findByPortfolioAndSymbol(portfolio, symbol);
        boolean isNewHolding = existing.isEmpty();
        Holding holding = isNewHolding ? new Holding() : existing.getFirst();
        if (existing.size() > 1) holdings.deleteAll(existing.subList(1, existing.size()));
        holding.setPortfolio(portfolio); holding.setSymbol(symbol); holding.setQuantity(quantity);
        // Only a brand-new holding gets a null cost basis (the supplied CSV format has no cost-basis column).
        // A pre-existing holding's manually-entered cost basis must survive a MERGE reimport.
        if (isNewHolding) holding.setAverageCostBasis(null);
        holding.setCurrency(source.getFirst().getNativeCurrency() == null ? baseCurrency : source.getFirst().getNativeCurrency());
        holdings.save(holding);
    }

    private void synchronizeCash(Portfolio portfolio, String currency, List<PortfolioImportRow> source, PortfolioImport portfolioImport) {
        PortfolioCashBalance cash = cashBalances.findByPortfolioAndCurrency(portfolio, currency).orElseGet(PortfolioCashBalance::new);
        cash.setPortfolio(portfolio); cash.setCurrency(currency); cash.setBaseCurrency(portfolioImport.getBaseCurrency());
        cash.setNativeAmount(source.stream().map(PortfolioImportRow::getNativeValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        cash.setBaseAmount(source.stream().map(PortfolioImportRow::getBaseValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        cash.setSourceImportId(portfolioImport.getId()); cashBalances.save(cash);
    }

    private PortfolioImportRow toEntity(ParsedPortfolioRow source) {
        PortfolioImportRow row = new PortfolioImportRow(); row.setRowNumber(source.rowNumber());
        row.setProductName(source.productName()); row.setSourceCode(source.sourceCode()); row.setIsin(source.isin());
        row.setQuantity(source.quantity()); row.setSourceLastPrice(source.sourceLastPrice());
        row.setNativeCurrency(source.currency()); row.setNativeValue(source.nativeValue()); row.setBaseValue(source.baseValue());
        row.setClassification(source.classification()); row.setStatus(source.status().name());
        row.setWarningText(source.warning()); row.setErrorText(source.error()); return row;
    }

    private PortfolioImportPreviewResponse previewResponse(PortfolioImport i) {
        Totals totals = totals(i.getRows());
        return new PortfolioImportPreviewResponse(i.getId(), i.getPortfolio() == null ? null : i.getPortfolio().getId(),
                i.getOriginalFilename(), i.getChecksum(), "BROKER_IT_V1", i.getMode(), i.getBaseCurrency(), i.getStatus(),
                i.getSourceRowCount(), i.getReadyRowCount(), i.getWarningCount(), i.getErrorCount(), totals.base,
                totals.nativeTotals, i.getCreatedAt(), i.getExpiresAt(), i.getRows().stream().map(this::rowResponse).toList());
    }
    private PortfolioImportCommitResponse commitResponse(PortfolioImport i) {
        Totals totals = totals(i.getRows().stream().filter(r -> "COMMITTED".equals(r.getCommittedOutcome())).toList());
        int holdingRows = (int) i.getRows().stream().filter(r -> "COMMITTED".equals(r.getCommittedOutcome()) && "SECURITY".equals(r.getClassification())).count();
        int cashRows = (int) i.getRows().stream().filter(r -> "COMMITTED".equals(r.getCommittedOutcome()) && "CASH".equals(r.getClassification())).count();
        int skipped = (int) i.getRows().stream().filter(r -> "SKIPPED".equals(r.getCommittedOutcome())).count();
        return new PortfolioImportCommitResponse(i.getId(), i.getPortfolio().getId(), i.getStatus(), i.getMode(),
                holdingRows, cashRows, skipped, totals.base, totals.nativeTotals, i.getCommittedAt(),
                i.getRows().stream().map(this::rowResponse).toList());
    }
    private PortfolioImportRowResponse rowResponse(PortfolioImportRow r) {
        Security s = r.getResolvedSecurity();
        return new PortfolioImportRowResponse(r.getId(), r.getRowNumber(), r.getProductName(), r.getSourceCode(), r.getIsin(),
                r.getQuantity(), r.getSourceLastPrice(), r.getNativeCurrency(), r.getNativeValue(), r.getBaseValue(),
                s == null ? null : s.getId(), s == null ? null : s.getSymbol(), r.getClassification(), r.getStatus(),
                r.getWarningText(), r.getErrorText(), r.getCommittedOutcome());
    }
    private PortfolioImportHistoryItem historyItem(PortfolioImport i) {
        Portfolio p = i.getPortfolio();
        return new PortfolioImportHistoryItem(i.getId(), p == null ? null : p.getId(), p == null ? null : p.getName(),
                i.getOriginalFilename(), i.getChecksum(), i.getMode(), i.getBaseCurrency(), i.getStatus(),
                i.getSourceRowCount(), i.getReadyRowCount(), i.getWarningCount(), i.getErrorCount(),
                i.getCreatedAt(), i.getExpiresAt(), i.getCommittedAt());
    }
    private Totals totals(List<PortfolioImportRow> source) {
        BigDecimal base = source.stream().map(PortfolioImportRow::getBaseValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> nativeTotals = source.stream().filter(r -> r.getNativeCurrency() != null && r.getNativeValue() != null)
                .collect(Collectors.groupingBy(PortfolioImportRow::getNativeCurrency, LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, PortfolioImportRow::getNativeValue, BigDecimal::add)));
        return new Totals(base, nativeTotals);
    }
    private boolean isCommitReady(PortfolioImportRow r) {
        return "CASH".equals(r.getClassification()) && !ImportRowStatus.INVALID.name().equals(r.getStatus())
                || "SECURITY".equals(r.getClassification()) && r.getResolvedSecurity() != null
                && !ImportRowStatus.INVALID.name().equals(r.getStatus());
    }
    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("CSV file is required");
        if (file.getSize() > properties.maxUploadBytes()) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV upload is too large");
        String type = file.getContentType();
        if (type != null && !type.isBlank() && !ALLOWED_TYPES.contains(type.toLowerCase(Locale.ROOT))) throw badRequest("Unsupported CSV content type");
    }
    private User user(Authentication auth) { return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)); }
    private PortfolioImport ownedImport(Authentication auth, UUID importId) {
        User user = user(auth);
        return imports.findByIdAndUser(importId, user).orElseThrow(() -> notFound("Portfolio import not found"));
    }
    private Portfolio ownedPortfolio(User user, UUID id) { return portfolios.findByIdAndUser(id, user).orElseThrow(() -> notFound("Portfolio not found")); }
    private String currency(String value) { String c = value.strip().toUpperCase(Locale.ROOT); try { Currency.getInstance(c); return c; } catch (Exception ex) { throw badRequest("Invalid base currency"); } }
    private String filename(String value) { if (value == null || value.isBlank()) return "portfolio.csv"; String s = value.replace('\\', '/'); s = s.substring(s.lastIndexOf('/') + 1); return s.length() > 255 ? s.substring(0, 255) : s; }
    private String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }
    private String append(String current, String value) { return current == null ? value : current + "; " + value; }
    private ResponseStatusException badRequest(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private ResponseStatusException notFound(String reason) { return new ResponseStatusException(HttpStatus.NOT_FOUND, reason); }
    private record Totals(BigDecimal base, Map<String, BigDecimal> nativeTotals) { }
}
