package it.mazzoni.vis.marketdata;

import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.HistoricalPriceQuote;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.marketdata.fmp.dto.FmpDividendEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpInsiderTradingEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketDataClient {
    CompanyProfile getProfile(String symbol);
    FundamentalSnapshot getFundamentals(String symbol);
    RatioSnapshot getRatios(String symbol);
    MarketPriceQuote getQuote(String symbol);

    /** Returns all tradable stocks for the given exchange short name (e.g. "NYSE", "NASDAQ"). */
    List<FmpStockListEntry> listSymbols(String exchange);

    /** Returns historical end-of-day price rows, newest first. */
    List<HistoricalPriceQuote> getHistoricalPrices(String symbol, LocalDate from, LocalDate to);

    /** Returns the full dividend history for the given symbol, newest first. */
    List<FmpDividendEntry> getDividendHistory(String symbol);

    /** Returns the most recent insider transactions for the given symbol. */
    List<FmpInsiderTradingEntry> getInsiderTransactions(String symbol);

    /** Returns FMP's precomputed DCF intrinsic value, or empty if unavailable. */
    Optional<BigDecimal> getFmpDcf(String symbol);
}
