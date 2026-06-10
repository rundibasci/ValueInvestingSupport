package it.mazzoni.vis.client.yahoo.dto;

public record QuoteSummaryResult(
        FinancialDataDto financialData,
        DefaultKeyStatisticsDto defaultKeyStatistics,
        IncomeStatementHistoryDto incomeStatementHistory,
        BalanceSheetHistoryDto balanceSheetHistory,
        CashflowStatementHistoryDto cashflowStatementHistory,
        SummaryDetailDto summaryDetail,
        AssetProfileDto assetProfile
) {}
