---
id: CCW-020
title: Add the Angular component archetype
status: done
type: feature
scope: [build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: medium
lane: harness
depends_on: [DFS-041, DFS-044]
touches:
  - .claude/archetypes/component
  - .claude/archetypes/endpoint/archetype.md
  - .claude/agents/architect.md
  - .claude/agents/test-author.md
  - .claude/agents/implementer.md
  - .claude/agents/verifier.md
  - .claude/skills/tickets/SKILL.md
  - tools/story-gate
  - server/frontend/angular.json
  - server/build.gradle.kts
  - server/frontend/src/testing
  - docs/design/story-orchestration.md
  - docs/design/ui.md
  - docs/tickets/backlog/CCW-020-angular-component-archetype/dry-run.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed: 2026-11-01
---

# CCW-020: Add the Angular component archetype

## Story

As the developer running frontend tickets through the story pipeline
I want an archetype for Angular screens and components in `server/frontend`
So that the pipeline's agents can design, test and build a screen from its ticket, with the lessons of DFS-037, DFS-038 and DFS-041 built in

## Context

`docs/design/ui.md` D7: harness work for the UI goes under `CCW`, including archetypes for Angular components and API endpoints. The endpoint archetype exists (CCW-019, `.claude/archetypes/endpoint/`); its decision 1 put the component archetype after DFS-041, to be written from a real screen. DFS-041 (the scan screen, `server/frontend/src/app/scans/`) is done and was built by hand as that material.

The story gate (`tools/story-gate`) assumes Java throughout:
- `Junit.readAll` reads only Gradle's `build/test-results/test/TEST-*.xml`; Vitest writes no such file.
- A stub failure is exactly `java.lang.UnsupportedOperationException("not implemented: <ID>")` (`TestResult.STUB_EXCEPTION`).
- `Sources` counts only `.java` and `.kt` files, so the stubs check (no stub marker left in production code), the disabled-test check and the freeze check never see TypeScript.

A pipeline ticket whose tests are all Vitest would today give the red, green and traceability checks no test results at all.

What DFS-037, DFS-038 and DFS-041 met, for the checklist:
- the generated client returns Promises, so a test lets them settle (`setTimeout`) before `whenStable` (DFS-038)
- `src/app/api` is generated, git-ignored, regenerated on every build, test and serve, and never edited (DFS-038)
- the one call outside the generated client, the SSE stream, goes through an injectable wrapper with its `EventSource` behind a token, because jsdom has no `EventSource` (DFS-041 decision 5)
- Material component harnesses drive the tests (DFS-041)
- the 500 kB initial-bundle warning budget is exceeded by Material (DFS-041)
- no web font is loaded, so Material falls back to a serif (DFS-041)
- browser screenshots of a background window can lag behind the DOM, so a live check reads the DOM (DFS-041)
- `ng serve` outlives its Gradle task and must be killed by PID or port (DFS-037)
- the dev proxy rewrites `Origin` so `POST`s pass the server's check (DFS-040)
- shell heredocs break on apostrophes in TypeScript and templates; write files with the editor (DFS-040, DFS-041)

## Decisions

1. **Vitest writes JUnit XML and the gate learns TypeScript** (2026-11-01).
   - **The report:** `angular.json`'s test options add a `junit` reporter beside the default one. It writes to its own directory, `server/build/test-results/vitest/TEST-vitest.xml`, not Gradle's `test-results/test`, which Gradle's `Test` task owns and may clear.
   - **Freshness:** `:server:npmTest` declares the file as an output, so `cleanTest build` (`GreenCheck`) never reads a stale report.
   - **Reading it:** the gate's `Junit.findReports` also reads `build/test-results/vitest/`.
   - **Stubs:** `TestResult` accepts a TypeScript stub, a failure of type `Error` with the message `not implemented: <ID>`, beside the Java one.
   - **Source checks:** `Sources` counts `.ts` and `.html` under `src/`, minus the generated `src/app/api`. The stubs check finds markers in them; the disabled check finds `it.skip`, `xit`, `describe.skip` and `xdescribe` in `*.spec.ts`; the freeze check treats `*.spec.ts` as test files.
   - **Self-tests:** each change gets a case in the gate's self-test.
   - **What this means in the gate, found reading it (2026-11-01):**
     - `RedCheck` runs `cleanTest test --continue`, which never starts `:server:npmTest`. It also runs `npmTest`.
     - `Build.summarize` treats only tasks named `test` as test tasks, so a failing `npmTest` reads as a broken build. `npmTest` also counts as a test task.
     - `npmTest` deletes its report before it runs, so a run that dies early leaves no stale report.
     - `RedCheck` compares the raw test name with the criterion prefix, so it uses the traced name from decision 6.
   - Not chosen: a stub that pretends to be the Java exception (a hack copied into every stub, with TypeScript still invisible to the source checks); an archetype for hand-built tickets only (no `/story run` for frontend work).

2. **DFS-041's bundle budget and font are fixed in a product ticket, DFS-044** (2026-11-01). It is product work (`angular.json`'s budget, `styles.css`'s font), so it goes under `DFS` in the frontend lane, built by hand before this ticket; this archetype's checklist names both, so later screens start from them.

3. **Checked by a dry run against DFS-041 plus one mechanical gate run** (2026-11-01).
   - **Dry run:** each DFS-041 decision and scenario is traced to an archetype checklist item, as CCW-019's was, and a script reports any left untraced.
   - **Gate run:** on a throwaway branch that is never merged, a small component with a stub (throwing `not implemented: CCW-020`) and a test whose name starts with a criterion ID.
     - The gate's red check must see the stub failure in the Vitest JUnit file.
     - The test is then made to pass, and the green and traceability checks must pass.
     - The outputs are recorded in `verification.md`.
   - This proves the whole path: Vitest writes the report, Gradle keeps it fresh, the gate parses it.
   - Not chosen: the dry run alone (the gate's Vitest path proven only by self-tests); a live `/story run` on a product ticket (it needs a refined product ticket first, and a failure is hard to pin on the archetype or the feature). The first real frontend pipeline ticket is the live run.

4. **The agent prompts get a language-neutral rule; the archetype's guides hold the TypeScript specifics** (2026-11-01).
   - Today the four pipeline prompts are Java-only:
     - `architect.md`: stubs throw `new UnsupportedOperationException(...)`, compiled with `compileJava`
     - `test-author.md`: `@DisplayName`, and the exact `UnsupportedOperationException`
     - `implementer.md` and `verifier.md`: `./gradlew build`
   - Each rule is restated as the stub's not-implemented failure "as the archetype's guides define it" (Java: `UnsupportedOperationException`; TypeScript: `Error`), and the test name "starts with the criterion ID, as the archetype's test guide shows". The Java examples stay as examples.
   - The new archetype's `impl-guide.md` and `test-guide.md` give the TypeScript forms: `throw new Error('not implemented: <ID>')`, `it('<ID>.AC-n ...')`, and `:server:npmTest`.
   - This is a harness merge, so it sends the CCW-018 message.
   - Not chosen: the archetype's guides overriding the prompts (agents would get two contradicting instructions and follow the prompt); separate TypeScript agents (four near-duplicate prompts, and a change to the story skill's phase table).

5. **`archetype: none`, written by hand** (2026-11-01), like every earlier archetype ticket (CCW-013, CCW-019). The archetype files and prompt edits are checked by reading. The gate changes are checked by self-tests and by decision 3's gate run, and the dry run's trace is checked by a script.

6. **The gate understands Vitest's report shape** (2026-11-01).
   - A real run with the `junit` reporter (all 21 current tests) writes `classname="src/app/app.spec.ts"` and `name="App > DFS-041.AC-10 shows the scan screen once connected"`: the `describe` names, then ` > `, then the test's own name.
   - For a test case whose `classname` ends in `.ts`:
     - **the traced name** is the part after the last ` > ` (traceability, and the red check's count of story tests)
     - **the disabled check** scopes to the story's files by that path itself, not by a class name, which for a path gives `ts`
     - **the label** in a verdict is the file name plus the full test name
   - Tests keep their `describe` blocks; DFS-041's tests trace as they are.
   - Not chosen: a test guide that bans `describe` or wants the ID first in it (against normal Angular tests, and a wrapped test silently breaks traceability); a reporter setting (Vitest's JUnit reporter has templates for `classname` and the suite name, not for the test name).

7. **A shared test helper surfaces a stub's error as the test's failure** (2026-11-01, found while implementing).
   - **Found by a probe** (a throwaway stubbed component, not committed). A stub was reached three ways:
     - from the template
     - from a click handler through a Material harness
     - in a generated-client Promise callback

     Each time, the test failed with an `AssertionError`, and the stub's `Error` showed only as a separate "vitest unhandled errors" entry. Angular's `rethrowApplicationErrors` changed nothing. The red gate would reject every such test.
   - **What worked for all three:** an `ErrorHandler` that records errors, with Node's `unhandledRejection` recorded too, and a `settle(fixture)` that rethrows the first recorded error.
   - **The helper:** `server/frontend/src/testing/stub-surfacing.ts` provides `provideStubSurfacing()` and `settle(fixture)`. It is the frontend counterpart of CCW-019's `StubSurfacingConfiguration`.
   - **The gate counts `src/testing/` as test code,** so the freeze check guards it and the stubs check skips it. The test guide requires every story spec to use it.
   - DFS-041's specs are not changed.
   - Not chosen: a recorder copied into each spec (it drifts); the gate accepting an `AssertionError` when an unhandled error carries the stub (the gate cannot tell which test caused it, so a vacuous test would pass red).

## Acceptance criteria

The full ID of each criterion is `CCW-020.AC-<n>`.

- **AC-1** `.claude/archetypes/component/` holds `archetype.md` (frontmatter `name: component`, `version: 1`, `phases: [design, test, implement, verify]`, `gates: [design, done]`, and `models` like the other archetypes), `design.md.tmpl`, `test-plan.md.tmpl`, `impl-guide.md`, `test-guide.md` and `skeletons/` with at least a stubbed standalone component (`.ts`, `.html`) and its spec.
- **AC-2** `archetype.md` says which tickets it applies to (Angular screens and components in `server/frontend`) and which not (endpoints: `endpoint`; core use cases: `use-case`; the security rules), and its "Where files go" covers: a folder per feature under `src/app/`, standalone components with signals, the generated client as the only API access (and never editing `src/app/api`), an injectable wrapper behind a token for anything jsdom lacks (like `EventSource`), and stubs throwing `new Error('not implemented: <ID>')`.
- **AC-3** `archetype.md`'s edge-case checklist covers at least: the generated client and its Promise timing in tests, browser APIs jsdom lacks, Material component harnesses, loading, empty and error states (including the server's `{code, message}` and `503`), the bundle budget and the system font (DFS-044), a live check read from the DOM rather than screenshots, the dev server and its proxy, and the docs (`CLAUDE.md`, `docs/design/ui.md`); every item names the ticket it comes from.
- **AC-4** Each of the four pipeline agent prompts states its stub and test-name rules language-neutrally, pointing to the archetype's guides, with Java and TypeScript as examples; no rule in them names `UnsupportedOperationException`, `@DisplayName` or `./gradlew build` as the only form.
- **AC-5** `./gradlew :server:npmTest` writes a JUnit report to `server/build/test-results/vitest/TEST-vitest.xml` beside its usual output; the report is deleted before each run and declared as the task's output.
- **AC-6** The gate reads that report: `Junit.readAll` returns its test cases, `TestResult` counts a failure of type `Error` with message `not implemented: <ID>` as a stub of `<ID>`, and for a `.ts` test case the traced name is the part after the last ` > `.
- **AC-7** The gate's source checks see the frontend: `.ts` and `.html` files under `server/frontend/src` (minus `src/app/api`) are production unless named `*.spec.ts`, which are tests; the stubs check finds a marker in a component; the disabled check finds `it.skip`, `xit`, `describe.skip` and `xdescribe`; the freeze check counts a changed `*.spec.ts` as a test change.
- **AC-8** `RedCheck` also runs `npmTest`, and `Build` treats a failed `npmTest` like a failed `test` task, so failing Vitest tests are judged by their reports rather than reported as a broken build.
- **AC-9** The gate's self-test has a case for each change in AC-6 to AC-8, its report cases built from a real Vitest test case, and passes.
- **AC-10** On an unmerged branch, a throwaway ticket (`PROBE-001`, outside both ID sequences, three criteria) with a stubbed component and a spec of story tests named `PROBE-001.AC-n ...` inside a `describe`, one per way of reaching a stub (AC-13), passes the gate end to end: `red` sees every story test failing with its stub, then, once implemented, `green`, `stubs` and `traceability` pass. (Amended 2026-11-01 while implementing: traceability checks a whole ticket, so the throwaway run needs its own ticket rather than one CCW-020 criterion.)
- **AC-11** `dry-run.md` traces each of DFS-041's decisions and each surprise met building DFS-037, DFS-038 and DFS-041 (listed in Context) to the checklist item or guide section that would have raised it, with none left untraced and no checklist item unused.
- **AC-12** `docs/design/ui.md` D7 says the component archetype exists (CCW-020) and that the gates now run the Vitest tests; `docs/design/story-orchestration.md` and the `tickets` skill give the TypeScript stub and test-name forms beside the Java ones; the `endpoint` archetype's "does not apply" points to `component`.
- **AC-13** With `server/frontend/src/testing/stub-surfacing.ts` (`provideStubSurfacing()`, `settle(fixture)`), a stub reached from a template, from a click handler through a Material harness, or in a generated-client Promise callback makes its test fail with the stub's `Error` itself; the gate counts files under a frontend's `src/testing/` as test code.

## Scenarios

How each scenario is checked:
- **Mechanically:** AC-1 (a listing and the frontmatter read by a script); AC-5 (a run of `npmTest`, the report inspected); AC-6 to AC-9 (the gate's self-test, its report cases fed real Vitest XML); AC-10 and AC-13 (the gate run on a throwaway branch, with one story test per way of reaching a stub, its output recorded in `verification.md`; plus a self-test case for `src/testing/`); AC-11 (a script counts the dry run's rows and the untraced or unused entries); AC-4 partly (a search of the four prompts for an unqualified Java-only rule).
- **By reading:** AC-2, AC-3, AC-12, and the wording behind AC-4 and AC-11.

```gherkin
Feature: Angular component archetype

  @AC-1
  Scenario: The archetype's files exist
    When the files under .claude/archetypes/component are listed
    Then they include archetype.md, design.md.tmpl, test-plan.md.tmpl, impl-guide.md, test-guide.md and skeletons/
    And skeletons/ holds a component .ts, its .html and its .spec.ts
    And archetype.md's frontmatter has name component, version 1, phases [design, test, implement, verify] and gates [design, done]

  @AC-2
  Scenario: The archetype says where it applies and where files go
    When archetype.md is read
    Then it applies to screens and components in server/frontend and refers endpoints to endpoint and use cases to use-case
    And "Where files go" names the feature folder, standalone components with signals, the generated client, the wrapper behind a token and the Error stub

  @AC-3
  Scenario: The checklist asks what DFS-037, DFS-038, DFS-041 and DFS-044 met
    When archetype.md's edge-case checklist is read
    Then it has items on the generated client and Promise timing, jsdom's missing APIs, Material harnesses, loading, empty and error states, the budget and font, live checks read from the DOM, the dev server and proxy, and the docs
    And every item names the ticket it comes from

  @AC-4
  Scenario: The agent prompts no longer assume Java
    When architect.md, test-author.md, implementer.md and verifier.md are searched for UnsupportedOperationException, @DisplayName and ./gradlew build
    Then every hit is an example beside a TypeScript one, or a pointer to the archetype's guides, never the only form a rule allows

  @AC-5
  Scenario: Vitest writes a fresh JUnit report
    Given server/build/test-results/vitest/TEST-vitest.xml holds a file from an earlier run
    When ./gradlew :server:npmTest --rerun runs
    Then the file is rewritten and lists all 21 current tests by their full names
    And the task declares the file as an output

  @AC-6
  Scenario: The gate reads a Vitest report and its stub
    Given a Vitest JUnit report with a test "Thing > CCW-020.AC-1 shows the thing" failing with type Error and message "not implemented: CCW-020"
    When the gate reads the reports
    Then that test is a stub failure of CCW-020 and its traced name is "CCW-020.AC-1 shows the thing"

  @AC-7
  Scenario: The source checks see TypeScript
    Given a component thing.ts containing "not implemented: CCW-020", a thing.spec.ts with it.skip, and a file under src/app/api
    When the stubs, disabled and freeze checks run
    Then stubs reports thing.ts, disabled reports thing.spec.ts, and freeze counts a changed thing.spec.ts as a test change
    And nothing under src/app/api is judged

  @AC-8
  Scenario: Failing Vitest tests are judged, not reported as a broken build
    Given the build output of a run where :server:npmTest failed because its tests failed
    When Build summarizes it
    Then the npmTest failure is not listed as a problem
    And RedCheck's command includes npmTest

  @AC-9
  Scenario: The gate's self-test covers the TypeScript cases
    When the gate's self-test runs
    Then it has a case for each of AC-6, AC-7 and AC-8, and every case passes

  @AC-10
  Scenario: A stubbed component goes red then green through the real gate
    Given an unmerged branch with a throwaway ticket PROBE-001, a stubbed component and a spec with describe("Probe") and it("PROBE-001.AC-1 ...") to it("PROBE-001.AC-3 ...")
    When the red check runs
    Then it passes with every story test failing on its stub
    When the component is implemented
    Then the green, stubs and traceability checks pass
    And the branch is deleted without merging

  @AC-11
  Scenario: The dry run traces every decision and surprise
    When dry-run.md is checked by a script
    Then every DFS-041 decision and every surprise listed in Context has a row naming a checklist item or guide section
    And every checklist item appears in at least one row

  @AC-12
  Scenario: The docs record the archetype and the TypeScript forms
    When docs/design/ui.md D7, docs/design/story-orchestration.md, the tickets skill and the endpoint archetype are read
    Then D7 names the component archetype (CCW-020) and says the gates run the Vitest tests
    And the orchestration design and the tickets skill give the TypeScript stub and test name beside the Java ones
    And the endpoint archetype refers screens to component

  @AC-13
  Scenario: A stub's error fails its test, however the test reaches it
    Given a stubbed component whose stubs are reached from its template, from a click handler and in a generated-client Promise callback
    And a spec with one story test per way, using provideStubSurfacing() and settle(fixture)
    When the tests run
    Then each story test fails with type Error and the stub's message "not implemented: <ID>"
    And the gate counts server/frontend/src/testing/stub-surfacing.ts as a test file
```

## Scope

**Touches:**
- `.claude/archetypes/component`: the new archetype (`archetype.md`, templates, guides, skeletons).
- `.claude/archetypes/endpoint/archetype.md`: "does not apply" points to `component`.
- `.claude/agents/architect.md`, `test-author.md`, `implementer.md`, `verifier.md`: language-neutral stub and test-name rules (decision 4).
- `.claude/skills/tickets/SKILL.md`: the test-name rule names the Vitest form.
- `tools/story-gate`: the report path, the TypeScript stub, the traced name, the source checks, `RedCheck`, `Build`, and self-test cases (decisions 1 and 6).
- `server/frontend/angular.json`: the `junit` reporter.
- `server/build.gradle.kts`: `npmTest` deletes and declares its report.
- `server/frontend/src/testing`: the stub-surfacing helper (decision 7).
- `docs/design/story-orchestration.md`: the stub and test-name rules name the TypeScript forms.
- `docs/design/ui.md`: D7.
- `docs/tickets/backlog/CCW-020-angular-component-archetype/dry-run.md`: the dry run (decision 3).

**Out of scope:**
- A live `/story run` (decision 3; the first real frontend pipeline ticket).
- End-to-end browser tests (Playwright or similar).
- Changes to the Java archetypes beyond the pointer in `endpoint`.
- The disk usage view (DFS-045) or any other product screen.

## Definition of done

- [x] Each scenario checked as the note above the scenarios says, recorded in `verification.md` as mechanical or by reading
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests), and the gate's self-test passes
- [x] No files changed outside the paths listed under Touches; AC-10's throwaway branch deleted unmerged
- [x] The CCW-018 harness-merge message sent to every other running session in `lanes.md` (dfs-backend, after the merge f431376; dfs-product told too)
- [x] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
