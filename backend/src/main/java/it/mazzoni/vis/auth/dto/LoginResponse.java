package it.mazzoni.vis.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, long expiresIn) {}
