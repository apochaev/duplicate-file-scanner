---
id: DFS-036
title: Secure the local server against other pages in the browser
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: frontend
depends_on: [DFS-035]
touches: []
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
- The path-inside-roots rule needs scans, which do not exist in `server` yet (the scan screen is `ui.md` ticket 5); whether this ticket builds the check now or the first endpoint that takes a path does is an open question.

## Acceptance criteria

The full ID of each criterion is `DFS-036.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Secure the local server

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined>

**Out of scope:**
- The Angular UI (`ui.md` ticket 4); this ticket's page stays a placeholder.
- Any endpoint that reads the disk.
- Users, logins or remote access: one local user only.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Archetype: `none` or a pipeline archetype?
- Which endpoint proves the cookie check, given there is no `/api` yet (a minimal `/api/session` or similar)?
- Does the server open the browser itself (U10 says so), and how is that tested or switched off in tests?
- Token: lifetime and single use; what a second visit with the same token does.
- Path-inside-roots check: now, or with the first endpoint that takes a path?
- What `GET /` returns without the cookie (the page itself, a "use the link from the console" page, or 403).
