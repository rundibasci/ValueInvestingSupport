package it.mazzoni.vis.client.yahoo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CashflowStatementHistoryDto(
        @JsonProperty("cashflowStatements") List<CashflowEntry> entries
) {}
