package it.mazzoni.vis.client.yahoo.dto;

import java.util.List;

public record QuoteSummaryData(List<QuoteSummaryResult> result, Object error) {}
