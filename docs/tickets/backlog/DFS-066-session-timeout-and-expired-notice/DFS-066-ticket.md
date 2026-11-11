---
id: DFS-066
title: Keep the login session for a day and explain an expired one
status: draft
type: fix
scope: [build]
archetype:
priority: high
lane: backend
depends_on: []
touches:
  - server/src/main/resources
  - server/frontend/src/app
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

Root cause (read from the code, not yet reproduced by hand): the scan data is not lost. `ScanRegistry` keeps every scan until the process exits and nothing evicts it. The 403 is the session check. `TokenExchangeController` trades the `?token=` link for an `HttpSession` and `SecurityConfiguration` keeps the login in it (`HttpSessionSecurityContextRepository`, DFS-036 decision 3). `application.properties` sets the cookie's name, `HttpOnly` and `SameSite` but no `server.servlet.session.timeout`, so Spring Boot's default of 30 idle minutes applies. A running scan keeps the session alive by polling and streaming; a finished scan left idle does not, so the next `/api/**` call after 30 minutes answers 403. A server restart has the same effect (new token, no sessions). The page has no text for a 403: only `404 UNKNOWN_SCAN` and `409 SCAN_NOT_COMPLETED` have messages (`result-error.ts`).

Options considered with the user: a long timeout (chosen, as no timeout at all), a page keep-alive ping, a signed cookie replacing the session (a change to DFS-036's settled design), a notice for 403 (chosen), and persisting the newest completed scan across restarts (a separate ticket if wanted).

## Decisions

1. **No idle timeout.** The session lasts until the server stops, which is the stated goal ("as long as the user needs it or until server restart"). Set in `application.properties` as a zero or negative `server.servlet.session.timeout`, which Spring Boot passes to Tomcat as "never expires"; the ticket's test must prove the effective value, not only the property (see the open question on the seam). Chosen over 24 h and 8 h: one user and one browser, so uncleaned sessions cost nothing, and the cookie stays `HttpOnly` and `SameSite=Strict` on `127.0.0.1`.

## Acceptance criteria

<!-- Filled during refinement. -->

The full ID of each criterion is `DFS-066.AC-<n>`.

- **AC-1** The session set by the token link never expires from idleness: the running server's session timeout is "never", not Spring Boot's 30 minutes
- **AC-2** <to be refined: a 403 from `/api/**` shows a message saying the session ended and to open the link printed in the console, on the scan screen and the result screen>

## Scenarios

<!-- Not drafted. Do not invent scenarios before refinement. -->

## Scope

**Touches:**
- `server/src/main/resources`: `server.servlet.session.timeout`
- `server/frontend/src/app`: one 403 message shared by the scan and result screens

**Out of scope:**
- Persisting scan results across a server restart
- Replacing the `HttpSession` with a signed cookie, or any other change to DFS-036's security design
- A keep-alive ping from the page

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No source files changed outside the paths listed under Touches (the ticket folder's own files excepted)
- [ ] By hand: leave a finished scan idle past the old 30 minutes (or shorten the timeout for the check), reload, and the results still load; restart the server, reload, and the notice appears

## Open questions

- How a test observes AC-1 (decision 2).
- Archetype and gates are not set (draft).
