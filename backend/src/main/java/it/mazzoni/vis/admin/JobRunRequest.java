package it.mazzoni.vis.admin;

public record JobRunRequest(
        String symbols,
        String exchange,
        String dataTypes
) {
}
