# H7 - Dashboard Plan

1. **Scope alignment and contracts**
   - Review H1-H6 frontend conventions, portfolio/watchlist/alert API contracts, and any existing dashboard-adjacent components.
   - Confirm the feature-spec decisions for dashboard audience, default portfolio selection, top-mover definition, and calendar data source.
   - Add typed API-client functions, query keys, route wiring, and protected navigation entries for the dashboard experience.

2. **Dashboard data composition**
   - Compose portfolio summary data: total value, weighted average margin of safety, weighted yield, sector exposure, and data freshness indicators where available.
   - Compose top movers from portfolio holdings using cached/local quote data; never trigger live FMP/Yahoo calls from the dashboard view.
   - Compose active alert summary from existing alert endpoints, preserving severity/status labels and links to watchlist or security detail.
   - Compose upcoming earnings and dividend calendar data for the next 30 days using existing local data or a narrowly scoped backend addition if no suitable endpoint exists.

3. **Dashboard UI implementation**
   - Build the authenticated dashboard route as the first productive landing view after login.
   - Present dense, scannable panels for portfolio summary, top movers, active alerts, and upcoming earnings/dividends without marketing-style hero content.
   - Support loading, empty, stale-data, partial-data, API-error, unauthorized, and expired-session states.
   - Add links from dashboard items into portfolio, watchlist, alert, and security-detail workflows.

4. **Quality, accessibility, and merge readiness**
   - Add focused frontend tests for dashboard composition, empty/error states, links, badges, and disclaimer rendering.
   - Add deterministic integration or browser-level coverage for an authenticated user with portfolio holdings, alerts, and calendar events.
   - Run linting, TypeScript checks, tests, production build, and responsive/manual accessibility review.
   - Resolve scope questions, update the spec with final decisions, and keep implementation limited to H7.
