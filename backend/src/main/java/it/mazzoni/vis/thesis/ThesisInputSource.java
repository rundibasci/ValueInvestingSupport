package it.mazzoni.vis.thesis;

import it.mazzoni.vis.domain.entity.Security;

/** Seam so {@link ThesisGenerationService} can be tested without the real
 * {@link ThesisInputBuilder}'s MarketDataClient/repository dependencies. */
public interface ThesisInputSource {
    ThesisInput build(Security security);
}
