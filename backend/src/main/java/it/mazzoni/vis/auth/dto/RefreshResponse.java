package it.mazzoni.vis.auth.dto;

public record RefreshResponse(String accessToken, long expiresIn) {}
