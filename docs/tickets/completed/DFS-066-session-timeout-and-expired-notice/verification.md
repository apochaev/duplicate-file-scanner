# DFS-066 verification

## Product owner review

### What you can do now

- Leave a finished scan's results open as long as you like: the login no longer runs out after 30 idle minutes. It lasts until the server stops.
- When the login has ended (for example after a server restart), the page says so plainly: "Your session ended, or the server was restarted. Open the link printed in the console." It shows this both on a reload and when a call fails while you are using a screen. Before, the screen failed silently or showed a generic error.

### Acceptance criteria

- **DFS-066.AC-1** met: the running server's session timeout is 0, which Tomcat reads as "never expires". A test reads that value from the live Tomcat.
- **DFS-066.AC-2** met: a 403 from any `/api/` call (scan list, start, a page of sets) replaces the screen with the notice. See the concern under Known limits: some 403s do not mean the login ended.
- **DFS-066.AC-3** met: a page opened with no valid session (a 403, or "not authenticated") shows the same notice, and the old line "Open the link printed in the console" no longer appears on its own. No scan call is made.
- **DFS-066.AC-4** met: a `404 UNKNOWN_SCAN` keeps "This scan is not in this server run…" and a `500` keeps "The results could not be loaded; reload the page". Neither shows the notice.

### Try it

```
./gradlew :server:bootRun
```
1. Open the `Open http://127.0.0.1:<port>/?token=…` link it logs. The page shows "Connected" and the scan screen.
2. Stop the server (Ctrl+C), start it again, and reload the old tab without using the new link. The page shows "Your session ended, or the server was restarted. Open the link printed in the console." in place of the scan screen.
3. Open the new link. The page works again.

You can't see the idle part quickly. Leaving a finished scan idle for more than 30 minutes and then reloading should still show the results.

### Decided without you

- The timeout is written as `server.servlet.session.timeout=0` (zero, not a negative value). Both mean "never" to Tomcat.
- Any 403 from a URL under `/api/` counts as "session ended". The page does not look at the response body. This includes 403s the server sends for other reasons (see Known limits).
- Once the notice has shown, it stays until you reload with a fresh link. Nothing resets it.
- When the notice shows during use, it also replaces the "Connected" line, not only the screen.
- The new code lives in a new folder, `server/frontend/src/app/session/`.

### Known limits

- **403 is not always "session ended".** The server also answers `403 PATH_OUTSIDE_ROOTS` for a folder path outside the scan roots (result, overlaps and plan APIs). Today the page never sends such a path, because every path it sends came from the server. A future screen where you type paths (for example a deletion-plan builder) would show the session notice instead of a "path outside the scanned folders" error. A follow-up could ignore 403s whose body has a `code`.
- A broken live-progress stream (`EventSource`) is not covered. Ticket decision 3 accepts this, because a running scan keeps the login alive.
- The ticket title still says "for a day", but decision 1 chose no timeout at all. The code follows decision 1.
- **Merge risk:** DFS-065, open as uncommitted changes on `main`, also edits `app.html`. It moves "Connected" into the toolbar. Whichever ticket lands second must merge `app.html` by hand and keep the `showNotice()` switch.
- Docs are not updated here: the `CLAUDE.md` server entry and `docs/design/ui.md` are outside `touches`. Design item 13 leaves them to `done`.
- The by-hand done-item is only partly checked (see Evidence), so it is left unticked.

### Evidence

- `./gradlew build` is green: 279 of 279 Vitest tests pass with none skipped (`server/build/test-results/vitest/TEST-vitest.xml`), and `SessionTimeoutTest` passes with 1 test, 0 skipped (`server/build/test-results/test/TEST-us.pochaev.dfs.server.SessionTimeoutTest.xml`).
- The 7 story tests are all in `server/frontend/src/app/session/session-ended.spec.ts`: 3 for AC-2, 2 for AC-3, 2 for AC-4.
- `./gradlew :server:e2eTest` passes: `BrowserFlowTest` ran 8 tests, 0 failures, 0 skipped (`server/build/test-results/e2eTest/`).
- The deterministic gate passed, as the orchestrator reported. Every changed file is inside `touches` or the story folder (checked with `git diff 672693c HEAD --stat`).
- By-hand check on the built `dfs-server.jar`:
  - The token link sets `DFS_SESSION` as a browser-session cookie: no `Max-Age`, `HttpOnly`, `SameSite=Strict`.
  - With that cookie, `/api/session` answered `{"authenticated":true}` and `/api/scans` answered 200.
  - After a restart, the old cookie got 403 on both.
  - Not done: reading the notice in a real browser's DOM, and the 30-minute idle reload. The notice's DOM text is covered by the AC-3 Vitest tests, and the "never" timeout by `SessionTimeoutTest`.

### How it went

- **Design:** 1 attempt.
- **Tests:** 2 runs.
  - The first stopped at the red gate because AC-1 can only fail on an assertion: a property cannot be stubbed. You accepted this as an exception.
  - The phase was reopened once (defect 2) to flush the first `GET /api/scans` in three specs. This was a test defect: `http.verify()` found the request still open.
- **Implement:** 2 attempts. The first was held at the gate by defect 2. The second kept attempt 1's code unchanged, and the gate passed.
- No escalations.

Ready for your approval. Before a future screen sends user-typed paths, consider a follow-up that ignores 403s with a `code` in the body.

## Summary

- **DFS-066.AC-1** met: `server.servlet.session.timeout=0`. `SessionTimeoutTest` reads the live Tomcat Context's timeout and asserts it is 0 or less. At the red gate it failed on 30, so it is not vacuous.
- **DFS-066.AC-2** met: `sessionEndedInterceptor` plus `SessionEnded`, and `App.showNotice()` swaps out `<router-outlet>`. Three shell-level tests cover the scan list GET, the sets page, and POST /api/scans.
- **DFS-066.AC-3** met: `disconnected` (a 403, or authenticated false) shows the notice. `expectNone('/api/scans')` asserts no scan call.
- **DFS-066.AC-4** met: the interceptor rethrows unchanged and `isSessionEnded` is 403-only. The 404 UNKNOWN_SCAN and 500 tests assert each screen's own text and no `.session-ended`.
- **Design:** matches `design.md`, including the checklist dispositions.
- **Concern, not a defect against the ACs:** `403 PATH_OUTSIDE_ROOTS` would also be read as "session ended".
- **Merge risk:** DFS-065 is editing `app.html` on `main`.
- **Done-items:** 4 of 5 ticked. The by-hand idle/restart item is left unticked: the server-side restart was checked, the browser DOM and the 30-minute idle were not.
- **Verdict:** ready for the human gate. No phase needs to be rerun.

## Criteria

### DFS-066.AC-1: met

`application.properties` sets `server.servlet.session.timeout=0`. Spring Boot's Tomcat factory maps a zero or negative duration to `Context.setSessionTimeout(0)`, and Tomcat's `StandardSession` expires a session only when `maxInactiveInterval > 0`. `SessionTimeoutTest` starts `Main` with a real Tomcat, finds the Context and asserts `getSessionTimeout() <= 0`. This proves the effective value, as decision 2 asks. plan.json records that it failed on an assertion before the property was set. The user accepted the red-gate exception for this test.

### DFS-066.AC-2: met

`isSessionEnded` checks for an `HttpErrorResponse` with status 403 whose URL path starts with `/api/`. It parses absolute URLs, so it works for the production `responseURL` form. The interceptor marks the root `SessionEnded` signal and rethrows the failure. `App.showNotice()` returns `connection() === 'disconnected' || ended()`, and the template's `@if` replaces the toolbar's "Connected" line and the `<router-outlet>`.

The three tests render the shell with the real routes and the interceptor, flush a 403 on each scenario's call, and assert three things: the exact notice text, `role="alert"`, and that `app-scan-screen` or `app-result-screen` is gone. The scan-list test also checks for no "Start" and no "Connected".

### DFS-066.AC-3: met

`it.each` covers a 403 from `/api/session` and a 200 with `authenticated: false`. Each case asserts the exact notice, no "Connected" or "Start", and `http.expectNone('/api/scans')`. The old `<p>Open the link printed in the console</p>` is removed from `app.html`.

### DFS-066.AC-4: met

The interceptor never swallows or replaces a failure. The 404 test uses `refuse(..., 404, UNKNOWN_SCAN)` and asserts the result screen's unknown-scan text is shown and `.session-ended` is absent. The 500 test does the same on `/folders` with the generic text.

## Test quality

- **Behaviour, not implementation.** The tests drive real HTTP traffic through `HttpTestingController` and the real interceptor and routes, and assert on rendered DOM text and element presence. No test calls `isSessionEnded` or `markEnded` directly. Each test asserts the visible result, so none of them restates the code.
- **Non-403 tests.** AC-4 has both a negative assertion (no notice) and a positive one (the screen's own message), so a regression that shows both would fail.
- **AC-1.** The test inspects the running container, not `ServerProperties`, so a mistyped property name would fail it.
- **Minor gaps.** No unit test for `isSessionEnded` covers a 403 outside `/api/` or an absolute URL. The `/api/` prefix rule is untested, and only relative URLs are exercised. These are not required by any scenario.
- **Scenario wording.** The first AC-2 test answers the first `GET /api/scans` with 403, not a later poll as "the next" might suggest. The behaviour is the same.

## Design and invariants

- **Design.** The diff matches `design.md`'s component table, page states and checklist dispositions:
  - no API call was added and `src/app/api/` is untouched;
  - no Material module was added;
  - the notice is in the shell.
- **Item 13 (docs).** Explicitly deferred to `done`, since the files are outside `touches`.
- **Safety invariants.** Not touched by this ticket: no plan, deletion, scan walking, hashing or hard-link code changed. Preview stays a pure query.
- **Security design (DFS-036).**
  - Unchanged apart from the timeout: the cookie is still `HttpOnly` and `SameSite=Strict` and has no `Max-Age`. Checked on the jar.
  - The Host and Origin filter and the token exchange are untouched.
  - A session that never expires is in line with decision 1: one user and localhost only.
- **Concern.** The server sends `403 PATH_OUTSIDE_ROOTS` from the result, overlaps and plan APIs, and the interceptor would read it as "session ended". It cannot be reached from today's UI, but it can be from a future input screen. AC-2's wording ("any `/api/**` call") asks for exactly this, so it is not a defect against the ticket.

## Done-items

- [x] Every scenario is an automated test and passes: 7 Vitest and 1 JUnit, none skipped.
- [x] `./gradlew build` is green. Re-run by the verifier: up to date and green.
- [x] No source files are outside Touches (`git diff 672693c HEAD --stat`).
- [x] `:server:npmTest` (279/279) and `:server:e2eTest` (8/8, 0 skipped) pass. e2eTest was run by the verifier.
- [ ] By hand: not fully done.
  - Done: the server-side restart check on the jar. After a restart the old cookie gets 403 on `/api/session` and `/api/scans`, and the cookie has no expiry.
  - Not done: the browser DOM read after the restart, and the idle reload past 30 minutes. A "never" timeout cannot be shortened for the check without testing a different value.
