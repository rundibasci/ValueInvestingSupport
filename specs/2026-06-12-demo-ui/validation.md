# Phase Z5: Demo UI — Validation

## Definition of Done

### Functional

- [ ] Open `demo-ui/index.html` directly in the browser (no server) — page loads without errors
- [ ] Enter `AAPL` → click Analyze → all fields populate within 3 seconds
- [ ] Enter `MSFT` → confirms behavior is not AAPL-specific
- [ ] Enter `INVALID123` → page shows "Symbol not found" — no crash, no blank screen
- [ ] With Spring Boot stopped → page shows "Service unavailable" — no crash
- [ ] Stock with `valuation.dcf === null` → "DCF not available: insufficient FCF history" displayed instead of blank/broken layout
- [ ] MoS badge is green for MoS > 15%, yellow for 5–15%, red for < 5% or negative
- [ ] MoS percentage value is shown inside the badge
- [ ] MiFID II disclaimer is visible at the bottom without scrolling on a 1080p screen
- [ ] Pressing Enter in the ticker input submits the form

### Non-functional

- [ ] No JavaScript console errors in Chrome DevTools
- [ ] No JavaScript console errors in Firefox DevTools
- [ ] No external network calls other than to `localhost:8080` (verify in Network tab)
- [ ] No Spring Boot source changes were required (CORS already configured in Z4)

### Out of scope for this phase

- Automated UI / end-to-end tests (deferred to I1)
- Mobile / responsive layout (deferred to H5)
- Dark theme (deferred to H-series)

## Merge Criteria

All functional checkboxes above pass with `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` running on port 8080. The file `demo-ui/index.html` must be the only new file introduced.
