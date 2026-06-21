# H1 — Validation

## Automated checks

- Install dependencies from the committed frontend manifest and run the production build successfully.
- Run the configured frontend static checks (type checking, linting, or equivalent project checks) successfully.
- Confirm route configuration includes `/`, `/screener`, `/securities/:symbol`, `/portfolio`, and `/watchlist`.
- Confirm the API base URL is supplied by configuration and that no committed file contains an API key, password, private key, or live token.

## Browser smoke test

1. Start the local Spring Boot backend and the Vite development server using the documented local API-base-URL configuration.
2. Open the root route and verify the sidebar, header, and main-content layout render without console errors.
3. Navigate through Screener, a sample Security Detail URL, Portfolio, and Watchlist; verify each route renders its placeholder and the matching navigation item is active.
4. Resize to a narrow viewport and verify navigation and content remain reachable and readable.
5. Inspect the browser network activity or a controlled test request to verify the shared client resolves requests against the configured local backend URL and can attach a provided token without exposing it in rendered UI.

## Merge criteria

- The Vite/React/TypeScript/Tailwind frontend builds cleanly and is runnable locally.
- The router, TanStack Query provider, configurable API client, and navigable application shell are present and usable.
- All five H1 routes are reachable through the UI or direct navigation.
- Automated checks and the browser smoke test pass against the local backend setup.
- The change set contains only intentional H1 work and no secrets.
