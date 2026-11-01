# CCW-019 verification

## Product owner review

### What you can do now

Server endpoint tickets can now run through the story pipeline with `archetype: endpoint`. The archetype tells each agent what DFS-036, DFS-038 and DFS-040 learned by hand:
- where the files go: a package per feature, the API's own records, `ApiError`/`ApiException`, and tag and operation IDs that name the generated client's services
- what the architect stubs, and that the architect regenerates the OpenAPI spec at design time
- a 12-item checklist: security, paths inside a scan, errors, status codes, records, the spec, the generated client, server state, streams, async test states, the dev proxy, and docs
- how endpoint tests get past the pipeline's red gate

That last part rests on a new shared test helper. When a stubbed controller method throws behind a `500`, `TestServer` now rethrows the server's exception, so the story test fails with the stub's own exception, as the red gate requires. As a bonus, any server test that hits a `500` now shows the real server exception instead of a status mismatch.

### Acceptance criteria

- **AC-1** met (mechanical): a script listed `.claude/archetypes/endpoint/` and found:
  - `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `impl-guide.md`, `test-guide.md`
  - `skeletons/` with `FeatureController.java.tmpl` and `ApiRecord.java.tmpl`
  - frontmatter `name: endpoint`, `version: 1`, the four phases, `gates: [design, done]` and `models`
- **AC-2** met (read): "Applies to" names endpoints under `/api` in `server`, and sends use cases to `use-case`, wiring to `wiring`, and screens to the later component archetype. "Where files go" covers the feature package, the API's own records, `ApiError`/`ApiException` (with where they move once a second feature exists), `@Tag`/`@Operation`, the stubs, and the architect running `:server:updateOpenApi` in the design phase.
- **AC-3** met (read + mechanical): the checklist has 12 items covering every area the criterion lists. The script confirmed each item names its source ticket.
- **AC-4** met (mechanical):
  - a throwaway stubbed endpoint (`GET /api/throwaway` throwing `not implemented: CCW-019`) and a throwaway test calling it through `TestServer` produced a JUnit XML failure with `type="java.lang.UnsupportedOperationException"` and the message `not implemented: CCW-019`
  - with the spec regenerated (as the architect would), the real red gate, `java tools/story-gate/StoryGate.java red CCW-019`, printed `PASS red CCW-019: 1 story tests fail with stubs, 0 other tests fail on the ticket's stub, 0 fail in declared classes, 524 other tests pass (3 skipped)`
  - the throwaway files were removed and the spec restored; then `./gradlew build` was green, with 62 server tests
- **AC-5** met (read):
  - `design.md.tmpl` has the endpoint table, the security matrix, the API records, the spec and client step, and `## Expected breakage`
  - `test-plan.md.tmpl` has a Timing-sensitive column and the Level `spec (OpenApiSpecTest)`
  - `test-guide.md` names `TestServer`, `StubSurfacingConfiguration`, the raw-socket `Host` check (`rawGet`) and `HoldingExecutorConfiguration`
- **AC-6** met (mechanical + read):
  - the script found 20 trace rows: decisions D1 to D7 of DFS-040, the surprises S1 to S10, and three facts F1 to F3
  - all of D1 to D7 and S1 to S6 were present and traced, and all 12 checklist items were used, so nothing is untraced and nothing gets cut
  - I read each row against the archetype text
- **AC-7** met (read): `ui.md` D7 names the endpoint archetype (CCW-019). It says the gates needed no Node/npm change, and that the component archetype follows DFS-041.

### Try it

Read `.claude/archetypes/endpoint/archetype.md` (about 80 lines), and the dry run in `docs/tickets/backlog/CCW-019-endpoint-archetype/dry-run.md`. The dry run's last section says what writing it changed in the archetype. The archetype's real test is the next endpoint ticket, run with `archetype: endpoint`.

### Decided without you

- **`ApiError`/`ApiException` stay in the `scan` package for now.** The archetype tells the first ticket with a second feature package to move them to a shared `us.pochaev.dfs.server.api` package in its design phase. Moving them now would touch DFS-040's code for no current need.
- **Security tests must also reach a stub** (found while writing the guide). Security refuses a request before any controller runs, so a test that only asserts `403` would pass against stubs and fail the red gate. The guide makes each security test create its fixture and check "no side effect" through the API, which is what DFS-040's AC-7 tests already do.
- **No story test may read the committed spec** (found during the dry run, row S8). Once the architect regenerates the spec at design time, such a test passes against stubs. Spec criteria go to `OpenApiSpecTest` plus the verifier's reading. DFS-040's AC-8 test would have tripped this under the pipeline.
- **The stub-surfacing helper is always on** in `TestServer`, not opt-in, so no test can forget it.

### Known limits

- **Not yet used by real agents.** The first live use is the next endpoint ticket (decision 2), and its retro will show what the agents misread.
- **The helper keeps one recorded exception per server** and clears it after each request. Two concurrent requests that both fail with a `500` could swap exceptions. No test does that today.
- **The component archetype is still missing.** DFS-041 is built by hand first (decision 1).

### Evidence

- Commits: `4d3e774` (the helper, test tree only), `df7efad` (the archetype, the dry run, and `ui.md`).
- AC-4: the throwaway JUnit XML is kept in the session scratchpad. The `RedCheck` output is quoted above. After cleanup, `./gradlew build` was green and `git status` was clean.
- AC-1 and AC-6: a check script printed `RESULT PASS`. A first run reported every item "without source" because of a bug in the script's regex (it expected `DFS036` instead of `DFS-036`); I fixed the script, not the archetype.
- Scope: `git diff --name-only main...HEAD` lists only paths under Touches. A search of `.claude/`, `CLAUDE.md` and `docs/design/` for lists of archetypes (CCW-018.L-1) found only `ui.md` D7 needing a change.

### How it went

Written by hand in one session. The dry run earned its keep: it surfaced three rules the archetype would otherwise have missed (S8, S9 and S10).

## Summary

7 criteria met:
- mechanically: AC-1, AC-4 (including a real `RedCheck` pass) and AC-6
- by reading: AC-2, AC-3, AC-5 and AC-7

Build green, scope respected. Merging this changes harness files, so the backend gets the reread message.
