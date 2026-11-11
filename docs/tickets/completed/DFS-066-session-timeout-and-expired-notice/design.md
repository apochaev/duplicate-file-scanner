# DFS-066: Keep the login session for a day and explain an expired one — design

## Summary

- AC-1 (session never expires from idleness): one line in `server/src/main/resources/application.properties`, `server.servlet.session.timeout` zero or negative (decision 1); proven by one Java test that reads Tomcat's `Context.getSessionTimeout()` from a running server (decision 2). There is no stub for it (a property cannot throw), so that test will fail on an assertion, not on `not implemented: DFS-066`: flagged for the red gate.
- AC-2 (a 403 from `/api/**` replaces the screen with the notice): `sessionEndedInterceptor` (new, `src/app/session/`, registered in `app.config.ts` through `withInterceptors`) calls `isSessionEnded(failure)` and `SessionEnded.markEnded()`; the shell `App` reads `SessionEnded.ended()` through `showNotice()` and swaps `<router-outlet>` for `<p class="session-ended" role="alert">`. Reached through stubs: `App.showNotice`, `isSessionEnded`, `SessionEnded.markEnded`.
- AC-3 (no session at load shows the same notice, no scan call): the shell's `disconnected` case now goes through `showNotice()` too, in place of "Open the link printed in the console"; the routed screen is still not rendered, so no `/api/scans` call. Stub: `App.showNotice`.
- AC-4 (a non-403 failure keeps its own message): `isSessionEnded` is true only for a 403 under `/api/`; the interceptor rethrows every failure unchanged, so `result-error.ts` and the screens are untouched. Stub: `isSessionEnded`.
- Package placement: new folder `server/frontend/src/app/session/` (`session-ended.ts`, `session-ended.interceptor.ts`); changed `app.ts`, `app.html`, `app.config.ts`. No API call added, `src/app/api/` untouched, no Material module added (initial total 705.93 kB, no budget warning).
- Test-author note: an interceptor stub failure is swallowed by a screen's own `catch` (it shows its generic message), so tests for AC-2 and AC-4 must render the shell `App` with the real routes and `provideHttpClient(withInterceptors([sessionEndedInterceptor]))`: the shell's template evaluates the stubbed `showNotice()` on every render, which `provideStubSurfacing()` and `settle(fixture)` surface.
- Checklist items marked not applicable: 4 (no browser API), 6 (no input), 8 (no stream; a failed `EventSource` is out of scope per decision 3), 9 (no Material component added), 12 (no style iteration needed).
- Expected breakage: `app.spec.ts` and `app.routes.spec.ts` (the shell's template now calls the stubbed `showNotice()`); `BrowserFlowTest` (e2e only, not run by `check`).
- Tests read for the design: No tests read (the red Vitest run showed which existing specs break).

## Components and files

| File | Kind | Inputs / outputs | Injects | Purpose |
|---|---|---|---|---|
| `src/app/session/session-ended.ts` | helper + service | none | none | `SESSION_ENDED_NOTICE` text; `isSessionEnded(failure)` (stub); `SessionEnded` root service with `ended` signal and `markEnded()` (stub) |
| `src/app/session/session-ended.interceptor.ts` | functional `HttpInterceptorFn` | none | `SessionEnded` | on a failed call: `isSessionEnded` then `markEnded`, then rethrows the same failure |
| `src/app/app.config.ts` | config | none | none | `provideHttpClient(withInterceptors([sessionEndedInterceptor]))` |
| `src/app/app.ts` | component (shell) | none | `SessionService`, `SessionEnded` | adds `notice`, `sessionEnded` and `showNotice()` (stub) |
| `src/app/app.html` | template | none | none | notice `<p class="session-ended" role="alert">` in place of the routed screen and of the old "Open the link" paragraph |
| `server/src/main/resources/application.properties` | config | none | none | `server.servlet.session.timeout` (implementer; no stub possible) |
| `server/src/test/java/us/pochaev/dfs/server/` (a new test class, test-author) | test | none | none | AC-1: starts the server as `ServerApplicationTest` does and asserts the effective session timeout is zero or negative |

## Page states

- Not connected (no session at load, `/api/session` rejected or `authenticated: false`): `Your session ended, or the server was restarted. Open the link printed in the console.` in `.session-ended` (`role="alert"`); no toolbar change, no routed screen, no `/api/scans` call.
- Checking (before `/api/session` answers): main is empty, as before.
- Loaded (connected, no 403 yet): "Connected" and the routed screen, as before.
- Session ended during use (any `/api/**` call answered 403): the same notice replaces "Connected" and the routed screen (scan list and Start button, or the result screen).
- Loading, Empty, Input refused, Server refusal (`400`), Server busy (`503`), Load failed (`404 UNKNOWN_SCAN`, `500`): not applicable to the shell; each screen keeps its own text and the session notice is not shown for them (AC-4).
- Live updates: not applicable (a failed `EventSource` carries no status; a live scan keeps the session alive).

## API calls

None added. The interceptor observes every call made through `HttpClient` (the generated client's, and `EventSource` is not one) and reacts only to a `403` whose URL starts with `/api/`. `SessionService.getSession()` is unchanged: its 403 or `authenticated: false` both end in the shell's `disconnected`, now showing the notice.

## Browser APIs behind wrappers

None.

## Material components

No new Material component; `MatToolbarModule` stays. The initial total after the stubs compile is 705.93 kB (the result screen is lazy); `ngBuild` prints no budget warning. No earlier figure was measured, and none is needed: no module was added.

## Checklist disposition

1. The generated client: not applicable beyond `SessionService.getSession()`, unchanged; no call added, `src/app/api/` untouched. The interceptor sits on `HttpClient`, under the generated services.
2. Promise timing in tests: specs use `settle(fixture)` after each step; the 403 flows through the generated client's Promise, so a step that flushes a 403 is followed by `settle`.
3. Stubs reachable and surfaced: AC-2, AC-3 and AC-4 all go through `App.showNotice()`, called from the template on every render, so `provideStubSurfacing()` + `settle` surface it; `isSessionEnded` and `markEnded` are the narrower stubs behind it. They run in an RxJS `catchError` callback, so their error alone would become a failed HTTP call that a screen's own `catch` swallows (it shows its generic message): story tests for AC-2 and AC-4 therefore render `App`. After the stubs compiled, `:server:npmTest` ran once: 8 existing tests fail (see Expected breakage), 2 of them with the stub's message in the report and 6 with a consequence of it.
4. Browser APIs jsdom lacks: not applicable: no browser API is used.
5. Where the screen sits and when it shows: the notice lives in the shell (`App`), shown instead of the routed screen when `showNotice()` is true; it needs no route or link.
6. Input and validation: not applicable: no input.
7. Loading, empty and error states: the notice is the new `403` state (`role="alert"`, class `session-ended`); every other failure is passed on unchanged so each screen keeps its message (AC-4).
8. Live updates: not applicable: no stream added; the `EventSource` failure case is out of scope (ticket decision 3).
9. Material components and their harnesses: not applicable: no Material component added.
10. The bundle budget and the system font: no new dependency; initial total 705.93 kB, no budget warning; text uses the existing typography.
11. The live check reads the DOM: by hand once on the built jar, as the ticket's last done-item says: reload after restarting the server, read `.session-ended`'s text from the DOM; leave a finished scan idle (or shorten the timeout) and reload.
12. The dev server and its proxy: not applicable: no style iteration; the check above covers it.
13. Docs: the `CLAUDE.md` `server` entry should say that a 403 from `/api/**` or a page opened without a session shows one "session ended" notice, and that the session never expires from idleness (`server.servlet.session.timeout`); `docs/design/ui.md` and DFS-036's session text, if it names 30 minutes, to match. These files are outside `touches`; the closing step (`done`) handles them.

## Expected breakage

- `app.spec.ts`: the shell's template calls the stubbed `showNotice()` on every render, so every existing test of the shell fails (first with `not implemented: DFS-066`, the rest of that file as a consequence); the test-author rewrites them for the notice text.
- `app.routes.spec.ts`: it renders `App` with the real routes, so it reaches the same stub.
- `BrowserFlowTest`: end-to-end only (`./gradlew :server:e2eTest`, not part of `check`); every flow loads the page, which renders the shell, so each fails on the stub until the implementer replaces it.

## Open design notes (as of the design phase)

- AC-1 has no stub: the change is a property in `application.properties`, which cannot throw `not implemented`. The Java test for AC-1 will fail the red gate's "fails with the stub error" rule by asserting 30 minutes; the pipeline needs to accept that one test, or the orchestrator decides how (a deliberate exception for this property-only criterion).
- The ticket's title says "a day" but decision 1 chose no idle timeout (zero or negative); the design follows decision 1.
- The new notice text contains "Open the link printed in the console", so the existing `toContain` assertions on it still hold; the tests that fail do so because of the stub, not the text.

## Stubs (as of the design phase)

- Files stubbed: `server/frontend/src/app/session/session-ended.ts` (`isSessionEnded`, `SessionEnded.markEnded`), `server/frontend/src/app/session/session-ended.interceptor.ts` (real pass-through that calls the two stubs), `server/frontend/src/app/app.ts` (`showNotice`), `server/frontend/src/app/app.html` and `server/frontend/src/app/app.config.ts` (final).
- Each stubbed method throws `new Error('not implemented: DFS-066')`; which stub each criterion reaches: AC-1: none (property only, see above); AC-2: `App.showNotice`, `isSessionEnded`, `SessionEnded.markEnded`; AC-3: `App.showNotice`; AC-4: `App.showNotice` (the stubs behind the interceptor are also reached on a non-403 failure).
- The template's structure and CSS classes (`.session-ended`, `.connection`) are final; tests find elements by them.
- Compiled with `./gradlew :server:ngBuild`.
- Commit: not committed by the architect.
