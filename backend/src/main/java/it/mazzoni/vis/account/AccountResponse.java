package it.mazzoni.vis.account;

public record AccountResponse(
        String email,
        String role,
        boolean googleLinked,
        boolean localPasswordAvailable
) {
}
