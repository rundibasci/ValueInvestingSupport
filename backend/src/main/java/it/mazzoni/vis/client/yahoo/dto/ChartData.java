package it.mazzoni.vis.client.yahoo.dto;

import java.util.List;

public record ChartData(List<ChartResult> result, Object error) {}
