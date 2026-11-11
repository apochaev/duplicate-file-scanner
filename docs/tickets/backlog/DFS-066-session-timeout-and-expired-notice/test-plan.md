# DFS-066: Keep the login session for a day and explain an expired one — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`it` name, inside its `describe`) | Spec file | Stub it reaches | Level |
|---|---|---|---|---|---|
| `DFS-066.AC-1` | The running server's sessions never expire from idleness | `DFS-066.AC-1 The running server's sessions never expire from idleness` (JUnit) | `server/src/test/java/us/pochaev/dfs/server/SessionTimeoutTest.java` | none (property only; fails on an assertion, 30 minutes) | Java, running Tomcat |
| `DFS-066.AC-2` | The scan list's 403 ends the session screen | `DFS-066.AC-2 replaces the scan screen with the notice when GET /api/scans answers 403` | `src/app/session/session-ended.spec.ts` | `App.showNotice` | component (TestBed, shell with real routes and interceptor) |
| `DFS-066.AC-2` | A 403 while a result is showing ends it too | `DFS-066.AC-2 replaces the result screen with the notice when a page of sets answers 403` | same | `App.showNotice` | component |
| `DFS-066.AC-2` | A 403 on starting a scan ends the session screen | `DFS-066.AC-2 replaces the scan screen with the notice when POST /api/scans answers 403` | same | `App.showNotice` | component |
| `DFS-066.AC-3` | A reload after the session ended shows the notice and calls nothing else | `DFS-066.AC-3 shows the notice and calls no scan endpoint when /api/session answers 403` | same | `App.showNotice` | component |
| `DFS-066.AC-3` | A session reported as not authenticated shows the same notice | `DFS-066.AC-3 shows the notice and calls no scan endpoint when /api/session answers 200 authenticated false` | same | `App.showNotice` | component |
| `DFS-066.AC-4` | An unknown scan keeps its own message | `DFS-066.AC-4 keeps the unknown scan message for a 404 UNKNOWN_SCAN and shows no notice` | same | `App.showNotice` | component |
| `DFS-066.AC-4` | A server error keeps its generic message | `DFS-066.AC-4 keeps the generic message for a 500 and shows no notice` | same | `App.showNotice` | component |

## Fixtures and fakes

- HTTP: `provideHttpClient(withInterceptors([sessionEndedInterceptor]))`, `provideHttpClientTesting()`; `http.verify()` after each test that did not already fail.
- Stub surfacing: `provideStubSurfacing()`, `await settle(fixture)` after each step.
- Fakes behind tokens: `CLIPBOARD` null, `EVENT_SOURCE` inert.
- Data builders: `scanEntry`, `setPage`, `duplicateSet`, `expectScan/Folders/Sets`, `UNKNOWN_SCAN` from `results/result-test-data.ts`.

## Notes for the implementer

- Tests render the shell `App` with the real routes (a stub in the interceptor alone would be swallowed by a screen's catch).
- The notice is found as `.session-ended` (`role="alert"`) with exact text "Your session ended, or the server was restarted. Open the link printed in the console."
- Existing `app.spec.ts` and `app.routes.spec.ts` are unchanged; they still fail on the stub until `showNotice` is implemented, and their text assertions still hold.
- `SessionTimeoutTest` fails on its assertion until `server.servlet.session.timeout` is set to zero or negative.
