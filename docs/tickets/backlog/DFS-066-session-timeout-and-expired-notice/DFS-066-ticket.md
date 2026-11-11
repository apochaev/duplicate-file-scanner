---
id: DFS-066
title: Keep the login session for a day and explain an expired one
status: ready
type: fix
scope: [build]
archetype: component
gates: [done]
priority: high
lane: backend
depends_on: []
touches:
  - server/src/main/resources
  - server/frontend/src/app
  - server/src/test/java/us/pochaev/dfs/server
blocked_reason:
created: 2026-11-11
updated: 2026-11-11
completed:
---

# DFS-066: Keep the login session for a day and explain an expired one

## Story

As a user reviewing a finished scan
I want the page to keep working after I leave it idle, and to tell me plainly when the login has ended
So that I do not lose sight of my results and know what to do when it happens

## Context

Reported 2026-11-11: reloading the page after a scan has finished sometimes shows no results, with the browser console reporting `Failed to load resource: the server responded with a status of 403`. A reload during a running scan works.

Root cause (read from the code, not yet reproduced by hand): the scan data is not lost. `ScanRegistry` keeps every scan until the process exits and nothing evicts it. The 403 is the session check. `TokenExchangeController` trades the `?token=` link for an `HttpSession` and `SecurityConfiguration` keeps the login in it (`HttpSessionSecurityContextRepository`, DFS-036 decision 3). `application.properties` sets the cookie's name, `HttpOnly` and `SameSite` but no `server.servlet.session.timeout`, so Spring Boot's default of 30 idle minutes applies. A running scan keeps the session alive by polling and streaming; a finished scan left idle does not, so the next `/api/**` call after 30 minutes answers 403. A server restart has the same effect (new token, no sessions). On a reload with no valid session the shell (`app.ts`) shows "Open the link printed in the console" (a rejected `/api/session`), which does not say the session ended; during use the page has no text for a 403: only `404 UNKNOWN_SCAN` and `409 SCAN_NOT_COMPLETED` have messages (`result-error.ts`), so every other screen shows a generic failure or nothing.

Options considered with the user: a long timeout (chosen, as no timeout at all), a page keep-alive ping, a signed cookie replacing the session (a change to DFS-036's settled design), a notice for 403 (chosen), and persisting the newest completed scan across restarts (a separate ticket if wanted).

## Decisions

1. **No idle timeout.** The session lasts until the server stops, which is the stated goal ("as long as the user needs it or until server restart"). Set in `application.properties` as a zero or negative `server.servlet.session.timeout`, which Spring Boot passes to Tomcat as "never expires"; the ticket's test must prove the effective value, not only the property (see the open question on the seam). Chosen over 24 h and 8 h: one user and one browser, so uncleaned sessions cost nothing, and the cookie stays `HttpOnly` and `SameSite=Strict` on `127.0.0.1`.
2. **AC-1 is observed on the running container.** A test in `server/src/test` starts the server on a random port, as `ServerApplicationTest` does, and reads Tomcat's effective `Context.getSessionTimeout()`, asserting it is zero or negative ("never"). No waiting, and a wrong property name or value fails it. Chosen over asserting `ServerProperties` alone (it would stay green if Tomcat stopped honouring the value) and over a short-timeout run (it overrides the value under test and costs over a minute).
3. **One central notice.** An Angular HTTP interceptor (registered in `app.config.ts`) turns any 403 from `/api/**` into one "session ended" signal; the shell (`app.ts`, `app.html`) then replaces the routed screen with the notice. The reload case already ends in the shell's "Open the link printed in the console" (a rejected `/api/session`); it gets the same wording, which cannot tell a first visit from an expired session, so it covers both. A failed `EventSource` stream is not covered (it carries no status) and need not be: a live scan keeps the session alive. Chosen over per-screen 403 texts (about five files, easy to forget for a new call) and over rewording the reload text alone (leaves the silent failures during use).
4. **Archetype `component`, gates `[done]`.** The frontend part is the larger one and the `component` checklist fits it; the one Java test (decision 2) sits under `touches`. No design gate: nothing here plans or deletes files, and decisions 1 to 3 already fix the design. Chosen over `none` (loses the verifier and the criterion-to-test trace for a bug that has already bitten) and over two tickets (two sessions for four small criteria).

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-066.AC-<n>`.

- **AC-1** The session set by the token link never expires from idleness: the running server's session timeout is "never", not Spring Boot's 30 minutes
- **AC-2** A 403 from any `/api/**` call made while a screen is showing replaces that screen with the notice "Your session ended, or the server was restarted. Open the link printed in the console."
- **AC-3** A page opened without a valid session shows the same notice, in place of "Open the link printed in the console", and makes no scan call
- **AC-4** A failure that is not a 403 is not turned into the notice: a `404 UNKNOWN_SCAN` or a `500` keeps the message its screen already shows

## Scenarios

```gherkin
Feature: The login session outlives idleness and an ended one is explained

  @AC-1
  Scenario: The running server's sessions never expire from idleness
    Given the server started on a random port, as in ServerApplicationTest
    When the web application context's session timeout is read from the running Tomcat
    Then it is zero or negative, meaning "never", not 30 minutes

  @AC-2
  Scenario: The scan list's 403 ends the session screen
    Given the page is connected and the scan screen is showing
    When the next GET /api/scans answers 403
    Then the scan screen is replaced by "Your session ended, or the server was restarted. Open the link printed in the console."
    And no scan list or Start button is shown

  @AC-2
  Scenario: A 403 while a result is showing ends it too
    Given the page is connected and the result screen of scan "s1" is showing its sets
    When the next page of GET /api/scans/s1/sets answers 403
    Then the result screen is replaced by the same notice

  @AC-2
  Scenario: A 403 on starting a scan ends the session screen
    Given the page is connected and the scan screen is showing
    When POST /api/scans answers 403
    Then the scan screen is replaced by the same notice

  @AC-3
  Scenario: A reload after the session ended shows the notice and calls nothing else
    Given the browser holds no valid session, so GET /api/session answers 403
    When the page loads
    Then the page shows "Your session ended, or the server was restarted. Open the link printed in the console."
    And "Open the link printed in the console" is not shown on its own
    And no request is made to GET /api/scans

  @AC-3
  Scenario: A session reported as not authenticated shows the same notice
    Given GET /api/session answers 200 with authenticated false
    When the page loads
    Then the page shows the same notice and makes no call to GET /api/scans

  @AC-4
  Scenario: An unknown scan keeps its own message
    Given the page is connected and the result screen of scan "gone" is loading
    When GET /api/scans/gone answers 404 with code UNKNOWN_SCAN
    Then the screen shows "This scan is not in this server run (the server may have restarted)"
    And the session notice is not shown

  @AC-4
  Scenario: A server error keeps its generic message
    Given the page is connected and the result screen of scan "s1" is loading
    When GET /api/scans/s1/folders answers 500 with no body
    Then the screen shows "The results could not be loaded; reload the page"
    And the session notice is not shown
```

## Scope

**Touches:**
- `server/src/main/resources`: `server.servlet.session.timeout`
- `server/frontend/src/app`: the interceptor, its registration in `app.config.ts`, and the notice in `app.ts` and `app.html`
- `server/src/test/java/us/pochaev/dfs/server`: the test that reads Tomcat's session timeout

**Out of scope:**
- Persisting scan results across a server restart
- Replacing the `HttpSession` with a signed cookie, or any other change to DFS-036's security design
- A keep-alive ping from the page

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No source files changed outside the paths listed under Touches (the ticket folder's own files excepted)
- [ ] `./gradlew :server:npmTest` and `./gradlew :server:e2eTest` pass (the shell changes, so the browser flows run)
- [ ] By hand: leave a finished scan idle past the old 30 minutes (or shorten the timeout for the check), reload, and the results still load; restart the server, reload, and the notice appears

## Open questions

None.
