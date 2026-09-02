package it.mazzoni.vis.thesis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Low-cost drift tripwire for the evidenceFields parity chain (thesis-output.schema.json ->
 * config/vertex-gemini-v1.json -> the backend test fixture -> this enum ->
 * ThesisResponseSchema.EVIDENCE_FIELDS -> system-prompt-v3.txt's allowed-values list). RM4
 * added 5 REIT fields to the pre-existing 12. */
class EvidenceFieldTest {

    @Test
    void values_has17Entries_12PreExistingPlus5Reit() {
        assertThat(EvidenceField.values()).hasSize(17);
    }

    @Test
    void values_includesTheFiveReitFields() {
        assertThat(EvidenceField.values()).contains(
                EvidenceField.ffoPerShare, EvidenceField.affoPerShare,
                EvidenceField.priceToFfo, EvidenceField.priceToAffo,
                EvidenceField.affoPayoutRatio);
    }
}
