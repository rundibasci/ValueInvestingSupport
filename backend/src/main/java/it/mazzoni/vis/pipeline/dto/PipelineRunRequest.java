package it.mazzoni.vis.pipeline.dto;

import java.util.List;

public record PipelineRunRequest(List<String> tickers) {}
