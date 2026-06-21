# H1 — Implementation Plan

1. Establish the frontend project
   - Create a Vite-based React 18 application using TypeScript in strict mode.
   - Configure TailwindCSS 3 and the shared frontend build, development, and test commands.
   - Keep the frontend independent from backend source code while allowing local development alongside Spring Boot.

2. Build the application foundation
   - Configure React Router v6 routes for `/`, `/screener`, `/securities/:symbol`, `/portfolio`, and `/watchlist`.
   - Set up the TanStack Query client with appropriate defaults for REST-backed server state.
   - Add a configurable API base URL and a shared HTTP client that attaches an available authentication token.

3. Deliver the navigable application shell
   - Create the persistent sidebar navigation, header, and main-content layout.
   - Add route-level placeholder pages with clear names and active-navigation state.
   - Make the shell responsive and accessible enough to support the subsequent feature phases.

4. Verify the scaffold
   - Run the frontend build and static checks.
   - Start the frontend with the local backend configuration and perform a browser smoke test of each route, navigation, and API-client configuration.
   - Confirm the committed source and configuration contain no credentials or other secrets.
