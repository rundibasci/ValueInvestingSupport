# VIS Model Training Data Policy

- Effective date: 2026-08-01
- Owner: VIS model-training maintainers
- Applies to: scenario inputs, prompts, teacher candidates, curated datasets, benchmarks, evaluation outputs, and release artefacts

## Permitted Data

- Project-authored synthetic financial scenarios using fictitious symbols.
- Deterministically generated synthetic scenarios whose rules, seed, and generator version are recorded.
- Teacher-generated candidates produced under reviewed terms and retained only through the controlled candidate workflow.
- Short project-authored instructions, labels, and review annotations.
- Third-party data only after its source, licence, allowed transformations, training rights, redistribution rights, and commercial restrictions are recorded and approved.

## Prohibited Data

- Passwords, API keys, access tokens, cookies, private keys, credentials, or secret configuration.
- Personal data or information that can identify an individual unless a separately approved privacy process exists.
- Proprietary or provider-derived financial data without explicit authorization for model training and the intended distribution mode.
- Real-company claims copied from memory or external knowledge without traceable and approved provenance.
- Substantial copyrighted text or transformations intended to reproduce protected expression.
- Teacher output whose provider terms do not permit the intended training or commercial use.
- Untraceable records, unreviewed raw candidates in release datasets, or examples that contain operational investment instructions.

## Required Provenance

Every candidate and released example must carry or resolve through a manifest to:

- unique example ID;
- scenario type and synthetic/third-party classification;
- source or generator identity and version;
- creation/generation timestamp;
- input/output schema and prompt versions;
- teacher model and immutable revision when applicable;
- dataset version;
- automated validation result;
- human review status and reviewer identity or controlled reviewer ID;
- licence-register entry and permitted-use classification.

## Review States

```text
CANDIDATE
AUTO_REJECTED
NEEDS_REVIEW
APPROVED
REJECTED
QUARANTINED
```

Only `APPROVED` records may enter a versioned training, validation, or test release. A release is immutable; corrections create a new version rather than rewriting an existing one.

## Candidate Workflow

1. Generate or author a candidate with complete provenance.
2. Store raw teacher candidates outside release datasets and outside Git when they are large, sensitive, or not yet licensed for redistribution.
3. Apply schema, semantic, numeric-grounding, prohibited-instruction, and secret checks.
4. Quarantine ambiguous licence, privacy, provenance, or factual-grounding cases.
5. Require explicit human approval before curation.
6. Deduplicate and split data without allowing test records or near-duplicates into training data.
7. Freeze the release with a manifest and hashes.

## Retention and Deletion

- Versioned approved dataset releases are immutable and retained with their manifests.
- Rejected and raw teacher candidates use a documented retention period established before TRAIN-05 production runs; until then, bulk generation is prohibited.
- Credentials and accidentally captured personal data are removed immediately, credentials are rotated, and the incident is documented without reproducing the secret.
- A licence or deletion request places affected records in `QUARANTINED` state and blocks new releases until resolved.

## Escalation

Stop processing and request owner/legal review when:

- rights to train, retain, redistribute, or use commercially are unclear;
- a record may contain personal or proprietary information;
- a teacher/provider changes its terms;
- provenance cannot be reconstructed;
- generated text resembles substantial third-party copyrighted material;
- the requested use crosses from decision support into personalized investment advice.

## Decision-Support Boundary

Financial calculations remain in deterministic VIS engines. Training data must not teach the model to retrieve facts, recalculate financial metrics, predict prices, or issue BUY, SELL, or HOLD instructions. Later user-facing outputs retain the VIS MiFID II disclaimer.

