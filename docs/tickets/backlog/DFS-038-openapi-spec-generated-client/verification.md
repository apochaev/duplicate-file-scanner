# DFS-038 verification

## Product owner review

### What you can do now

The page reads `/api/session` through a client generated from the API's OpenAPI spec, so the frontend and server types cannot drift. Here is the workflow for any API change from now on:
1. change the controller
2. run `./gradlew :server:updateOpenApi`
3. commit `server/frontend/openapi.json`, which shows the API change as a readable diff
4. fix whatever no longer compiles in the frontend

Forgetting step 2 fails the build with a message naming the command. A renamed field fails the frontend compile at the exact line that uses it. The page looks the same as after DFS-037, and the spec is never served by the running server.

### Acceptance criteria

- **AC-1** met (test):
  - the spec has `GET /api/session` with tag `session`, operation ID `getSession`, and a `200` `application/json` response referring to `SessionStatus`, whose `authenticated` is a required boolean
  - the server URL is empty, so it has no port
  - the committed `openapi.json` is JSON-equal to the live spec
- **AC-2** met (test + one run):
  - the test shows that a mismatch fails with a message naming `./gradlew :server:updateOpenApi`
  - one run: with a line deleted from the committed spec, `./gradlew build` failed in `OpenApiSpecTest` with "...is not the spec the controllers produce; run ./gradlew :server:updateOpenApi and commit the file"
  - running that task restored the file byte for byte (`git diff` empty), and the build was green
  - see "Deviation" below for which line was deleted
- **AC-3** met (test): on a normal run, `/v3/api-docs` answers `404` with or without the cookie, and `/api/session` still answers `{"authenticated":true}` with the cookie.
- **AC-4** met (one run, after a fix found while running it):
  - with `src/app/api/` deleted, `./gradlew :server:bootJar` regenerated `services/session.service.ts` (with `getSession`) and `models/session-status.ts`
  - `git status` listed nothing under `src/app/api/`
  - a second `processResources` reported `npmInstall` and `ngBuild` `UP-TO-DATE`, also right after `npmTest` had regenerated the same files
- **AC-5** met (3 Vitest tests + search):
  - `{"authenticated":true}` shows "Connected"
  - `403` shows the console message
  - `{"authenticated":false}` shows the console message and not "Connected"
  - the search for `HttpClient` and `/api/` under `server/frontend/src`, excluding generated and test files, finds only `provideHttpClient()` in `app.config.ts`, which registers the HTTP client the generated service uses
- **AC-6** met (one run): with `SessionStatus.authenticated` renamed to `signedIn` and the spec updated, `./gradlew build` failed in `ngBuild` with `TS2339: Property 'authenticated' does not exist on type 'SessionStatus'` at `app.ts:24`. Both changes were reverted and the build was green.
- **AC-7** met (read):
  - `CLAUDE.md`'s `server` entry describes springdoc, the committed spec, `OpenApiSpecTest`, the update command, the generated folder and the workflow for changing an endpoint
  - its Commands list `./gradlew :server:updateOpenApi`
  - `ui.md` D2 names springdoc 3.2.0, `ng-openapi-gen` 1.1.0 and the committed, unserved spec

### Try it

```
$env:JAVA_HOME = "C:\Users\apoch\.jdks\temurin-25.0.4.1"
cd ..\duplicate-file-scanner-DFS-038-openapi-spec-generated-client
.\gradlew.bat :server:installBootDist
server\build\install\dfs-server-boot\bin\dfs-server.bat
```

The tab shows "DFS server is running" and "Connected". Change `?token=...` in the address to `v3/api-docs`: it answers with an error page (404), meaning no spec is served. Read the spec in `server\frontend\openapi.json` instead. Stop with Ctrl+C.

### Decided without you

- **Generator option `--services true`:** `ng-openapi-gen` 1.1.0 by default writes only functions plus a generic `Api` helper. With the option, it writes `SessionService.getSession()`, as decision 5 named. Its methods return Promises.
- **Empty server URL in the spec**, rather than `/`. With `/`, the generated client would have called `//api/session`, which a browser reads as a host named `api`.
- **The spec covers only `/api/**`** (`springdoc.paths-to-match`). Without that, it also described the token page `/`.
- **The endpoint declares `application/json`**, so the spec and the client say JSON instead of `*/*`.
- **The Vitest render helper waits one tick** for the generated service's Promise before reading the page. This changes the shared helper, not any assertion.
- **A missing generated client reruns the frontend build:** `src/app/api/` is declared as a second output of `ngBuild` (`7bf54d9`; see Deviation).

### Known limits

- **`updateOpenApi` needs a valid `openapi.json` to start from:** it runs the test classpath, which includes the frontend build, and that build generates the client from the current file. Restore a broken or deleted file from git first.
- **Changes in the token page `/` are not in the spec**, by design (it isn't part of the API).
- **The generated client is Promise-based.** Later screens that need streams (SSE progress, D2) will use their own code or the generated functions directly; that's for the scan-screen ticket to decide.

### Deviation

- **AC-2's scenario** says the stale spec "lacks the `authenticated` property". A spec like that fails the build earlier, in the frontend compile, because the page uses that field (AC-6's mechanism), so `OpenApiSpecTest` never runs. The run therefore deleted the operation's `summary` line instead, a stale spec that still compiles. The criterion itself ("differs from the live one ... fails in `OpenApiSpecTest`") held. The automated AC-2 test does drop `authenticated`, since it calls the comparison directly.
- **AC-4** failed when first run: excluding `src/app/api/**` from the task inputs, without declaring it as an output, meant a deleted folder was not regenerated. The jar was still correct, but an IDE would see missing imports. Fixed in `7bf54d9`, then the run was repeated and passed.

### Evidence

- Red run before the implementation (`7812289`):
  - Java: `OpenApiSpecTest` had 3 tests, 2 failed, because `/v3/api-docs` answered `404` with springdoc absent. AC-3's test passed vacuously, since no spec was served yet.
  - Vitest: 3 tests, 1 failed: the `{"authenticated":false}` case showed "Connected".
- Green:
  - `81e16d5` is the implementation, `7bf54d9` the AC-4 fix, `99e3486` the docs
  - `./gradlew build` green
  - `:server:test --rerun`: 35 JUnit tests, 0 failed, 0 skipped
  - `:server:npmTest --rerun`: 3 Vitest tests passed
- Manual run from `installBootDist` (port 60027, checked with `curl`):
  - page `200`
  - `/v3/api-docs` `404` without and with the cookie
  - `/api/session` `{"authenticated":true}` as `application/json`
  - no warnings in the log
- Scope: `git diff --name-only main...HEAD` lists only paths under Touches.

### How it went

Implemented by hand in one session, tests first. These were found and fixed along the way:
- the `//api` root URL
- the token page appearing in the spec
- the generator's default of functions only
- the Promise timing in the Vitest helper
- the AC-4 regeneration gap

## Summary

7 criteria met:
- by automated tests: AC-1, AC-2, AC-3 and AC-5 (35 JUnit and 3 Vitest tests)
- by one recorded run each: AC-2, AC-4 and AC-6
- by search and reading: AC-5 and AC-7

Two deviations are recorded: AC-2's scenario wording, and AC-4, which was fixed and re-run. Tests ran red before the implementation and green after it. Build green, scope respected. Still yours: the browser check in "Try it".
