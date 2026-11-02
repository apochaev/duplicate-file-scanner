# CCW-020 verification

## Product owner review

What to look at:
- **The archetype:** `.claude/archetypes/component/`. In `archetype.md`:
  - "Applies to" and "Where files go"
  - the 13-item checklist, each item naming its source ticket
  - the extra done-items

  Plus the design and test-plan templates, the two guides, and three skeletons (component, template, spec).
- **The stub-surfacing helper:** `server/frontend/src/testing/stub-surfacing.ts` (decision 7, found while building). Without it, a stub reached from a template, a click handler or a Promise callback never fails its test (see AC-13 below). Every story spec must use it, and the gate freezes it like a spec.
- **The gate:** `tools/story-gate`. It reads Vitest's report, accepts the TypeScript stub, reads the test's own name after the last ` > `, checks `.ts`/`.html` sources and `src/testing/`, runs `npmTest` at the red step, and accepts a spec file name under "Expected breakage".
- **The four agent prompts** now state the stub and test-name rules for both languages.
- **Two changes beyond the plan, both inside `touches`:**
  - **Stale version pins.** The self-test case `CCW-015.AC-7-instructions` pinned archetype versions that later learnings bumped, so `selftest` had been failing on `main`. The pins are gone (commit f5be643).
  - **`contains` cases survive the close.** A `contains` case can name `docs/tickets/*/...`, so the dry-run case still finds its file once the ticket moves to `completed/`.
- **AC-10's wording was amended while implementing:** the gate run uses a throwaway ticket, `PROBE-001`, because traceability checks a whole ticket.

Verdict per criterion: all 13 pass.
- **Mechanically:** AC-1, AC-5, AC-6, AC-7, AC-8, AC-9, AC-10, AC-11, AC-13, and AC-4 in part.
- **By reading:** AC-2, AC-3, AC-12, and the wording of AC-4.

## Checks

All on branch `tickets/CCW-020-angular-component-archetype`, at 7c84be7 unless noted, 2026-11-01.

### AC-1, AC-11: archetype files and dry run (mechanical)

A script (`ccw020_check.py`, adapted from CCW-019's) read the archetype and the dry run:

```
files: ['archetype.md', 'design.md.tmpl', 'impl-guide.md', 'skeletons', 'test-guide.md', 'test-plan.md.tmpl']
skeletons: ['feature.html.tmpl', 'feature.spec.ts.tmpl', 'feature.ts.tmpl']
checklist items: 13 ['1', ..., '13']
trace rows: 19 ['D1'..'D6', 'F1', 'F2', 'S1'..'S11']
items traced by rows: ['1', ..., '13'] unused: []
RESULT PASS
```

- The frontmatter is `name: component`, `version: 1`, `phases: [design, test, implement, verify]`, `gates: [design, done]`, with `models`.
- Every checklist item names a source ticket.
- Every DFS-041 decision (D1 to D6) and every surprise (S1 to S10 from Context, plus S11 from decision 7) has a row.
- No checklist item is unused.

### AC-2, AC-3: what the archetype says (by reading)

- "Applies to" covers screens and components in `server/frontend`. It refers endpoints to `endpoint`, use cases to `use-case`, wiring to `wiring`, and leaves out the security rules and the frontend build.
- "Where files go" names:
  - the feature folder
  - standalone components with signals
  - the generated client as the only API access, never edited
  - the wrapper behind an `InjectionToken`
  - the `Error` stub
  - the `src/testing/` helper
- The checklist has all the items AC-3 lists: the generated client, Promise timing, jsdom's missing APIs, Material harnesses, the states (including `{code, message}` and `503`), the budget and font, the DOM live check, the dev server and proxy, and docs. It also has items on stubs being reached and surfaced, where the screen sits, input, and live updates.

The `contains` cases `CCW-020.AC-2-applies-and-files` and `CCW-020.AC-3-checklist` pin those phrases.

### AC-4: agent prompts (partly mechanical)

Searching the four prompts for `UnsupportedOperationException`, `@DisplayName` and `gradlew build`, minus lines that also name TypeScript, Vitest or `Error`, leaves three hits:
- `test-author.md:32` and `:36`: the Java example, followed by its Vitest example.
- `implementer.md:47`: `./gradlew build`, which also runs the Vitest tests, as step 4 now says.

No rule names a Java form as the only one. The case `CCW-020.AC-4-agent-prompts` pins the new wording.

### AC-5: the Vitest JUnit report (mechanical)

I seeded `server/build/test-results/vitest/TEST-vitest.xml` with the text `stale` and ran `./gradlew :server:npmTest --rerun`. The file was rewritten as a JUnit report listing 21 test cases, each named `<describe> > <it>`. `build.gradle.kts` declares it as the output `junitReport` and deletes it in `doFirst`. The case `CCW-020.AC-5-vitest-junit-report` pins that setup.

### AC-6 to AC-9, AC-13: the gate's self-test (mechanical)

`java tools/story-gate/StoryGate.java selftest` reports: `PASS selftest: 125 cases match their expected verdicts; every scenario of CCW-003, CCW-009, CCW-014, CCW-015, CCW-020 is covered`.

The CCW-020 cases, by criterion:
- **AC-6:**
  - `vitest-test-phase`: a real Vitest report with nested `describe`s; red and traceability pass.
  - `vitest-wrong-error`: a `TypeError`, a non-stub `Error` and a regression are each reported.
  - `declared-spec-file`: `app.spec.ts` under Expected breakage is accepted.
- **AC-7:**
  - `stubs-frontend`: a marker in a component's `.ts` and `.html` is found. Markers in the spec, `src/app/api`, `node_modules` and `.angular` are not.
  - `disabled-frontend`: `it.skip`, `xit`, `describe.skip` and `xdescribe` are found; a comment and `it.skipIf` are not.
  - `disabled-scoped`: only the story's own spec is judged, matched by path.
  - `freeze-frontend`: a changed spec is caught.
- **AC-8:**
  - `red-run-npmtest`: the real `RedCheck` runs a fake `gradlew`, which writes the Vitest report only when asked for `cleanTest` then `npmTest`.
  - `npmtest-failed-build`: a failed `:server:npmTest` is not listed as a build problem.
- **AC-9:** `selftest-cases` checks that the cases above exist.
- **AC-13:**
  - `testing-not-production`, `testing-frozen`: `src/testing/` is test code.
  - `probe-stubs-surface`: the real report of the gate run below.

### AC-10, AC-13: the real gate run on a throwaway ticket (mechanical)

- **Setup:** a worktree on branch `probe/ccw-020-gate-run`, from f77921b, held:
  - a ticket `PROBE-001` with three criteria and its `test-plan.md`
  - a component `src/app/probe/probe.ts` with three stubs, `throw new Error('not implemented: PROBE-001')`: one reached from the template, one from a click handler through `MatButtonHarness`, one in the callback of `SessionService.getSession()`'s Promise
  - a spec with `describe('Probe')` and `it('PROBE-001.AC-1 ...')` to `AC-3`, using `provideStubSurfacing()` and `settle(fixture)`

  The real checks, at the stubbed commit:

  ```
  PASS red PROBE-001: 3 story tests fail with stubs, 1 other tests fail on the ticket's stub, 0 fail in declared classes, 575 other tests pass (3 skipped)
  ```

  The "1 other" is Vitest's "Uncaught Exception: not implemented: PROBE-001" entry, for the error Angular also threw from the template. It carries the ticket's stub, so it is accepted.
- **After implementing the three methods:**

  ```
  PASS green PROBE-001: build and tests green: 575 passed, 3 skipped
  PASS stubs PROBE-001: no stub markers in 144 production files
  PASS traceability PROBE-001: 3 criteria traced through 3 scenarios to tests and test-plan.md
  ```

- **Without the helper,** the probe found (decision 7) that all three tests failed with `AssertionError`, and the stub showed only as unhandled errors. Angular's `rethrowApplicationErrors: true` did not change that.
- **Cleanup:** the trimmed red and green reports are kept as self-test cases (`CCW-020.AC-10-probe-red`, `-probe-green`, `CCW-020.AC-13-probe-stubs-surface`). The worktree was removed and the branch deleted unmerged (`Deleted branch probe/ccw-020-gate-run (was efc0934)`). `PROBE-001` never reached `main`.

### AC-12: docs (by reading)

- `docs/design/ui.md` D7 names the component archetype (CCW-020) and what the gates now do for it.
- `docs/design/story-orchestration.md` gives the TypeScript stub at the red gate and in the stubs rule, and the Vitest test name in the naming rule.
- The `tickets` skill's naming rule names the Vitest form.
- The `endpoint` archetype refers screens to `component` (its version is now 2).

The case `CCW-020.AC-12-docs` pins those phrases.

### Build and scope

- `./gradlew build`: `BUILD SUCCESSFUL`. Vitest ran 2 test files (21 tests), the report was written, and there was no budget line in the output.
- `java tools/story-gate/StoryGate.java touches CCW-020`: `PASS touches CCW-020: 98 changed files, all inside touches`.
- The real repo's `stubs` and `disabled` checks pass with the frontend now scanned: 143 production files, no markers.

## Definition of done

- [x] Each scenario checked as the note above the scenarios says, recorded here as mechanical or by reading
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests), and the gate's self-test passes
- [x] No files changed outside the paths listed under Touches; AC-10's throwaway branch deleted unmerged
- [ ] The CCW-018 harness-merge message sent to every other running session in `lanes.md` (sent at the merge, by `/tickets done`)
- [x] `verification.md` opens with the `## Product owner review` section
