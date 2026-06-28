# HD4 - Beta Feature Selection Plan

1. **Confirm HD3 findings and selected scope**
   - Review `specs/2026-06-28-hd3-beta-tester-personas/extracted-roadmap-requirements.md`.
   - Map all eight HD3 requirements to implemented, deferred, or rejected decisions.
   - Keep HD4 implementation focused on trust blockers: score/data-quality transparency, concentration warnings, and watchlist rationale.
   - Record deferral rationale for screener diagnostics, cross-symbol comparison, story-versus-fundamentals support, and persona replay scripts.

2. **Design shared score and data-quality status model**
   - Identify backend DTOs and frontend models that currently expose scores, valuations, seed results, review data, screener/search rows, and portfolio holdings.
   - Define the shared status vocabulary and any display labels in one consistent place per layer.
   - Add structured fields for availability state, reason, data freshness, and guardrail explanation where supported.
   - Ensure missing or stale metrics remain visible rather than appearing as blank or zero values.

3. **Implement score and data-quality transparency**
   - Update backend mappers/services to populate structured availability fields from existing data paths.
   - Update frontend review, seed, screener/search, and portfolio surfaces touched by the selected scope to render status labels.
   - Add targeted tests for available, stale, missing computation, provider-limited, and guardrail-blocked cases where fixtures or existing services make them practical.
   - Preserve MiFID II decision-support copy on valuation and score surfaces.

4. **Implement portfolio concentration warnings**
   - Inspect portfolio read models, holding price data, and sector metadata available to the UI.
   - Add backend or frontend calculation support for holding and sector concentration based on persisted holdings and available price/security data.
   - Show warnings in portfolio detail and add-to-portfolio flows when concentration thresholds are exceeded.
   - Show explainable unavailable states when price or sector data prevents concentration calculation.
   - Add tests for concentrated holding, concentrated sector, diversified portfolio, and missing-data cases.

5. **Implement watchlist research rationale**
   - Add persistence support for watchlist note and monitoring reason/category.
   - Update watchlist request/response DTOs, service validation, and API tests.
   - Update watchlist UI create/edit/read flows to capture and display rationale.
   - Keep rationale user-owned and concise; do not attach notes to shared security records.

6. **Update documentation and demo notes**
   - Add a short HD4 feature-selection report or update this spec directory with implementation decisions as work lands.
   - Update any user-facing demo notes affected by new status labels, concentration warnings, or watchlist rationale fields.
   - Ensure deferred HD3 requirements have named follow-up phases.

7. **Validate and prepare for merge**
   - Run targeted backend tests for changed services/controllers/mappers.
   - Run frontend typecheck/build and targeted component tests if present.
   - Run local demo smoke flows for the impacted HD3 persona paths: review/score visibility, portfolio concentration, and watchlist rationale.
   - Run `git diff --check`.
   - Capture validation evidence in `validation.md` before merge.
