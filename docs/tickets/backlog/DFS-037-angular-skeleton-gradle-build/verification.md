# DFS-037 verification

## Product owner review

### What you can do now

The server's page is now an Angular 22 app with Angular Material, built by `./gradlew build` and packaged into `dfs-server.jar`. It looks like the old page: a toolbar reading "DFS server is running", then "Connected" or "Open the link printed in the console". Gradle downloads its own Node 24.22.2, so no Node install is needed. The Node 18 on this machine's `PATH` is too old for Angular 22 and is never used. For frontend work, the dev server reloads the page on every save and forwards `/api` to a running server.

### Acceptance criteria

- **AC-1** met (test + one clean run):
  - with every `nodejs` folder removed from `PATH` (`command -v node` found nothing) and `server/.gradle/nodejs`, `node_modules`, `dist` and `server/build` deleted, `./gradlew --no-daemon :server:bootJar` downloaded Node 24.22.2, ran `npm ci` and `ng build`, and built the jar
  - the jar holds `static/index.html`, `main-<hash>.js`, `styles-<hash>.css` and `favicon.ico`, which a test checks against `index.html`'s own references
  - `git status` was clean afterwards
- **AC-2** met (2 tests): `GET /` without a cookie returns the Angular `index.html` with `<app-root>` and title `DFS`. Its JS and CSS answer `200` as `text/javascript` and `text/css`. The JS file with `Host: evil.example:<port>` gets `403`.
- **AC-3** met by 2 Vitest tests:
  - `200` shows the heading and "Connected"
  - `403` shows "Open the link printed in the console" and no "Connected"
  - **Browser check still pending: yours** (see "Try it").
- **AC-4** met (one run): a frontend test changed to expect "Disconnected" made `./gradlew build` fail at `:server:npmTest`, which named the test. With the change reverted, the build is green.
- **AC-5** met (one run): the second `./gradlew :server:processResources` reported `npmInstall`, `ngBuild`, `frontendResources` and `processResources` all `UP-TO-DATE`, in 3 s.
- **AC-6** met (one run, without a browser):
  - setup: `bootRun --args=--server.port=8080` and `npm_run_start`, cookie taken from the token link
  - through `http://127.0.0.1:4200`:
    - `/api/session` gave `{"authenticated":true}` with the cookie and `403` without it
    - the page gave `<app-root>` and title `DFS`
  - an edit to the heading rebuilt in 0.3 s ("Changes detected. Rebuilding..."), and `main.js` then served the new text; the edit was reverted
- **AC-7** met:
  - test: `SecurityTest` (DFS-036.AC-3) and `ServerApplicationTest` (DFS-035.AC-4) assert `<app-root>` and `<title>DFS</title>` and pass
  - read: `CLAUDE.md`'s `server` entry describes the Angular app, and its Commands list `npmTest` and the two dev commands with the login order
  - read: `ui.md` U9 names node-gradle 7.1.0, Node 24.22.2, Angular 22.2 and Vitest

### Try it

```
.\gradlew.bat :server:installBootDist
server\build\install\dfs-server-boot\bin\dfs-server.bat
```

A browser tab opens with a blue Material toolbar reading "DFS server is running", and below it "Connected". Copy the address without `?token` into a private window: it says "Open the link printed in the console". Stop with Ctrl+C. This is also the done-item "started by hand once from `installBootDist`", which is still open.

To try hot reload, open two terminals. In the first, run `.\gradlew.bat :server:bootRun --args=--server.port=8080`. In the second, run `.\gradlew.bat :server:npm_run_start`. Open the `Open ...?token=` link from the first terminal, then `http://127.0.0.1:4200`, and edit `server/frontend/src/app/app.html`.

### Decided without you

- **Native install scripts stay blocked.** npm 11 skips package install scripts unless they are allowed: `esbuild`, `lmdb`, `@parcel/watcher` and `msgpackr-extract`. The build, tests and dev server all work without them, since esbuild's binary comes as a prebuilt optional package. Nothing is compiled with `node-gyp`.
- **Router removed.** The scaffold's `@angular/router` was dropped, since there's no routing yet (decision 6). The first screen with a second view adds it back.
- **Theme:** Material's prebuilt `azure-blue` theme with the system font, so no font is fetched from the internet.
- **The Vitest tests use `App` itself** rather than a separate status component. The page is one component, and a split can come with the first real screen.
- **`npmTest` writes a marker file** (`server/build/frontend-test/passed`), so an unchanged frontend skips its tests like any other up-to-date Gradle task.

### Known limits

- **The first build needs the network:** about 1 minute for the Node download plus `npm ci`. Later builds use the cache under `server/.gradle/` and `server/frontend/node_modules`.
- **`bootRun` and `ng serve` outlive their Gradle tasks on Windows.** After the AC-6 run, both still listened (ports 8080 and 4200) and were killed by PID. Ctrl+C in your own terminal stops them, but a background-stopped Gradle task does not.
- **No `Origin` rewrite in the dev proxy:** a `POST` through port 4200 would get `403`. The first ticket with a state-changing endpoint decides how to handle it (decision 4).
- **The worktree's test reports keep long paths:** removing this worktree may again hit "Filename too long" (as on DFS-036).

### Evidence

- Red runs before the implementation:
  - Java: `3c7c5e3`, 32 tests, 5 failed: the 3 new `FrontendTest` tests, plus the two changed page assertions. The old static page has no `<app-root>` and references no JS.
  - Vitest: `487f7e2`, against a stub `App`, 2 tests, 2 failed ("Expected one matching request for criteria "Match URL: /api/session", found none").
- Green:
  - `0266624` is the implementation, `ad661a7` the docs
  - `./gradlew build` green
  - `:server:test --rerun`: 32 JUnit tests, 0 failed
  - `:server:npmTest`: 2 Vitest tests passed
- Scope: `git diff --name-only main...HEAD` lists only paths under Touches.

### How it went

Implemented by hand in one session, tests first, with no failures after the first full build. Two adjustments were needed along the way:
- the scaffold's router import had to go before the stub compiled
- writing over generated files with the editor first required reading them

## Summary

7 criteria met:
- by automated tests: AC-1, AC-2, AC-3 and AC-7 (32 JUnit and 2 Vitest tests)
- by one recorded run each: AC-1's clean build, AC-4, AC-5 and AC-6
- by reading: AC-7's docs

Still open, and yours: the browser check of AC-3 and the `installBootDist` start ("Try it"). Tests ran red before the implementation and green after it. Build green, scope respected.
