# DFS-066 progress

## design (architect)

- Wrote `design.md` (Summary, Checklist disposition, Expected breakage).
- Added stubs: `server/frontend/src/app/session/session-ended.ts` (`isSessionEnded`, `SessionEnded.markEnded`), `session-ended.interceptor.ts` (`sessionEndedInterceptor`), `App.showNotice()` in `app.ts`; changed `app.html` (notice `.session-ended`) and `app.config.ts` (`withInterceptors`). Every stub throws `not implemented: DFS-066`.
- `./gradlew :server:ngBuild` compiles (initial total 705.93 kB, no budget warning). `:server:npmTest` once: 8 failures in `app.spec.ts` and `app.routes.spec.ts`, declared under Expected breakage.
- AC-1 (session timeout property) has no stub possible: flagged in `design.md` and `learnings.md`.
- Files to commit: the three new/changed frontend files above plus `app.html`, `app.config.ts`, `app.ts`, `design.md`, `progress.md`, `learnings.md`.

## test (test-author)

- Wrote `server/frontend/src/app/session/session-ended.spec.ts` (7 tests, AC-2 to AC-4, all fail with `not implemented: DFS-066`) and `server/src/test/java/us/pochaev/dfs/server/SessionTimeoutTest.java` (AC-1, fails on an assertion: no stub possible), and `test-plan.md`.
- Existing app specs left unchanged. Expected commit: the two test files plus test-plan.md and progress.md.

## implement (implementer)

- Implemented `isSessionEnded` (403 `HttpErrorResponse` under `/api/`), `SessionEnded.markEnded`, `App.showNotice` (disconnected or ended); removed the stub notes; added `server.servlet.session.timeout=0` to `application.properties`.
- SessionTimeoutTest passes; `:server:npmTest` 276 of 279 pass. 3 tests in `session-ended.spec.ts` (AC-2 result screen, both AC-4) fail in `afterEach` `http.verify()`: "found 1 open request: GET /api/scans". The shell renders the scan screen at `#/` as soon as `/api/session` answers, which requests `/api/scans`; those tests navigate away without flushing it. Reported as a test defect, tests not edited.
