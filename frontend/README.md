# Value Investing Support Frontend

The H1 frontend is a Vite + React 18 + TypeScript + TailwindCSS application.

## Local development

1. Copy `.env.example` to `.env.local` if the backend is not available at `http://localhost:8080`.
2. Set `VITE_API_BASE_URL` to the backend URL when needed.
3. Run `npm install` and `npm run dev`.

Available scaffold routes are `/`, `/screener`, `/securities/:symbol`, `/portfolio`, and `/watchlist`.

## Checks

- `npm run typecheck`
- `npm run build`

Authentication, login, and protected routes begin in H2. The H1 API client only provides the shared configurable base URL and memory-token attachment boundary.
