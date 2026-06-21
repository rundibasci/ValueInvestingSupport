# H1 — Frontend Scaffold

## Context

H1 is the next roadmap phase after the completed alert and full-feature-demo work. It starts the production React frontend that will make the value-investing cycle approachable: screening candidates, investigating securities, forming portfolios, and monitoring watchlists.

The platform remains a decision-support product. This phase establishes presentation and integration foundations only; it does not itself introduce a valuation, score, recommendation, or advice workflow.

## Scope

- Create a Vite project using React 18, TypeScript 5 with strict mode, and TailwindCSS 3.
- Configure React Router v6 with routes for:
  - `/`
  - `/screener`
  - `/securities/:symbol`
  - `/portfolio`
  - `/watchlist`
- Configure a shared TanStack Query client for future REST-backed server state.
- Provide a configurable API base URL, defaulting to the local backend during development.
- Provide a shared HTTP client/interceptor mechanism that can attach an authentication token when one is available.
- Build an application shell with sidebar navigation, header, main-content region, route placeholders, and active navigation state.
- Ensure the shell is usable on common desktop and narrow viewport sizes.

## Decisions

- H1 includes a polished, navigable application shell rather than a bare project scaffold. Feature-specific UI and data workflows remain deferred to H2–H7.
- The frontend is a dedicated Vite/React application, matching the documented frontend stack; it is not added as another Spring Boot static HTML page.
- The API base URL is configurable through frontend environment configuration, with a local backend default suitable for development. Deployment-specific URLs must not be hard-coded into application code.
- TanStack Query owns server-state setup; no global client-state library is introduced in this phase.
- Authentication is represented only by the token-aware HTTP-client boundary. Login, secure token storage, refresh handling, and protected routes are H2 work.
- Navigation links target the roadmap routes even though their pages are placeholders in this phase, so later phases extend rather than replace the shell.
- No credentials, API keys, or token material may be committed. Runtime configuration follows the repository's secret-handling conventions.

## Out of Scope

- Login, logout, account lifecycle, token persistence, refresh flows, or protected-route enforcement.
- Screener filters, tables, security-detail data, charts, portfolio CRUD, watchlist workflows, alerts, dashboard content, or new backend endpoints.
- Changes to the Spring Boot API contracts, database schema, market-data clients, valuation engine, or alert engine.
- Investment advice or removal of any required MiFID II disclaimer from later valuation and scoring screens.
