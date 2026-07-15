package it.mazzoni.vis.admin;

import java.util.List;

public record SeedSubmissionResult(List<SeedResult> synchronousResults, SeedRunAcceptedResponse accepted) {
    public boolean asynchronous() { return accepted != null; }
    static SeedSubmissionResult synchronous(List<SeedResult> results) { return new SeedSubmissionResult(results, null); }
    static SeedSubmissionResult asynchronous(SeedRunAcceptedResponse accepted) { return new SeedSubmissionResult(null, accepted); }
}
