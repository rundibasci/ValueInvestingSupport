# Real-demo issue resolution trace

Date: 2026-07-15  
Source register: [real-demo-issues-to-verify.md](./real-demo-issues-to-verify.md)  
Configuration: `docker-compose.realDemo.yml`

## Resolution summary

| ID | Classification | Result | Verification evidence |
|---|---|---|---|
| RD-V-001 | Bug | Fixed | All 10 active symbols have normalized exchanges; exchange options are NASDAQ/NYSE; default preview is non-empty; partitions are 4 NASDAQ and 6 NYSE. |
| RD-V-002 | Bug | Fixed at API/service level | Market-cap min/max, volume, cap, sort, clearing, and bounds were verified against non-empty live data. Invalid input returns HTTP 400 with a specific reason. Literal browser automation remains under RD-V-008. |
| RD-V-003 | Bug | Fixed | Screener returns 10 unfiltered, 4 NASDAQ, and 6 NYSE securities, with exchange values on every active row. |
| RD-V-004 | Bug | Fixed | The configured 10-symbol universe is active; 55 legacy symbols are retained inactive. All active symbols have Piotroski, Altman, moat, and capital-allocation results. Peers exclude inactive symbols. |
| RD-V-005 | Missing feature | Open, reproducible | Persistent exclusions have no entity, migration, API, audit contract, or enabled UI workflow. This is a substantial feature, not a defect suitable for a contained repair. |
| RD-V-006 | Mixed data defects and provider limitations | Partially fixed | Exchange and market cap are populated for all active symbols and DCF remains valid. Explicit DDM eligibility, analyst-plan messaging, complete WACC provenance, and some capital-allocation availability explanations remain open. |
| RD-V-007 | Bug | Fixed | Disabled jobs expose `DISABLED`, `nextRunAt: null`, and `latestError: null`; historical disabled skips remain in history and the UI says `Schedule inactive`. |
| RD-V-008 | Not a bug | Open verification gap | A literal every-control browser pass still requires a browser/Playwright-capable environment. API checks and production frontend compilation do not replace that acceptance pass. |
| RD-V-009 | Not a bug | Open environment decision | Google OAuth is intentionally unavailable without credentials and the UI explains that state. Verification requires an OAuth-enabled real-demo configuration. |

No issue was marked **hard to reproduce**: the remaining items are deterministic feature, coverage, or environment gaps.

## Implemented repairs

- Added an explicit active-universe flag and migration; real-demo startup deactivates legacy rows and reactivates exactly the configured symbols.
- Restricted screener metadata, filters, and peer comparisons to active securities.
- Ingested and normalized Yahoo exchange metadata and persisted quote volume from Yahoo/FMP.
- Added fallback enrichment when an FMP profile succeeds but omits exchange data.
- Versioned market-data cache keys so older incomplete cached DTOs cannot block repair.
- Made startup idempotently update an existing same-day quote, including missing volume, and select the latest positive Yahoo volume interval.
- Derived missing market cap from the latest close and annual shares outstanding.
- Made exchange fallback strict so unknown exchanges are not silently assigned to NASDAQ or NYSE.
- Added universe numeric validation for negative values, inverted ranges, and `maxSymbols` outside 1–500; validation responses include the specific reason.
- Made disabled-job schedule/status presentation unambiguous in both API and UI.
- Added and adjusted backend tests for DTO mapping, universe selection, active-universe behavior, peers, and job monitoring.

## Independent live verification

Separate verification agents rechecked every changed issue after deployment. Final live observations after startup ingestion included:

- Active universe: 10 configured symbols; legacy universe: 55 inactive symbols.
- Exchange coverage: 10/10; market-cap coverage: 10/10; quote-volume coverage: 10/10.
- Default curation preview: 7 results after positive-volume enrichment; numeric matrix and explicit validation responses passed.
- Analytics: Piotroski 10/10, Altman 10/10, moat 10/10, capital allocation 10/10.
- Jobs: all seven disabled jobs report no active next run and no current error.

The final positive-volume adjustment was deployed with a new cache version so incomplete FMP volume and current partial Yahoo intervals cannot overwrite the latest completed positive interval. Independent verification confirmed positive latest volume for all 10 active symbols.

## Validation notes

- Backend production package and test compilation passed.
- Frontend TypeScript/Vite production build passed in Docker.
- Selected non-Mockito tests passed; the local full selected suite is blocked by Mockito inline-agent attachment on host JDK 26, not by test assertions. The deployed backend runs Java 21.
- Browser screenshots and Playwright traces remain explicitly open under RD-V-008.
