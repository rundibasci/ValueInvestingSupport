package it.mazzoni.vis.portfolio.importing;

import it.mazzoni.vis.admin.SecurityIsinService;
import it.mazzoni.vis.domain.entity.Holding;
import it.mazzoni.vis.domain.entity.Portfolio;
import it.mazzoni.vis.domain.entity.PortfolioImport;
import it.mazzoni.vis.domain.entity.PortfolioImportRow;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.HoldingRepository;
import it.mazzoni.vis.domain.repository.PortfolioCashBalanceRepository;
import it.mazzoni.vis.domain.repository.PortfolioImportRepository;
import it.mazzoni.vis.domain.repository.PortfolioImportRowRepository;
import it.mazzoni.vis.domain.repository.PortfolioRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.portfolio.importing.dto.IsinMappingRequest;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportCommitRequest;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportCommitResponse;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportPreviewResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioImportServiceTest {

    @Mock PortfolioCsvParser parser;
    @Mock UserRepository users;
    @Mock PortfolioRepository portfolios;
    @Mock PortfolioImportRepository imports;
    @Mock PortfolioImportRowRepository rows;
    @Mock SecurityRepository securities;
    @Mock HoldingRepository holdings;
    @Mock PortfolioCashBalanceRepository cashBalances;
    @Mock SecurityIsinService securityIsinService;
    @Mock Authentication authentication;

    private PortfolioImportService service() {
        return new PortfolioImportService(
                new PortfolioImportProperties(1_048_576, 1_000, 24, "EUR"),
                parser, users, portfolios, imports, rows, securities, holdings, cashBalances, securityIsinService);
    }

    private User user(UserRole role) {
        User user = new User();
        user.setRole(role);
        when(authentication.getName()).thenReturn("user@example.com");
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        return user;
    }

    private Portfolio portfolio(User user) {
        Portfolio portfolio = new Portfolio();
        portfolio.setUser(user);
        ReflectionTestUtils.setField(portfolio, "id", UUID.randomUUID());
        return portfolio;
    }

    private PortfolioImport pendingImport(User user, Portfolio portfolio, ImportMode mode) {
        PortfolioImport portfolioImport = new PortfolioImport();
        portfolioImport.setUser(user);
        portfolioImport.setPortfolio(portfolio);
        portfolioImport.setMode(mode.name());
        portfolioImport.setBaseCurrency("EUR");
        portfolioImport.setStatus("PREVIEW");
        portfolioImport.setExpiresAt(LocalDateTime.now().plusHours(1));
        ReflectionTestUtils.setField(portfolioImport, "id", UUID.randomUUID());
        return portfolioImport;
    }

    private Security security(String symbol, String isin) {
        Security security = new Security();
        security.setSymbol(symbol);
        security.setCompanyName(symbol + " Inc.");
        security.setIsin(isin);
        ReflectionTestUtils.setField(security, "id", UUID.randomUUID());
        return security;
    }

    private PortfolioImportRow readySecurityRow(PortfolioImport portfolioImport, int rowNumber, Security security, BigDecimal quantity) {
        PortfolioImportRow row = new PortfolioImportRow();
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        row.setRowNumber(rowNumber);
        row.setProductName(security.getSymbol());
        row.setIsin(security.getIsin());
        row.setQuantity(quantity);
        row.setNativeCurrency("EUR");
        row.setClassification("SECURITY");
        row.setResolvedSecurity(security);
        row.setStatus(ImportRowStatus.READY.name());
        portfolioImport.addRow(row);
        return row;
    }

    private PortfolioImportRow needsMappingRow(PortfolioImport portfolioImport, int rowNumber, String isin) {
        PortfolioImportRow row = new PortfolioImportRow();
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        row.setRowNumber(rowNumber);
        row.setProductName("Unresolved");
        row.setIsin(isin);
        row.setQuantity(new BigDecimal("1"));
        row.setNativeCurrency("EUR");
        row.setClassification("SECURITY");
        row.setStatus(ImportRowStatus.NEEDS_MAPPING.name());
        portfolioImport.addRow(row);
        return row;
    }

    private PortfolioImportCommitRequest requestWith(Set<UUID> skipped, List<IsinMappingRequest> mappings) {
        return new PortfolioImportCommitRequest(null, false, skipped, mappings);
    }

    // --- Section 2: cost-basis preservation ---

    @Test
    void synchronizeHolding_preservesExistingCostBasisOnMerge() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.MERGE);
        Security security = security("AAPL", "US0378331005");
        readySecurityRow(portfolioImport, 1, security, new BigDecimal("10"));

        Holding existingHolding = new Holding();
        existingHolding.setPortfolio(portfolio);
        existingHolding.setSymbol("AAPL");
        existingHolding.setQuantity(new BigDecimal("5"));
        existingHolding.setAverageCostBasis(new BigDecimal("142.50"));

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));
        when(holdings.findByPortfolioAndSymbol(portfolio, "AAPL")).thenReturn(List.of(existingHolding));
        when(holdings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().commit(authentication, portfolioImport.getId(), requestWith(Set.of(), List.of()));

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdings).save(captor.capture());
        assertThat(captor.getValue().getAverageCostBasis()).isEqualByComparingTo("142.50");
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void synchronizeHolding_newHoldingHasNullCostBasis() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.MERGE);
        Security security = security("AAPL", "US0378331005");
        readySecurityRow(portfolioImport, 1, security, new BigDecimal("10"));

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));
        when(holdings.findByPortfolioAndSymbol(portfolio, "AAPL")).thenReturn(List.of());
        when(holdings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().commit(authentication, portfolioImport.getId(), requestWith(Set.of(), List.of()));

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdings).save(captor.capture());
        assertThat(captor.getValue().getAverageCostBasis()).isNull();
    }

    // --- Section 3: ISIN admin-approval gate ---

    @Test
    void applyMappings_blocksNewIsinBindingForNonAdmin() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.MERGE);
        PortfolioImportRow row = needsMappingRow(portfolioImport, 1, "US1111111111");
        Security target = security("NEWCO", null);

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));
        when(securities.findById(target.getId())).thenReturn(Optional.of(target));
        when(securities.findByIsin("US1111111111")).thenReturn(Optional.empty());

        List<IsinMappingRequest> mappings = List.of(new IsinMappingRequest(row.getId(), target.getId()));

        assertThatThrownBy(() -> service().commit(authentication, portfolioImport.getId(), requestWith(Set.of(), mappings)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(row.getStatus()).isEqualTo(ImportRowStatus.NEEDS_ADMIN_MAPPING.name());
        verify(securityIsinService, never()).assignIsin(any(), any());
    }

    @Test
    void applyMappings_allowsNewIsinBindingForAdmin() {
        User user = user(UserRole.ADMIN);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.MERGE);
        PortfolioImportRow row = needsMappingRow(portfolioImport, 1, "US1111111111");
        Security target = security("NEWCO", null);
        Security bound = security("NEWCO", "US1111111111");

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));
        when(securities.findById(target.getId())).thenReturn(Optional.of(target));
        when(securities.findByIsin("US1111111111")).thenReturn(Optional.empty());
        when(securityIsinService.assignIsin(target.getId(), "US1111111111")).thenReturn(bound);
        when(holdings.findByPortfolioAndSymbol(portfolio, "NEWCO")).thenReturn(List.of());
        when(holdings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<IsinMappingRequest> mappings = List.of(new IsinMappingRequest(row.getId(), target.getId()));
        PortfolioImportCommitResponse response = service().commit(authentication, portfolioImport.getId(), requestWith(Set.of(), mappings));

        assertThat(response.status()).isEqualTo("COMMITTED");
        verify(securityIsinService).assignIsin(target.getId(), "US1111111111");
    }

    // --- Section 4: broader commit() coverage ---

    @Test
    void commit_mergeMode_consolidatesDuplicatePositions() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.MERGE);
        Security security = security("AAPL", "US0378331005");
        readySecurityRow(portfolioImport, 1, security, new BigDecimal("5"));
        readySecurityRow(portfolioImport, 2, security, new BigDecimal("3"));

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));
        when(holdings.findByPortfolioAndSymbol(portfolio, "AAPL")).thenReturn(List.of());
        when(holdings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().commit(authentication, portfolioImport.getId(), requestWith(Set.of(), List.of()));

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdings).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("8");
    }

    @Test
    void commit_replaceMode_requiresExplicitConfirmation() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.REPLACE);
        readySecurityRow(portfolioImport, 1, security("AAPL", "US0378331005"), new BigDecimal("1"));

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> service().commit(authentication, portfolioImport.getId(),
                requestWith(Set.of(), List.of())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(holdings, never()).deleteByPortfolio(any());
    }

    @Test
    void commit_replaceMode_deletesExistingHoldingsOnlyAfterValidation() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.REPLACE);
        Security security = security("AAPL", "US0378331005");
        readySecurityRow(portfolioImport, 1, security, new BigDecimal("1"));

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));
        when(holdings.findByPortfolioAndSymbol(portfolio, "AAPL")).thenReturn(List.of());
        when(holdings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PortfolioImportCommitRequest request = new PortfolioImportCommitRequest(null, true, Set.of(), List.of());
        service().commit(authentication, portfolioImport.getId(), request);

        verify(holdings).deleteByPortfolio(portfolio);
        verify(cashBalances).deleteByPortfolio(portfolio);
    }

    @Test
    void commit_replaceMode_withUnresolvedRow_doesNotDeleteAnything() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.REPLACE);
        needsMappingRow(portfolioImport, 1, "US1111111111");

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));

        PortfolioImportCommitRequest request = new PortfolioImportCommitRequest(null, true, Set.of(), List.of());

        assertThatThrownBy(() -> service().commit(authentication, portfolioImport.getId(), request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(holdings, never()).deleteByPortfolio(any());
        verify(cashBalances, never()).deleteByPortfolio(any());
    }

    @Test
    void commit_mergeMode_isIdempotentForSameUploadedFile() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.MERGE);
        Security security = security("AAPL", "US0378331005");
        readySecurityRow(portfolioImport, 1, security, new BigDecimal("10"));

        Holding existingHolding = new Holding();
        existingHolding.setPortfolio(portfolio);
        existingHolding.setSymbol("AAPL");
        existingHolding.setQuantity(new BigDecimal("10")); // already committed once from the same file

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));
        when(holdings.findByPortfolioAndSymbol(portfolio, "AAPL")).thenReturn(List.of(existingHolding));
        when(holdings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().commit(authentication, portfolioImport.getId(), requestWith(Set.of(), List.of()));

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdings).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void commit_alreadyCommittedImport_returnsCachedResultWithoutReprocessing() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.MERGE);
        portfolioImport.setStatus("COMMITTED");

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));

        PortfolioImportCommitResponse response = service().commit(authentication, portfolioImport.getId(), requestWith(Set.of(), List.of()));

        assertThat(response.status()).isEqualTo("COMMITTED");
        verify(holdings, never()).save(any());
        verify(portfolios, never()).findByIdAndUser(any(), any());
    }

    @Test
    void commit_skippedRow_isExcludedButRemainsInResult() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.MERGE);
        Security security = security("AAPL", "US0378331005");
        readySecurityRow(portfolioImport, 1, security, new BigDecimal("10"));
        PortfolioImportRow invalidRow = needsMappingRow(portfolioImport, 2, "US2222222222");
        invalidRow.setStatus(ImportRowStatus.INVALID.name());

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));
        when(portfolios.findByIdAndUser(portfolio.getId(), user)).thenReturn(Optional.of(portfolio));
        when(holdings.findByPortfolioAndSymbol(portfolio, "AAPL")).thenReturn(List.of());
        when(holdings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PortfolioImportCommitResponse response = service().commit(authentication, portfolioImport.getId(),
                requestWith(Set.of(invalidRow.getId()), List.of()));

        assertThat(response.skippedRows()).isEqualTo(1);
        assertThat(invalidRow.getCommittedOutcome()).isEqualTo("SKIPPED");
    }

    @Test
    void commit_foreignPortfolio_isRejectedWithOwnershipSafe404() {
        User user = user(UserRole.INVESTOR);
        UUID importId = UUID.randomUUID();
        when(imports.findByIdAndUser(importId, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().commit(authentication, importId, requestWith(Set.of(), List.of())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void commit_expiredPreview_isRejected() {
        User user = user(UserRole.INVESTOR);
        Portfolio portfolio = portfolio(user);
        PortfolioImport portfolioImport = pendingImport(user, portfolio, ImportMode.MERGE);
        portfolioImport.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(imports.findByIdAndUser(portfolioImport.getId(), user)).thenReturn(Optional.of(portfolioImport));

        assertThatThrownBy(() -> service().commit(authentication, portfolioImport.getId(), requestWith(Set.of(), List.of())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // --- Section 5: N+1 regression coverage ---

    @Test
    void preview_batchesIsinLookupsInsteadOfOnePerRow() throws Exception {
        User user = user(UserRole.INVESTOR);
        int rowCount = 30;
        List<ParsedPortfolioRow> parsed = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            parsed.add(new ParsedPortfolioRow(i + 1, "Security " + i, "CODE" + i, "US" + String.format("%010d", i),
                    new BigDecimal("1"), new BigDecimal("10"), "EUR", new BigDecimal("10"), new BigDecimal("10"),
                    "SECURITY", ImportRowStatus.NEEDS_MAPPING, null, null));
        }
        MockMultipartFile file = new MockMultipartFile("file", "Portfolio.csv", "text/csv",
                "Prodotto,Codice\n".getBytes(StandardCharsets.UTF_8));

        when(parser.parse(any(), eq(1_000))).thenReturn(parsed);
        when(imports.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(securities.findByIsinIn(any())).thenReturn(List.of());

        PortfolioImportPreviewResponse response = service().preview(authentication, file, null, "EUR", ImportMode.MERGE);

        assertThat(response.sourceRowCount()).isEqualTo(rowCount);
        verify(securities, times(1)).findByIsinIn(any());
        verify(securities, never()).findByIsin(any());
    }
}
