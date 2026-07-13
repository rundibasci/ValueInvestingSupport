package it.mazzoni.vis.marketdata.fmp;

import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.marketdata.fmp.dto.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FmpAdapter {

    public CompanyProfile toCompanyProfile(FmpProfileEntry e) {
        return new CompanyProfile(
                e.symbol(),
                e.companyName(),
                e.sector(),
                e.industry(),
                e.country(),
                e.currency(),
                e.exchangeShortName(),
                e.mktCap()
        );
    }

    public FundamentalSnapshot toFundamentalSnapshot(
            String symbol,
            List<FmpIncomeStatementEntry> income,
            List<FmpBalanceSheetEntry> balance,
            List<FmpCashFlowEntry> cashflow,
            FmpProfileEntry profile,
            BigDecimal currentPrice) {

        BigDecimal epsTtm = income.isEmpty() ? null : income.get(0).epsDiluted();
        BigDecimal totalDebt = balance.isEmpty() ? null : balance.get(0).totalDebt();
        BigDecimal cash = balance.isEmpty() ? null : balance.get(0).cashAndShortTermInvestments();
        BigDecimal netDebt = (totalDebt != null && cash != null) ? totalDebt.subtract(cash) : null;
        Long shares = income.isEmpty() ? null : income.get(0).sharesOutstandingDil();

        BigDecimal bookValuePerShare = null;
        if (!balance.isEmpty() && balance.get(0).totalEquity() != null && shares != null && shares > 0) {
            bookValuePerShare = balance.get(0).totalEquity()
                    .divide(BigDecimal.valueOf(shares), 4, java.math.RoundingMode.HALF_UP);
        }

        List<BigDecimal> revenueHistory = income.stream()
                .limit(11).map(FmpIncomeStatementEntry::revenue).collect(Collectors.toList());
        List<BigDecimal> netIncomeHistory = income.stream()
                .limit(11).map(FmpIncomeStatementEntry::netIncome).collect(Collectors.toList());
        List<BigDecimal> fcfHistory = cashflow.stream()
                .limit(11).map(FmpCashFlowEntry::freeCashFlow).collect(Collectors.toList());
        List<BigDecimal> epsHistory = income.stream()
                .limit(11).map(FmpIncomeStatementEntry::epsDiluted).collect(Collectors.toList());
        List<Long> sharesOutstandingHistory = income.stream()
                .limit(11).map(FmpIncomeStatementEntry::sharesOutstandingDil).collect(Collectors.toList());

        return new FundamentalSnapshot(
                symbol.toUpperCase(),
                profile != null ? profile.companyName() : null,
                profile != null ? profile.sector() : null,
                profile != null ? profile.industry() : null,
                profile != null ? profile.country() : null,
                profile != null ? profile.currency() : null,
                currentPrice,
                epsTtm,
                bookValuePerShare,
                shares,
                revenueHistory,
                netIncomeHistory,
                fcfHistory,
                epsHistory,
                sharesOutstandingHistory,
                netDebt,
                totalDebt,
                cash
        );
    }

    public RatioSnapshot toRatioSnapshot(String symbol, FmpRatiosEntry e) {
        return toRatioSnapshot(symbol, e, null);
    }

    public RatioSnapshot toRatioSnapshot(String symbol, FmpRatiosEntry e, FmpKeyMetricsEntry metrics) {
        return new RatioSnapshot(
                symbol.toUpperCase(),
                firstNonNull(e.priceToEarningsRatio(), e.peRatio()),
                null,
                e.priceToBookRatio(),
                metrics != null ? metrics.returnOnEquity() : e.returnOnEquity(),
                metrics != null ? metrics.returnOnAssets() : e.returnOnAssets(),
                metrics != null ? firstNonNull(metrics.returnOnInvestedCapital(), metrics.returnOnCapitalEmployed()) : e.returnOnCapitalEmployed(),
                e.currentRatio(),
                firstNonNull(e.debtToEquityRatio(), e.debtToEquity()),
                e.dividendYield(),
                firstNonNull(e.dividendPayoutRatio(), e.payoutRatio()),
                null,
                e.grossProfitMargin(),
                e.operatingProfitMargin(),
                e.netProfitMargin()
        );
    }

    private static BigDecimal firstNonNull(BigDecimal preferred, BigDecimal fallback) {
        return preferred != null ? preferred : fallback;
    }

    public MarketPriceQuote toMarketPriceQuote(String symbol, FmpQuoteEntry e) {
        return new MarketPriceQuote(
                symbol.toUpperCase(),
                e.price(),
                null,
                e.change(),
                e.changesPercentage()
        );
    }
}
