# Plan — Phase LS2: HTML Demo Client

## Task Group 1 — Admin Ping Endpoint

1.1 Create `AdminController` in `backend/src/main/java/.../controller/AdminController.java`
    - `@RestController`, `@RequestMapping("/api/v1/admin")`
    - `@GetMapping("/ping")` — `@PreAuthorize("hasRole('ADMIN')")` — returns `{ "status": "ok", "role": "ADMIN" }`
    - Response record: `PingResponse(String status, String role)`

1.2 Verify Spring Security config (`SecurityConfig`) already permits `/api/**` only with auth
    and that the existing `ADMIN` role authority string matches `hasRole('ADMIN')`.

## Task Group 2 — demo.html Static Page

2.1 Create `backend/src/main/resources/static/demo.html`
    - Login form: username + password inputs, "Login" button
    - On submit: `POST /auth/login` with JSON body → extract `accessToken` from response → store in JS `let token`
    - Show login status (success / error message)

2.2 "Ping Admin" button
    - `GET /api/v1/admin/ping` with `Authorization: Bearer <token>` header
    - Response panel shows: HTTP status code, `role` field from JSON body, `X-Cache` response header
      (or "n/a" if absent)

2.3 Token display section: shows first 40 chars of JWT + `…` so the user can see a token is present
    without exposing the full value.

2.4 Styling: plain CSS inline, no external CDN — works fully offline. Error states styled in red,
    success in green.

## Task Group 3 — Startup URL Banner

3.1 Create `DemoStartupListener` (active only on `demo` profile):
    - Implements `ApplicationListener<ApplicationReadyEvent>`
    - On event: logs `System.out.println("\n>>> Demo ready at http://localhost:8080/demo.html\n")`
    - Annotated `@Profile("demo")` so it never fires in `local` or `prod`

## Task Group 4 — Smoke Check & Cleanup

4.1 Start the app with `demo` profile; confirm console prints the URL banner.
4.2 Open `http://localhost:8080/demo.html` in browser; run the manual test from `validation.md`.
4.3 Confirm the app remains running after the test (no auto-exit).
4.4 Remove any TODO/placeholder comments left over from earlier phases in touched files.
