# Phase Z5: Demo UI — Requirements

## Scope

Build a single, self-contained HTML file (`demo-ui/index.html`) that lets a user type a ticker symbol and see the full valuation analysis returned by the `GET /demo/analyze/{symbol}` endpoint already live from Z4.

### In scope

- Single `index.html` using React 18 via CDN (no build step, no node_modules)
- Calls Spring Boot API at `http://localhost:8080/demo/analyze/{symbol}`
- Displays: company name, current price, currency, sector, DCF fair value (base / low / high), Graham Number, composite fair value, MoS badge (color-coded), recommendation label
- MoS gauge: green (> 15%), yellow (5–15%), red (< 5% or negative)
- Error states: symbol not found (404), service unavailable (503), loading spinner
- MiFID II disclaimer footer
- Opened directly in the browser as a file — no server needed for the UI

### Out of scope

- Authentication, routing, state management library
- Persistent state or local storage
- Multiple stock comparison
- Charts (deferred to Group H)
- Mobile-responsive design (deferred to Group H)

## Decisions

| Decision | Choice | Reason |
|---|---|---|
| Build tooling | None (CDN React + Babel standalone) | Zero setup; file opens directly in browser |
| Styling | Tailwind CSS via CDN | No build step; utility-first keeps the single file tidy |
| API base URL | `http://localhost:8080` hardcoded | Demo only — not a production deploy |
| Serving | Opened directly as a file | No Spring Boot static resource config needed |
| Location | `demo-ui/index.html` at project root | Separate from the Spring Boot `src/` tree |

## Context

This is the final phase of the M0 Demo milestone. Phases Z1–Z4 are merged: the backend is fully live and returns JSON with `valuation`, `marginOfSafety`, and `recommendation` fields.

The Yahoo Finance client (Z2) returns `valuation.dcf: null` when fewer than 3 years of positive FCF are found (RULE-06 guard). The UI must handle this gracefully — show "DCF not available: insufficient FCF history" rather than a broken layout or blank field.

The `GET /demo/analyze/{symbol}` endpoint already permits cross-origin requests (CORS configured in Z4). Verify before adding any Spring Boot changes.
