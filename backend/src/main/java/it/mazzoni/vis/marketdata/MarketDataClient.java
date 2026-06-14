package it.mazzoni.vis.marketdata;

import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;

public interface MarketDataClient {
    CompanyProfile getProfile(String symbol);
    FundamentalSnapshot getFundamentals(String symbol);
    RatioSnapshot getRatios(String symbol);
    MarketPriceQuote getQuote(String symbol);
}
