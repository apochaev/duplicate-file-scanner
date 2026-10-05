# DFS-005 learnings

- (architect) The `pipeline-stage` archetype was stretched to cover a service that composes the stages (ticket decision 1). None of its skeletons fit, and checklist items 2 and 7 had nothing to say about a composing service. The `design.md.tmpl` wording ("this stage") also reads oddly for it. When DFS-018 is refined, consider a `use-case`/`service` archetype that has its own checklist: stop/cancellation, the order of listener events, validation that fails fast before side effects, and stateless reuse.
- (architect) Checklist item 10 still says "before DFS-005's progress listener exists". Now that the listener exists, the item could ask for a drop to be reported through a stage's `onDrop` sink.
- (architect) For a record's compact constructor, "stub throws" means every use of the new type fails red, including a test that only builds an expected value (for example `ScanEvent.GroupDone`). That is intended here, but the archetype does not say it. It only works because no pre-existing test touches the new types.
- (implementer) Two tests appear to have authoring defects, not implementation bugs; reported rather than edited (per the archetype's rule to never edit a test). Both were verified by implementing exactly what design.md's algorithm prescribes and observing the actual vs. expected values:
  - `ScanPipelineTest.stoppingDuringWalkThrowsCancellationAndSkipsNextLocation` (AC-14) asserts `walker.listenerReturns().subList(0, 3)` equals `[false, false, false]`, but design.md's own algorithm (check the stop signal, return `false` only once it reads true) necessarily produces `[true, true, false]` for `stopAfterChecks(2)` — the walk listener returns `true` (keep going) for the first two events and only `false` for the third, matching the test's own failure message ("the walk listener should return false starting at the 3rd event"). The hardcoded expected list looks like it should have been `List.of(true, true, false)`.
  - `ScanPipelineEndToEndTest.realPipelineOnRealFiles` (AC-17) writes `near1.bin` with `writePattern(near1, size, -1)`, the same "no byte changed" pattern used for `same1.bin`/`same2.bin`, so `near1.bin` ends up byte-for-byte identical to `same1.bin`/`same2.bin` rather than merely same-sized. A correct pipeline correctly places `near1.bin` in the same duplicate set as `same1`/`same2`/`link`, which fails the test's assertion that no set holds `near1.bin`. The near-duplicate pair should likely use a pattern distinct from the "same" pair's (for example base every byte on `i + 1` for `near1`/`near2`, with `near2` also flipping its middle byte), so `near1` differs from `same1` but matches `near2` except at the middle byte.
- (orchestrator) The two defects above were fixed by hand in the main session (commit 23db805) and recorded as the tests phase's attempt 2, verified with `green` rather than `test-phase` because `src/main` already held the real implementation. The implement phase was then reconciled against its existing commit without a new implementer run. The resume protocol covers a phase blocked by its own failures and a phase reopened by gate feedback, but not "the implement gate failed because of a test defect, and a human fixed the tests"; defect routing to `test-author` from the `implement` phase has no documented path either.
- (orchestrator, relaying the verifier) The orchestrator's verifier launch prompt asked it to append to `learnings.md`, but the verifier role forbids writing anything other than the ticket and `verification.md`. The launch prompt and the role should agree; until then, the orchestrator records the verifier's friction here.
- (orchestrator, relaying the verifier) Test-author: for real-file tests whose results go through `toRealPath()`, build the expected paths from the resolved root. AC-17 builds them from the unresolved `@TempDir`, which passes on Windows but can fail on macOS (`/var` → `/private/var`) or with 8.3 short temp paths.
- (orchestrator, relaying the verifier) `pipeline-stage` archetype: near-duplicate fixtures must use a byte pattern distinct from the identical pair's, or the "near" file is a true duplicate (defect 2).

## Entries

### DFS-005.L-1: The test-author ran out of turns on a large story

- Status: proposed
- Impact: local (`pipeline-stage` archetype)
- Target: `.claude/archetypes/pipeline-stage/archetype.md`, `models.test-author.maxTurns`
- Change: raise the test-author's `maxTurns` from 30 to 40, matching the implementer.
- Evidence: `plan.json`, tests attempt 1: hit the 30-turn cap with the tests written but no `test-plan.md` and no report; the same agent was resumed with SendMessage to finish. The story's suite was 29 tests in 5 files. The step-6 resume rule in `.claude/skills/story/SKILL.md` exists because CCW-005 hit the same cap, though that occurrence was never logged as an entry.
- Recurrences: 1 (DFS-005)
- Recommendation: apply. It costs one line, and it removes a manual resume step.
- Decision:

### DFS-005.L-2: A near-duplicate fixture can be an exact copy of the identical pair

- Status: proposed
- Impact: local (`pipeline-stage` archetype)
- Target: `.claude/archetypes/pipeline-stage/archetype.md`, edge-case checklist item 12
- Change: item 12 additionally asks that the near-duplicate pair use a byte pattern different from any identical-pair fixture in the same test, so that "near" cannot accidentally equal "same".
- Evidence: `plan.json` defect 2: AC-17 (`ScanPipelineEndToEndTest`) wrote `near1.bin` with the same pattern as `same1.bin`/`same2.bin`, so a correct pipeline grouped it with them and the green gate failed. Fixed in `23db805`. Related but distinct: DFS-002.L-7 (discrimination tests for an adapter).
- Recurrences: 1 (DFS-005)
- Recommendation: apply.
- Decision:

### DFS-005.L-3: An expected value in a stop test contradicted its own message

- Status: proposed
- Impact: local (`test-guide.md`)
- Target: `.claude/archetypes/pipeline-stage/test-guide.md`
- Change: derive the expected listener-return sequence of a stop or cancellation test from the stop rule written in `design.md`, and keep the failure message consistent with it.
- Evidence: `plan.json` defect 1: AC-14 (`ScanPipelineTest.stoppingDuringWalkThrowsCancellationAndSkipsNextLocation`) asserted `[false, false, false]` where the design gives `[true, true, false]`; its own message said "starting at the 3rd event". Fixed in `23db805`.
- Recurrences: 1 (DFS-005)
- Recommendation: park. A one-off slip that the green gate already caught; a guide bullet for it is noise until it recurs.
- Decision:

### DFS-005.L-4: Real-file tests build expected paths from the unresolved temp directory

- Status: proposed
- Impact: local (`test-guide.md`)
- Target: `.claude/archetypes/pipeline-stage/test-guide.md`, the real-file and NIO adapter guidance
- Change: when results pass through `toRealPath()`, build expected paths from `tempDir.toRealPath()`, not the raw `@TempDir`.
- Evidence: `verification.md`, concern 1: AC-17 builds its expected paths from the unresolved `@TempDir` while `ScanPipeline` reports paths under `NioLocationResolver`'s `toRealPath()`. It passes on Windows but would fail on macOS (`/var` resolves to `/private/var`) or with 8.3 short temp paths. AC-17 itself still carries this and needs a small follow-up fix.
- Recurrences: 1 (DFS-005)
- Recommendation: apply.
- Decision:

### DFS-005.L-5: The story skill tells every agent to append learnings, but the verifier role forbids it

- Status: proposed
- Impact: shared (`.claude/skills/story/SKILL.md`, `.claude/agents/verifier.md`)
- Target: `.claude/skills/story/SKILL.md`, "Story folder and `plan.json`" paragraph; `.claude/agents/verifier.md`, its write boundaries
- Change: make the two agree. Preferably add `learnings.md` (append only) to the verifier's allowed writes, as every other phase agent has; otherwise have the verifier report friction in `verification.md` and the orchestrator relay it.
- Evidence: the DFS-005 verifier's final report: its launch prompt asked it to append to `learnings.md`, but its role forbids writing anything other than the ticket and `verification.md`, so it reported three friction notes instead and the orchestrator relayed them by hand (the "(orchestrator, relaying the verifier)" notes above).
- Recurrences: 1 (DFS-005)
- Recommendation: apply.
- Decision:

### DFS-005.L-6: A test defect found at the implement gate has no documented route

- Status: proposed
- Impact: shared (`.claude/skills/story/SKILL.md`)
- Target: `.claude/skills/story/SKILL.md`, resume protocol step 9 (defect routing) and the reopened-tests procedure under `approve <ID> [feedback]`
- Change: when an implement-gate failure is traced to a test rather than the implementation, route the defect to `test-author` and reopen `tests` with the existing send-back procedure: verify the reopened phase with `green`, and set `implement` to `in-progress` so it is reconciled against its existing commit instead of rerun. Today step 9 routes every implement failure to the implementer, who may not edit tests.
- Evidence: `plan.json` defects 1 and 2 (both routed to `test-author` by hand), the `tests` attempt-2 note, and the implement run's reconcile note. Related but distinct trigger: DFS-002.L-2 and DFS-002.L-3 (applied) cover a phase reopened by human feedback at a gate, not by a gate failure.
- Recurrences: 1 (DFS-005)
- Recommendation: apply.
- Decision:

### DFS-005.L-7: Checklist item 10 still refers to a listener that "does not exist yet"

- Status: proposed
- Impact: local (`pipeline-stage` archetype)
- Target: `.claude/archetypes/pipeline-stage/archetype.md`, edge-case checklist item 10
- Change: replace "How is that reported before DFS-005's progress listener exists" with a question about reporting the drop through the stage's `onDrop` sink.
- Evidence: the architect's note above; DFS-005 added `ScanListener` and the `onDrop` constructors on `PartialHashFilter`, `FullHashConfirmer` and `HardLinkDetector`. DFS-002.L-5 (parked) proposes splitting the same item; decide the two together.
- Recurrences: 1 (DFS-005)
- Recommendation: apply, together with DFS-002.L-5.
- Decision:

### DFS-005.L-8: `pipeline-stage` doesn't fit a service that composes stages

- Status: proposed
- Impact: shared (`.claude/archetypes/`)
- Target: a new `.claude/archetypes/use-case/` (a CCW ticket)
- Change: an archetype for use cases and composing services, with its own checklist: stop and cancellation, the order of listener events, validation that fails before any side effect, and stateless reuse across concurrent calls.
- Evidence: the architect's note above: none of `pipeline-stage`'s skeletons fit `ScanPipeline`, checklist items 2 and 7 had nothing to say about it, and `design.md.tmpl`'s "this stage" wording read oddly.
- Recurrences: 1 (DFS-005)
- Recommendation: decide while refining DFS-018, the next service-shaped ticket.
- Decision:
