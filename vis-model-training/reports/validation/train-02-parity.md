# TRAIN-02 Python/Node Parity

Date: 2026-08-03

## Scope

The TRAIN-01 Node validator remains a temporary oracle while TRAIN-02 introduces a generic Python CLI. Parity means equal accept/reject outcomes for the official seed and representative shared rules; diagnostic wording is intentionally different because Python exposes stable, sanitized codes.

## Results

| Case | Python | Node | Python diagnostic |
|---|---:|---:|---|
| Official 10-record seed | accept (`0`) | accept (`0`) | 10 valid, 0 invalid, 0 warnings |
| Malformed first JSONL line | reject (`1`) | reject (`1`) | `JSONL_PARSE_ERROR` |
| Duplicate `exampleId` | reject (`1`) | reject (`1`) | `DUPLICATE_EXAMPLE_ID` |
| Prohibited `BUY` text | reject (`1`) | reject (`1`) | `PROHIBITED_RECOMMENDATION` |

Negative cases were executed against temporary copies of `vis-model-training`; the official examples and seed dataset were not modified.

## Intentional Differences

- The Node validator is phase-specific and requires the exact ten TRAIN-01 filenames, identifiers, symbols and scenarios.
- The Python validator accepts arbitrary dataset paths and record counts. It requires unique `exampleId` values but permits repeated symbols and scenario types so later datasets can contain multiple examples for the same synthetic subject or category.
- Python collects safe record-level diagnostics when possible, exposes versioned text/JSON reports, and reserves exit codes `2` and `3` for blocking configuration and internal failures.

The Node validator remains available after TRAIN-02 and may be deprecated only after downstream consumers adopt the Python CLI.
