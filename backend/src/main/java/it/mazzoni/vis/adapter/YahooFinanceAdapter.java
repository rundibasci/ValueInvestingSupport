package it.mazzoni.vis.adapter;

import it.mazzoni.vis.client.yahoo.dto.*;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Component
public class YahooFinanceAdapter {

    public FundamentalSnapshot toFundamentalSnapshot(
            String symbol, QuoteSummaryResponse qsr, ChartResponse cr) {

        QuoteSummaryResult r = qsr.quoteSummary().result().get(0);
        FinancialDataDto fd = r.financialData();
        DefaultKeyStatisticsDto ks = r.defaultKeyStatistics();
        AssetProfileDto ap = r.assetProfile();
        SummaryDetailDto sd = r.summaryDetail();

        BigDecimal currentPrice = extractCurrentPrice(cr, fd);

        String companyName = null;
        if (cr != null && cr.chart() != null
                && cr.chart().result() != null
                && !cr.chart().result().isEmpty()) {
            ChartMeta meta = cr.chart().result().get(0).meta();
            companyName = meta.longName() != null ? meta.longName() : meta.shortName();
        }

        List<BigDecimal> revenueHistory = extractIncomeField(
                r.incomeStatementHistory(), IncomeStatementEntry::totalRevenue);
        List<BigDecimal> netIncomeHistory = extractIncomeField(
                r.incomeStatementHistory(), IncomeStatementEntry::netIncome);
        List<BigDecimal> fcfHistory = extractFcfHistory(r.cashflowStatementHistory());

        BigDecimal totalDebt = fd != null ? rawToBigDecimal(fd.totalDebt()) : null;
        BigDecimal cash = fd != null ? rawToBigDecimal(fd.totalCash()) : null;
        BigDecimal netDebt = (totalDebt != null && cash != null)
                ? totalDebt.subtract(cash) : null;

        Long shares = null;
        if (ks != null && ks.sharesOutstanding() != null
                && ks.sharesOutstanding().raw() != null) {
            shares = ks.sharesOutstanding().raw().longValue();
        }

        return new FundamentalSnapshot(
                symbol.toUpperCase(),
                companyName,
                ap != null ? ap.sector() : null,
                ap != null ? ap.industry() : null,
                ap != null ? ap.country() : null,
                sd != null ? sd.currency() : null,
                currentPrice,
                ks != null ? rawToBigDecimal(ks.trailingEps()) : null,
                ks != null ? rawToBigDecimal(ks.bookValue()) : null,
                shares,
                revenueHistory,
                netIncomeHistory,
                fcfHistory,
                netDebt,
                totalDebt,
                cash
        );
    }

    public RatioSnapshot toRatioSnapshot(String symbol, QuoteSummaryResponse qsr) {
        QuoteSummaryResult r = qsr.quoteSummary().result().get(0);
        FinancialDataDto fd = r.financialData();
        DefaultKeyStatisticsDto ks = r.defaultKeyStatistics();
        SummaryDetailDto sd = r.summaryDetail();

        BigDecimal roic = computeRoic(r.incomeStatementHistory(), r.balanceSheetHistory());

        return new RatioSnapshot(
                symbol.toUpperCase(),
                sd != null ? rawToBigDecimal(sd.trailingPE()) : null,
                sd != null ? rawToBigDecimal(sd.forwardPE()) : null,
                ks != null ? rawToBigDecimal(ks.priceToBook()) : null,
                fd != null ? rawToBigDecimal(fd.returnOnEquity()) : null,
                fd != null ? rawToBigDecimal(fd.returnOnAssets()) : null,
                roic,
                fd != null ? rawToBigDecimal(fd.currentRatio()) : null,
                fd != null ? rawToBigDecimal(fd.debtToEquity()) : null,
                sd != null ? rawToBigDecimal(sd.dividendYield()) : null,
                sd != null ? rawToBigDecimal(sd.payoutRatio()) : null,
                sd != null ? rawToBigDecimal(sd.beta()) : null
        );
    }

    public CompanyProfile toCompanyProfile(String symbol, QuoteSummaryResponse qsr, ChartResponse cr) {
        QuoteSummaryResult r = qsr.quoteSummary().result().get(0);
        AssetProfileDto ap = r.assetProfile();
        SummaryDetailDto sd = r.summaryDetail();
        DefaultKeyStatisticsDto ks = r.defaultKeyStatistics();

        String companyName = null;
        if (cr != null && cr.chart() != null
                && cr.chart().result() != null
                && !cr.chart().result().isEmpty()) {
            ChartMeta meta = cr.chart().result().get(0).meta();
            companyName = meta.longName() != null ? meta.longName() : meta.shortName();
        }

        BigDecimal marketCap = null;
        if (sd != null && sd.marketCap() != null) {
            marketCap = rawToBigDecimal(sd.marketCap());
        } else if (ks != null && ks.sharesOutstanding() != null) {
            BigDecimal shares = rawToBigDecimal(ks.sharesOutstanding());
            BigDecimal price = cr != null && cr.chart() != null
                    && cr.chart().result() != null && !cr.chart().result().isEmpty()
                    ? BigDecimal.valueOf(cr.chart().result().get(0).meta().regularMarketPrice())
                    : null;
            marketCap = (shares != null && price != null) ? shares.multiply(price) : null;
        }

        return new CompanyProfile(
                symbol.toUpperCase(),
                companyName,
                ap != null ? ap.sector() : null,
                ap != null ? ap.industry() : null,
                ap != null ? ap.country() : null,
                sd != null ? sd.currency() : null,
                null,
                marketCap
        );
    }

    public MarketPriceQuote toPriceQuote(String symbol, ChartResponse cr) {
        if (cr == null || cr.chart() == null
                || cr.chart().result() == null || cr.chart().result().isEmpty()) {
            return new MarketPriceQuote(symbol.toUpperCase(), null, null, null, null);
        }
        ChartMeta meta = cr.chart().result().get(0).meta();
        BigDecimal price = meta.regularMarketPrice() != null
                ? BigDecimal.valueOf(meta.regularMarketPrice()) : null;
        return new MarketPriceQuote(symbol.toUpperCase(), price, meta.currency(), null, null);
    }

    // --- helpers ---

    static BigDecimal rawToBigDecimal(YahooValue value) {
        if (value == null || value.raw() == null) return null;
        return BigDecimal.valueOf(value.raw());
    }

    private BigDecimal extractCurrentPrice(ChartResponse cr, FinancialDataDto fd) {
        if (cr != null && cr.chart() != null
                && cr.chart().result() != null
                && !cr.chart().result().isEmpty()) {
            Double price = cr.chart().result().get(0).meta().regularMarketPrice();
            if (price != null) return BigDecimal.valueOf(price);
        }
        return rawToBigDecimal(fd != null ? fd.currentPrice() : null);
    }

    private List<BigDecimal> extractIncomeField(
            IncomeStatementHistoryDto hist,
            Function<IncomeStatementEntry, YahooValue> extractor) {
        if (hist == null || hist.entries() == null) return Collections.emptyList();
        return hist.entries().stream()
                .limit(4)
                .map(extractor)
                .map(YahooFinanceAdapter::rawToBigDecimal)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<BigDecimal> extractFcfHistory(CashflowStatementHistoryDto cfHist) {
        if (cfHist == null || cfHist.entries() == null) return Collections.emptyList();
        return cfHist.entries().stream()
                .limit(4)
                .map(e -> {
                    BigDecimal opCf = rawToBigDecimal(e.totalCashFromOperatingActivities());
                    BigDecimal capex = rawToBigDecimal(e.capitalExpenditures());
                    if (opCf == null || capex == null) return null;
                    // capex is negative in Yahoo Finance, so opCf + capex = FCF
                    return opCf.add(capex);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private BigDecimal computeRoic(
            IncomeStatementHistoryDto isHist, BalanceSheetHistoryDto bsHist) {
        if (isHist == null || isHist.entries() == null || isHist.entries().isEmpty()) return null;
        if (bsHist == null || bsHist.entries() == null || bsHist.entries().isEmpty()) return null;

        BigDecimal netIncome = rawToBigDecimal(isHist.entries().get(0).netIncome());
        BalanceSheetEntry bs = bsHist.entries().get(0);
        BigDecimal equity = rawToBigDecimal(bs.totalStockholderEquity());
        BigDecimal longTermDebt = rawToBigDecimal(bs.longTermDebt());

        if (netIncome == null || equity == null) return null;
        BigDecimal investedCapital = equity.add(
                longTermDebt != null ? longTermDebt : BigDecimal.ZERO);
        if (investedCapital.compareTo(BigDecimal.ZERO) == 0) return null;

        return netIncome.divide(investedCapital, 4, RoundingMode.HALF_UP);
    }
}
