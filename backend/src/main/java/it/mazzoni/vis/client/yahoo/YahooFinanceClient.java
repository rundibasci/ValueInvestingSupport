package it.mazzoni.vis.client.yahoo;

import it.mazzoni.vis.client.yahoo.dto.ChartResponse;
import it.mazzoni.vis.client.yahoo.dto.QuoteSummaryResponse;

public interface YahooFinanceClient {
    QuoteSummaryResponse getQuoteSummary(String symbol);
    ChartResponse getChart(String symbol);
}
