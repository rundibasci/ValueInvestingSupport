package it.mazzoni.vis.client.yahoo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BalanceSheetHistoryDto(
        @JsonProperty("balanceSheetStatements") List<BalanceSheetEntry> entries
) {}
