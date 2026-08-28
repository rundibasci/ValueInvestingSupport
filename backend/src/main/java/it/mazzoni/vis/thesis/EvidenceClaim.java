package it.mazzoni.vis.thesis;

import java.util.List;

/** A single bull-case or bear-case entry. Mirrors thesis-output.schema.json -> $defs.evidence. */
public record EvidenceClaim(String claim, List<EvidenceField> evidenceFields) {}
