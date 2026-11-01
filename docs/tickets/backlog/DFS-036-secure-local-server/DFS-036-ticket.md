---
id: DFS-036
title: Secure the local server against other pages in the browser
status: in-review
type: feature
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-035]
touches:
  - server/build.gradle.kts
  - server/src/main/java/us/pochaev/dfs/server
  - server/src/main/resources/application.properties
  - server/src/main/resources/static/index.html
  - server/src/test/java/us/pochaev/dfs/server
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-10-31
updated: 2026-10-31
completed:
---

# DFS-036: Secure the local server against other pages in the browser

## Story

As the user of the duplicate-file-scanner
I want the local server to answer only my own browser tab, opened from the link it prints
So that no other web page I visit can read my folders or change a deletion plan through it

## Context

- `docs/design/ui.md` U10 and "Suggested ticket order" item 3. A server on localhost is reachable from any page in the user's browser: cross-site request forgery, and with DNS rebinding a page can also read responses. U10 requires, built with Spring Security, as the first server ticket before any endpoint that reads the disk:
  - listen on `127.0.0.1` only, on a random port (done by DFS-035);
  - at startup generate a random token, print `http://127.0.0.1:<port>/?token=…` and open it in the browser; the page exchanges the token for an `HttpOnly`, `SameSite=Strict` session cookie and removes it from the URL; every API request needs the cookie;
  - reject requests whose `Host` is not `127.0.0.1:<port>` or `localhost:<port>` (DNS rebinding);
  - state-changing requests need a matching `Origin`; no CORS;
  - folder listings, previews, file contents and plan entries only for paths inside the scan's roots.
- D6: MockMvc/WebTestClient tests per endpoint, including missing token, wrong `Host`, wrong `Origin` → 403, and a path outside the roots → 403.
- DFS-035 (in review) built the skeleton: `server` subproject, `Main`, `StartupUrlLogger` logging `DFS server listening on http://127.0.0.1:<port>/`, a static `index.html`, no `/api`. Its tests (`ServerApplicationTest`, `LaunchScriptTest`) assert that line and `GET /` returning 200; this ticket changes both, so they are named in Touches once refined (DFS-030.L-2).
- The path-inside-roots rule needs scans, which do not exist in `server` yet (the scan screen is `ui.md` ticket 5); see decision 1.

## Decisions

1. **The path-inside-roots rule is deferred to the first endpoint that takes a path** (2026-10-31). This ticket builds the four request-level rules (loopback is already done by DFS-035; token-to-cookie exchange, `Host` check, `Origin` check). The first ticket with an endpoint that takes a path (the folder view, `ui.md` ticket 8, or earlier) carries the criterion "a path outside the scan's roots → 403"; `docs/design/ui.md` U10 records that.
2. **Only `/api/**` needs the cookie; the page is public** (2026-10-31). `GET /` and the static files are served to any request that passes the `Host` check: they carry no data. Without the cookie every `/api/**` request gets 403, and the page then shows "Open the link printed in the console". This keeps Angular dev mode (U9, `ng serve` proxying `/api`) simple.
3. **Server-side token exchange by redirect, plus `GET /api/session`** (2026-10-31). `GET /?token=<t>` with the valid token sets the `HttpOnly`, `SameSite=Strict` session cookie and answers `302 Location: /`, so the token leaves the address bar before any script runs; no JavaScript is involved, and the flow survives the switch to Angular. `GET /api/session` returns `{"authenticated": true}` with the cookie and 403 without it; the placeholder page calls it to show "Connected" or "Open the link printed in the console". `docs/design/ui.md` U10's "the page exchanges the token" is reworded to the redirect.
4. **The token lives for the whole server run and is reusable** (2026-10-31). Each visit with the right token gets a fresh session; the token dies when the server stops (a restart prints a new port and token). Other web pages, the threat U10 targets, can never see it. The comparison is constant-time, and the token is logged nowhere except the one printed link.
5. **The server opens the browser by default, behind a switch** (2026-10-31). Property `dfs.server.open-browser` (default `true`; `false` in tests). A `BrowserOpener` interface takes the link; the real one uses `java.awt.Desktop.browse` where supported and falls back to the OS command (`rundll32 url.dll,FileProtocolHandler` on Windows, `open` on macOS, `xdg-open` elsewhere). A failure to open logs a warning and leaves the printed link working. Tests check the link handed to a fake opener; the real opener is checked by hand once on Windows.
6. **`archetype: none`, tests written first** (2026-10-31). No archetype fits server security code yet. The tests are written from the scenarios and run red before the implementation; `verification.md` records the red run and the commit it preceded. D7's endpoint archetype is drafted after this ticket, from DFS-035, DFS-036 and the first data endpoint.
7. **Implementation shape** (2026-10-31, settled while writing the criteria). Spring Security with session-based authentication: the token exchange stores an authenticated `SecurityContext` in a new session (new session ID, against fixation), cookie `DFS_SESSION` (`HttpOnly`, `SameSite=Strict`, `Path=/`, no `Secure`: plain http on loopback). Spring Security's CSRF token is turned off; the `Origin` check plus `SameSite=Strict` replace it (U10). The `Host` and `Origin` checks are one filter ahead of the security chain, so they also cover `GET /`. The DFS-035 listening line stays as it is; the link with the token is a second line.
8. **DFS-035 tests that change** (2026-10-31). `ServerApplicationTest`'s `GET /api/anything` → 404 (DFS-035.AC-4) becomes 403 without a cookie, and moves to this ticket's AC-4 test; DFS-035.AC-4's test keeps only `GET /` → 200. Every test that starts the server passes `--dfs.server.open-browser=false`, and `LaunchScriptTest` sets `DFS_SERVER_OPENBROWSER=false` in the script's environment, so no test opens a browser tab.

## Acceptance criteria

The full ID of each criterion is `DFS-036.AC-<n>`.

- **AC-1** At startup the server generates a random token (at least 128 bits, URL-safe) and logs one line `Open http://127.0.0.1:<port>/?token=<token>`, the only place the token appears; the DFS-035 line `DFS server listening on http://127.0.0.1:<port>/` is unchanged.
- **AC-2** `GET /?token=<the token>` answers `302` with `Location: /` and sets cookie `DFS_SESSION` with `HttpOnly` and `SameSite=Strict`; a second visit with the same token during the same run also gets `302` and a cookie for a new session.
- **AC-3** `GET /?token=<anything else>` answers `403` and sets no `DFS_SESSION` cookie; `GET /` without a token answers `200` with the placeholder page.
- **AC-4** `GET /api/session` answers `200` with `{"authenticated":true}` given the cookie from AC-2, and `403` without a cookie or with a cookie from an earlier server run; any other `/api/**` path without the cookie answers `403`.
- **AC-5** A request whose `Host` is not `127.0.0.1:<port>` or `localhost:<port>` (for example `evil.example:<port>`, `127.0.0.1:<another port>`, or no `Host`) answers `403`, for `GET /` and for `/api/session` with a valid cookie alike.
- **AC-6** A `POST`, `PUT`, `PATCH` or `DELETE` request with no `Origin`, or with an `Origin` other than `http://127.0.0.1:<port>` or `http://localhost:<port>`, answers `403` even with a valid cookie; with a matching `Origin` and the cookie, `POST /api/session` gets past security (`405`). No response, including an `OPTIONS` preflight, carries an `Access-Control-Allow-Origin` header.
- **AC-7** With `dfs.server.open-browser=true` the server hands the exact AC-1 link to its `BrowserOpener` once after startup; with `false` it does not call it; an opener that throws makes the server log one warning and keep serving.
- **AC-8** The placeholder page calls `/api/session` and shows "Connected" on `200` and "Open the link printed in the console" on `403`.
- **AC-9** `docs/design/ui.md` U10 describes the redirect exchange and defers the path-inside-roots rule to the first endpoint that takes a path; the `CLAUDE.md` `server` entry names the token link, the cookie and the `Host`/`Origin` checks; DFS-035's tests run with the browser opener off and no longer assert `404` for `/api`.

## Scenarios

Each scenario is an automated test in `server/src/test/java/us/pochaev/dfs/server` against a real server on a random port, except AC-8 (checked by hand in a browser) and AC-9 (checked by reading), each noted in `verification.md`. Requests with a forged `Host` are sent over a plain socket, since `java.net.http.HttpClient` does not let a caller set `Host`.

```gherkin
Feature: Secure the local server

  Background:
    Given the server runs on 127.0.0.1 port P with dfs.server.open-browser=false
    And T is the token in its "Open http://127.0.0.1:P/?token=T" log line

  @AC-1
  Scenario: Startup prints the link with the token
    Then the log holds exactly one line "Open http://127.0.0.1:P/?token=T"
    And T is URL-safe and at least 22 characters (128 bits in base64url)
    And the log still holds "DFS server listening on http://127.0.0.1:P/"
    And T appears in no other log line

  @AC-2
  Scenario: The token is exchanged for a cookie by redirect
    When a client requests GET /?token=T without following redirects
    Then the response is 302 with Location "/"
    And it sets cookie DFS_SESSION with HttpOnly and SameSite=Strict

  @AC-2
  Scenario: The token can be used again in the same run
    Given a client already exchanged T for cookie C1
    When another client requests GET /?token=T
    Then the response is 302 and sets a DFS_SESSION cookie C2 different from C1

  @AC-3
  Scenario: A wrong token gets nothing
    When a client requests GET /?token=wrong-token
    Then the response is 403 and sets no DFS_SESSION cookie

  @AC-3
  Scenario: The page itself is public
    When a client without a cookie requests GET /
    Then the response is 200 and contains "DFS server is running"

  @AC-4
  Scenario: The session endpoint needs the cookie
    Given cookie C from exchanging T
    When a client requests GET /api/session with C
    Then the response is 200 with body {"authenticated":true}
    When a client requests GET /api/session without a cookie
    Then the response is 403
    When a client requests GET /api/anything without a cookie
    Then the response is 403

  @AC-4
  Scenario: A cookie from an earlier run is refused
    Given cookie C was issued by a server that has since stopped
    And a new server runs on port Q
    When a client requests GET /api/session on port Q with C
    Then the response is 403

  @AC-5
  Scenario Outline: A foreign Host is refused
    Given cookie C from exchanging T
    When a client requests <path> with Host "<host>" and cookie C
    Then the response is 403

    Examples:
      | path         | host               |
      | /            | evil.example:P     |
      | /api/session | evil.example:P     |
      | /api/session | 127.0.0.1:1        |
      | /            | (none)             |

  @AC-5
  Scenario: localhost is accepted as Host
    When a client requests GET / with Host "localhost:P"
    Then the response is 200

  @AC-6
  Scenario Outline: A state-changing request needs a matching Origin
    Given cookie C from exchanging T
    When a client sends POST /api/session with cookie C and Origin "<origin>"
    Then the response is <status>

    Examples:
      | origin                 | status |
      | (none)                 | 403    |
      | http://evil.example    | 403    |
      | http://127.0.0.1:1     | 403    |
      | http://127.0.0.1:P     | 405    |
      | http://localhost:P     | 405    |

  @AC-6
  Scenario: No CORS headers
    When a client sends OPTIONS /api/session with Origin "http://evil.example" and Access-Control-Request-Method "POST"
    Then the response has no Access-Control-Allow-Origin header
    And no response in this feature has one

  @AC-7
  Scenario: The browser is opened with the printed link
    Given a fake BrowserOpener and dfs.server.open-browser=true
    When the server starts
    Then the fake opener received exactly one link, equal to the one in the "Open" log line

  @AC-7
  Scenario: The switch keeps the browser closed
    Given a fake BrowserOpener and dfs.server.open-browser=false
    When the server starts
    Then the fake opener received no link

  @AC-7
  Scenario: A failing opener does not stop the server
    Given a BrowserOpener that throws and dfs.server.open-browser=true
    When the server starts
    Then the log holds one warning naming the failure
    And GET / still answers 200

  @AC-8
  Scenario: The page tells the user whether they are connected
    When the user opens the printed link in a browser
    Then the page shows "Connected"
    When the user opens http://127.0.0.1:P/ in a private window
    Then the page shows "Open the link printed in the console"

  @AC-9
  Scenario: Docs and earlier tests follow the new rules
    Then docs/design/ui.md U10 describes the redirect exchange and the deferred path rule
    And CLAUDE.md's server entry names the token link, the cookie and the Host and Origin checks
    And no server test asserts 404 for /api, and every test that starts a server turns the browser opener off
```

## Scope

**Touches:**
- `server/build.gradle.kts`: add `spring-boot-starter-security`.
- `server/src/main/java/us/pochaev/dfs/server`: the security configuration (filter chain, session cookie, CSRF off), the `Host`/`Origin` filter, the token holder and exchange, `SessionController` (`/api/session`), `BrowserOpener` and its default implementation, the startup line with the link.
- `server/src/main/resources/application.properties`: `dfs.server.open-browser=true`, session cookie name and attributes.
- `server/src/main/resources/static/index.html`: calls `/api/session` and shows the result (AC-8).
- `server/src/test/java/us/pochaev/dfs/server`: new security tests; `ServerApplicationTest` and `LaunchScriptTest` adjusted (decision 8).
- `CLAUDE.md`: the `server` entry.
- `docs/design/ui.md`: U10.

**Out of scope:**
- The Angular UI (`ui.md` ticket 4); this ticket's page stays a placeholder.
- Any endpoint that reads the disk.
- The path-inside-roots check (decision 1).
- Users, logins or remote access: one local user only.

## Definition of done

- [x] Every scenario above is an automated test and passes, except AC-8 (by hand) and AC-9 (read), each noted in `verification.md` (29 server tests, 0 failed)
- [x] The tests ran red before the implementation; the failing run and the commit it preceded are recorded in `verification.md` (decision 6) (22 of 29 failed at `59d0203`)
- [x] Each test's `@DisplayName` starts with its criterion ID (`DFS-036.AC-n`)
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] Started by hand once on Windows: the browser opens on the link, the page shows "Connected", and a private window shows "Open the link printed in the console" (confirmed by the product owner, 2026-10-31)
- [x] No files changed outside the paths listed under Touches
- [x] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
