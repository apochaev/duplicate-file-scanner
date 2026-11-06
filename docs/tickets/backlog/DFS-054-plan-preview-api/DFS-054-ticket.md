---
id: DFS-054
title: Preview a deletion plan through the server API
status: draft
type: feature
scope: [build]
archetype: endpoint
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [design, done]
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: backend
depends_on: []
touches:
  - server/src/main/java/us/pochaev/dfs/server/plan
  - server/src/main/java/us/pochaev/dfs/server/scan
  - server/src/main/java/us/pochaev/dfs/server/result
  - server/src/test/java/us/pochaev/dfs/server/PlanApiTest.java
  - server/src/test/java/us/pochaev/dfs/server/plan
  - server/src/test/java/us/pochaev/dfs/server/TestServer.java
  - server/frontend/openapi.json
  - CLAUDE.md
  - docs/design/ui.md
blocked_reason:
created: 2026-11-05
updated: 2026-11-06
completed:
---

# DFS-054: Preview a deletion plan through the server API

## Story

As the user of the local DFS server
I want to send rules ("keep A, quarantine in B"), exceptions and single-set picks for a finished scan and get back the plan they produce
So that the UI can show me exactly what a bulk delete would move, which copy each file keeps, and where my rules conflict, before anything touches the disk

## Context

Milestone M-3 ("build and check a plan"), first step (`docs/design/ui.md`, "Milestones"; U6, U7). The endpoint half of the plan builder; a frontend ticket for the plan screen follows and depends on this one.

- **The core is built:** `PreviewDeletionPlan.preview(ScanResult, PlanRequest)` returns `Result<DeletionPlan>` (DFS-022): rules `KeepAndQuarantine(keep, quarantineIn)`, exceptions, picks `KeepCopy(digest, keep)`; the plan has a content-derived `id`, `Quarantine` operations with their `Source`, `conflicts`, `PlanTotals` and `leftBehind`; invalid requests fail with `INVALID_RULE`, `UNKNOWN_SET`, `PATH_NOT_IN_SET` or `DUPLICATE_PICK`. Drift check (DFS-033), content verify (DFS-034) and asynchronous verify (DFS-042) take a previewed plan and are later steps of M-3.
- **CLAUDE.md safety invariants that apply:** "Preview is a pure query" (the endpoint never touches disk state); "Never delete the last copy" (conflicts are reported and block confirming, U7.1); "Hard links are not duplicates" (an operation moves a physical copy with all its links).
- **The server's conventions (DFS-040, DFS-047, DFS-048):** a COMPLETED scan only (`409 SCAN_NOT_COMPLETED`), `404 UNKNOWN_SCAN`, paths absolute (`400 RELATIVE_PATH`) and inside a scan root (`403 PATH_OUTSIDE_ROOTS`), `{code, message}` errors, the API's own records (never core types), the spec committed as `server/frontend/openapi.json` and `./gradlew :server:updateOpenApi`.
- **Analysis decision 1** (`docs/design/folder-overlap-analysis.md`): pairs are never combined into a verdict; only the whole-plan check says what is safe. This endpoint is that check.

## Decisions

1. **Previewing creates a frozen plan the server keeps** (2026-11-05). `POST /api/scans/{id}/plans` takes the whole request (rules, exceptions, picks), previews it with `PreviewDeletionPlan`, keeps the resulting plan with the scan in memory (lost on restart, like scans) and answers `201` with `Location: /api/scans/{id}/plans/{planId}` and the plan's summary; `GET` on that location reads it back. The plan ID is DFS-022's content-derived ID, so the same request previews to the same ID. The draft lives in the browser; editing it and previewing again posts a new request and gets a new ID (U7, D4). The later drift, verify and execute endpoints name `{planId}`, so the browser never resends or rebuilds a plan, and a big plan's operations can be paged from the kept copy. Not a stateless preview answering the whole plan (every later step recomputes, no paging, and "frozen" rests on the client resending the same request) and not a server-held draft edited rule by rule (many endpoints and a mutable plan on the server, against D4, where only previewed plans are fixed).
2. **The server keeps every previewed plan until it stops, like scans** (2026-11-05). Each plan is kept under its scan in memory for the server run, with no limit; every plan ID previewed for a scan stays readable and usable, and an ID never previewed for that scan answers `404` `UNKNOWN_PLAN`. Previewing the same request again gives the same ID (DFS-022) and keeps one copy. Same lifetime as scans (`ScanRegistry`), so nothing new to explain; the back button and a second tab keep working; the least code. Known limit: memory grows with each distinct preview (a plan holds about one operation per quarantined copy, far less than its scan); a limit can come later behind the same endpoints. Not the latest N per scan (an eviction rule, and a plan still on screen can disappear) and not only the latest per scan (two tabs replace each other's plan, and a later verify or execute step could find its plan gone).
3. **A plan summary plus paged operations and paged conflicts** (2026-11-05). The `201` of a preview and `GET /api/scans/{id}/plans/{planId}` carry the plan ID, its totals (files, bytes, sets), its `leftBehind` (one entry per rule) and the operation and conflict counts. The operations come from `GET /api/scans/{id}/plans/{planId}/operations?page=&size=` and the conflicts from `GET /api/scans/{id}/plans/{planId}/conflicts?page=&size=`, largest first, paged like `/sets`. Every response stays small whatever the rules: crossing rules on big folders can produce thousands of conflicts, exactly when the user needs a quick answer. Not the whole plan in one response (many megabytes per preview on a big scan) and not every conflict inline in the summary (huge again when rules cross).
4. **Operations and conflicts are self-contained records** (2026-11-05). A conflict carries its set's digest and size, its copies (each copy's links, and the rule that would quarantine it or the rule that keeps it) and its sources; an operation carries digest, size, links, keeper and its source; a source is `{kind: "rule", ruleIndex}` or `{kind: "pick", digest}` (the browser holds its own request, so the index is enough); a `leftBehind` entry is `ruleIndex`, `folder`, `files`, `bytes`, as in the core. The plan screen can explain each conflict (U7.1: conflicts block confirming) from one page with no further calls, and "largest first" needs the size anyway. The server joins the scan's sets onto the core's `Conflict`. Not a mirror of the core with a new `GET /sets/{digest}` (one call per conflict shown, and an endpoint outside this ticket) and not counts per rule pair (the user could not see which sets clash or settle one with a single-set pick, U6).
5. **The server checks every request path first, then passes the core's codes through** (2026-11-05). Each rule's `keep` and `quarantineIn`, each exception and each pick's `keep` must be absolute (`400` `RELATIVE_PATH`) and inside a scan root (`403` `PATH_OUTSIDE_ROOTS`), the message naming the field (e.g. `rules[1].keep`); then the core's `INVALID_RULE`, `UNKNOWN_SET`, `PATH_NOT_IN_SET` and `DUPLICATE_PICK` answer `400` with the same code and message. Only the first problem is reported, in request order, as the core does. A refused request keeps no plan. An empty request (no rules, no picks) is valid and previews an empty plan with zero totals. Same path rule as `/folders`, `/sets` and `/pair`; a mistyped or out-of-scan folder is refused with its field named instead of previewing as "0 files" on a deletion screen. Not a straight pass to the core (a rule on a wrong folder silently does nothing) and not an absolute-only check (keeps that silent no-op for folders outside the scan).
6. **Archetype `endpoint`, gates `[design, done]`** (2026-11-06). Controllers, the API's records, a plan store per scan and the spec, in `server`, over the existing `PreviewDeletionPlan` bean (`scanner-spring`): the `endpoint` archetype, as DFS-047 and DFS-048. The pipeline pauses after the architect: this endpoint is the whole-plan check the user trusts before deleting (analysis decision 1), and the record shapes (decision 4's conflict join) and where the plan store lives (`ScanRegistry`'s per-scan state or a store of its own) are checked before tests depend on them. Not `[done]` (a wrong record shape or store found only at the end redoes tests and the spec).

## Acceptance criteria

The full ID of each criterion is `DFS-054.AC-<n>`.

- **AC-1** `POST /api/scans/{id}/plans` with `rules` (`keep`, `quarantineIn`), `exceptions` and `picks` (`digest`, `keep`) on a COMPLETED scan answers `201` with `Location: /api/scans/{id}/plans/{planId}` and a plan summary: `planId` equal to the `id` of the `DeletionPlan` that `PreviewDeletionPlan` returns for that scan and request, its totals (files, bytes, sets), its `leftBehind` (one entry per rule: `ruleIndex`, `folder`, `files`, `bytes`), and `operationCount` and `conflictCount`.
- **AC-2** `GET /api/scans/{id}/plans/{planId}` answers that summary for every plan previewed for the scan during the server run, an earlier plan still after later previews; previewing the same request twice answers the same `planId`; a `planId` never previewed for that scan (including one previewed for another scan) answers `404` `UNKNOWN_PLAN`.
- **AC-3** `GET /api/scans/{id}/plans/{planId}/operations` answers an `OperationPage` of every quarantine operation, largest `size` first: `digest`, `size`, `links` (every link of the copy), `keeper`, and `source` as `{kind: "rule", ruleIndex}` or `{kind: "pick", digest}`.
- **AC-4** `GET /api/scans/{id}/plans/{planId}/conflicts` answers a `ConflictPage` of every conflict, largest `size` first: the set's `digest` and `size`, its copies (each with its links and the rules that would quarantine it or keep it) and the conflict's `sources`.
- **AC-5** `operations` and `conflicts` page like `/sets`: `page` and `size` (`400` `INVALID_PAGE` for `page` < 0 or `size` outside 1-500), and a page past the end is empty with the same `totalItems`; an unknown `planId` answers `404` `UNKNOWN_PLAN` on both.
- **AC-6** No plan quarantines the last copy: for crossing rules (keep A, quarantine in B; keep B, quarantine in A), each set with copies only in A and B is a conflict and no operation names it, and in every plan the API answers, every set of the scan keeps at least one copy that no operation moves.
- **AC-7** Hard links are not copies: for a set whose file in A has a hard link in B and a real copy in C, the rule "keep C, quarantine in A" answers one operation whose `links` are both the A and the B path, and no operation's `keeper` is a link of the copy it moves.
- **AC-8** Previewing is a pure query: after any `POST /plans`, answered or refused, every file under the scan's roots has the same path, size and modified time as before, and no file or folder was created.
- **AC-9** Every request path (each rule's `keep` and `quarantineIn`, each exception, each pick's `keep`) must be absolute (`400` `RELATIVE_PATH`) and inside a scan root (`403` `PATH_OUTSIDE_ROOTS`, also through `..`), the message naming the field (e.g. `rules[1].keep`); a refused request answers no `Location`.
- **AC-10** The core's refusals answer `400` with the same code and message (`INVALID_RULE`, `UNKNOWN_SET`, `PATH_NOT_IN_SET`, `DUPLICATE_PICK`), only the first problem in request order is reported, and an empty request (no rules, no picks) answers `201` with an empty plan (zero totals and counts).
- **AC-11** All four endpoints answer `409` `SCAN_NOT_COMPLETED` for a scan that is not COMPLETED and `404` `UNKNOWN_SCAN` for an unknown or malformed scan ID, checked before the body or any parameter.
- **AC-12** All four endpoints answer `403` without the session cookie and with a foreign `Host`, and `POST /plans` answers `403` without a matching `Origin`.
- **AC-13** Two previews of different requests on one scan sent at once both answer `201` with different `planId`s, and both plans are readable afterwards.
- **AC-14** `server/frontend/openapi.json` describes `previewPlan`, `getPlan`, `listPlanOperations` and `listPlanConflicts` (tag `plans`) and their request and response schemas: a test reading the committed spec finds them, `OpenApiSpecTest` passes, and the generated client has a `PlansService`.

## Scenarios

```gherkin
Feature: Plan preview API

  Background:
    Given a COMPLETED scan of the roots "<tmp>/A", "<tmp>/B" and "<tmp>/C" (absolute paths in a @TempDir)
    And "photo.jpg" of 2 MB exists as "<tmp>/A/photo.jpg" and "<tmp>/B/photo.jpg"
    And "song.mp3" of 5 MB exists as "<tmp>/A/song.mp3", "<tmp>/B/song.mp3" and "<tmp>/C/song.mp3"
    And "note.txt" of 1 KB exists only as "<tmp>/B/note.txt"

  @AC-1
  Scenario: A rule previews to a kept plan
    When I POST to "/api/scans/{id}/plans" the rule keep "<tmp>/A", quarantine in "<tmp>/B"
    Then the answer is 201 with Location "/api/scans/{id}/plans/{planId}"
    And planId equals the id PreviewDeletionPlan returns for the same scan and request
    And the totals are 2 files, 7 MB, 2 sets
    And leftBehind is one entry: ruleIndex 0, folder "<tmp>/B", 1 file, 1 KB
    And operationCount is 2 and conflictCount is 0

  @AC-2
  Scenario: Every previewed plan stays readable
    Given I previewed "keep A, quarantine in B" as plan P1, then "keep A, quarantine in C" as plan P2
    When I GET "/api/scans/{id}/plans/P1"
    Then the answer is 200 with P1's summary
    And GET of P2 answers P2's summary

  @AC-2
  Scenario: The same request previews to the same plan
    When I POST "keep A, quarantine in B" twice
    Then both answers carry the same planId

  @AC-2
  Scenario: A plan of another scan is unknown here
    Given plan P was previewed for another COMPLETED scan
    When I GET "/api/scans/{id}/plans/P"
    Then the answer is 404 with code "UNKNOWN_PLAN"

  @AC-3
  Scenario: Operations come largest first with their source
    Given plan P is "keep A, quarantine in B" plus the pick keep "<tmp>/C/song.mp3" for song's digest
    When I GET "/api/scans/{id}/plans/P/operations"
    Then the song operations (5 MB) come first, each with source kind "pick" and song's digest
    And the last is photo (2 MB), links ["<tmp>/B/photo.jpg"], keeper "<tmp>/A/photo.jpg", source kind "rule", ruleIndex 0

  @AC-4 @AC-6
  Scenario: Crossing rules become conflicts, never a lost last copy
    When I preview "keep A, quarantine in B" and "keep B, quarantine in A"
    Then photo's set is a conflict of size 2 MB with copies "<tmp>/A/photo.jpg" and "<tmp>/B/photo.jpg", each naming the rule that would quarantine it and the rule that keeps it, and sources ruleIndex 0 and 1
    And no operation names photo's digest
    And every set of the scan keeps at least one copy no operation moves

  @AC-5
  Scenario: Operations and conflicts are paged like sets
    Given plan P has 2 operations
    When I GET its operations with page 0 and size 1
    Then I get 1 item and totalItems 2
    And page 5 with size 1 is empty with totalItems 2
    And size 0, size 501 or page -1 answers 400 with code "INVALID_PAGE", on operations and on conflicts
    And operations and conflicts of an unknown planId answer 404 with code "UNKNOWN_PLAN"

  @AC-7
  Scenario: A hard-linked copy moves with all its links
    Given "<tmp>/B/photo-link.jpg" is created at test time as a hard link of "<tmp>/A/photo.jpg" (skipped where the platform refuses)
    And "<tmp>/C/photo.jpg" is a real copy, and the scan is run again
    When I preview "keep C, quarantine in A"
    Then one operation for photo has links "<tmp>/A/photo.jpg" and "<tmp>/B/photo-link.jpg" and keeper "<tmp>/C/photo.jpg"
    And no operation's keeper is a link of the copy it moves

  @AC-8
  Scenario: Previewing touches nothing on disk
    Given I record every path, size and modified time under the roots
    When I preview "keep A, quarantine in B", then a request refused with RELATIVE_PATH
    Then the recorded paths, sizes and modified times are unchanged and nothing was added

  @AC-9
  Scenario Outline: Request paths are checked first
    When I preview a request whose <field> is <path>
    Then the answer is <status> with code "<code>" and a message naming "<field>"
    And there is no Location header

    Examples:
      | field                 | path                  | status | code               |
      | rules[0].keep         | "A"                   | 400    | RELATIVE_PATH      |
      | rules[0].quarantineIn | "<tmp>/A/../../other" | 403    | PATH_OUTSIDE_ROOTS |
      | exceptions[0]         | "<another tmp dir>"   | 403    | PATH_OUTSIDE_ROOTS |
      | picks[0].keep         | "song.mp3"            | 400    | RELATIVE_PATH      |

  @AC-10
  Scenario Outline: The core's refusals pass through
    When I preview <request>
    Then the answer is 400 with code "<code>" and the core's message

    Examples:
      | request                                             | code            |
      | keep "<tmp>/A", quarantine in "<tmp>/A/sub"         | INVALID_RULE    |
      | a pick for digest "0000"                            | UNKNOWN_SET     |
      | a pick keeping "<tmp>/B/note.txt" for song's digest | PATH_NOT_IN_SET |
      | two picks for song's digest                         | DUPLICATE_PICK  |

  @AC-10
  Scenario: Only the first problem is reported
    When I preview an invalid rule followed by a pick for digest "0000"
    Then the answer is 400 with code "INVALID_RULE"

  @AC-10
  Scenario: An empty request previews an empty plan
    When I preview no rules, no exceptions and no picks
    Then the answer is 201 with totals 0 files, 0 bytes, 0 sets and operationCount and conflictCount 0

  @AC-11
  Scenario: Only a finished, known scan has plans
    Given a scan that is still RUNNING
    When I POST a request with a relative path to its plans
    Then the answer is 409 with code "SCAN_NOT_COMPLETED"
    And each of the four endpoints answers 404 with code "UNKNOWN_SCAN" for scan ID "not-a-uuid" and for a random UUID

  @AC-12
  Scenario: The plan endpoints are behind the server's guards
    When I call each of the four endpoints without the session cookie, or with Host "evil.example"
    Then each answers 403
    And POST to plans with Origin "http://evil.example" answers 403

  @AC-13
  Scenario: Concurrent previews are each kept
    When "keep A, quarantine in B" and "keep A, quarantine in C" are POSTed at the same moment
    Then both answer 201 with different planIds
    And both plans are readable afterwards

  @AC-14
  Scenario: The spec describes the plan endpoints
    When a test reads server/frontend/openapi.json
    Then it finds previewPlan, getPlan, listPlanOperations and listPlanConflicts under tag "plans"
    And OpenApiSpecTest passes and the generated client has a PlansService
```

## Scope

**Touches:**
- `server/src/main/java/us/pochaev/dfs/server/plan`: the plan controller, the API records (decisions 3, 4), the request path checks (decision 5) and the plan store (decisions 1, 2).
- `server/src/main/java/us/pochaev/dfs/server/scan`: `ScanRegistry`, only if the plan store lives in its per-scan state.
- `server/src/main/java/us/pochaev/dfs/server/result`: only to share the existing path checks and page records with the plan package.
- `server/src/test/java/us/pochaev/dfs/server/PlanApiTest.java`: the API tests (one class, or more named `PlanApi*Test.java` beside it).
- `server/src/test/java/us/pochaev/dfs/server/plan`: unit tests (the store, the mapping).
- `server/src/test/java/us/pochaev/dfs/server/TestServer.java`: helpers if needed.
- `server/frontend/openapi.json`: regenerated with `:server:updateOpenApi` in the design phase.
- `CLAUDE.md`: the `server` entry.
- `docs/design/ui.md`: U6, U7 and D4 name the endpoints.

**Out of scope:**
- The plan screen in the browser (a frontend ticket depending on this one).
- Drift check and content verify endpoints (later M-3 tickets on DFS-033, DFS-034, DFS-042).
- Executing a plan (M-5; DFS-051).
- A limit on kept plans, or deleting a plan (decision 2's known limit).
- A server-held editable draft (decision 1) and `GET /sets/{digest}` (decision 4).
- Changes to `PreviewDeletionPlan` or any other core code.
- Changes to the dev proxy: `POST /plans` passes its `Origin` rewrite like `POST /api/scans`.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] Each test's `@DisplayName` starts with its criterion ID (`DFS-054.AC-n`)
- [ ] `./gradlew build` is green (includes the ArchUnit tests, `OpenApiSpecTest` and the Vitest tests)
- [ ] Every endpoint-archetype checklist item is addressed in `design.md` or marked not applicable with a reason
- [ ] `CLAUDE.md`'s `server` entry lists the four plan endpoints, and `docs/design/ui.md` U6, U7 and D4 name them (the verifier reads them)
- [ ] No test this ticket adds uses `Thread.sleep` (AC-13 waits on latches)
- [ ] Started by hand once on Windows: a real folder scanned, then plans previewed with `curl` (a rule, crossing rules with conflicts, one refused path), their operations and conflicts paged, the server log free of warnings and errors
- [ ] No files changed outside the paths listed under Touches

## Open questions

None.
