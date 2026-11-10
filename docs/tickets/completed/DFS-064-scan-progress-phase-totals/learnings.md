# DFS-064 learnings

- **Endpoint archetype, spec order trap.** `:server:updateOpenApi` runs on the test classpath, which includes the frontend build, so it fails (`TS2561` and `TS2353` on literals) when frontend literals already carry the new fields the old spec lacks. Step order for a changed record: write the stubs, run `updateOpenApi` with the old frontend literals, then add the literals. `archetype.md` checklist item 7 and `design.md.tmpl` say the design fixes the frontend, but not in which order.
- **Endpoint archetype, red-gate blast radius.** When the stub sits on the read path of a shared record (here every `ScanProgress` build), most existing API test classes fail red with the stub. They pass the gate on their own (the stub is in each failure), but the architect has to check each class's report to say so; a one-line shell loop counting `<failure` against the stub message did it.
- **Ticket gap.** AC-5 says the progress events include `finishedAt`, but decision 3 puts `finishedAt` on `ScanEntry` and the stream sends `ScanProgress`. Raised in `design.md` under Open design notes for the design gate.
- **Ticket gap.** The core sends no event when a scan ends, so decision 3's "stamp when the scan ends" cannot be a callback inside `server`; the design uses a 50 ms sweep plus a read-time stamp.
- **Verifier, fakes must replay the core's real event order.** `ScanRegistryPhasesTest`'s fake core sent `GroupDone` only in HARD_LINKS, but `ScanPipeline` sends `GroupDone` after every group of every stage, PARTIAL_HASH and FULL_HASH included. The implementation's `GroupDone` branch zeroed `files`/`bytes` for any phase, so every test passed while a real scan lost both hash phases' totals (found only by the by-hand run). Test-author: when a fake stands in for the core's listener events, copy the sequence from the core (`ScanPipeline`) or add one real-scan HTTP assertion on every field the criterion names, not just the first phase's.
- **Verifier, background scheduler in a unit-constructed component.** The package-private `ScanRegistry(…, Clock)` constructor starts the 50 ms sweeper, so registry unit tests race it on the fake clock; they pass only because each test moves the clock before setting the terminal status. A constructor flag (or a separate `start()`) that leaves the scheduler off for tests would make them deterministic by construction.
- **Verifier, by-hand fixture size for observing progress.** A scan of under 1 MB finishes before the first SSE event or GET poll, so "phases growing" cannot be seen. Three identical 400 MB files (about 1.2 GB, written to the scratchpad with `head -c ... /dev/urandom` and `cp`) gave about 4 s of FULL_HASH, enough for a `curl` GET loop to catch an intermediate snapshot with the running phase `finished:false`. Use a fixture of this size whenever a done-item asks to see progress change.

## Entries

### DFS-064.L-1: Endpoint archetype: spec regeneration order for a changed record

- Status: applied
- Impact: local (`endpoint` archetype)
- Target: `.claude/archetypes/endpoint/archetype.md`, edge-case checklist item 7, and `design.md.tmpl`
- Change: add the step order for a changed record: write the stubs, run `:server:updateOpenApi` while the frontend literals still lack the new fields, then add the literals. `updateOpenApi` runs on the test classpath, which includes the frontend build, so it fails (TS2561, TS2353) if the literals already carry fields the old spec lacks.
- Evidence: DFS-064 design phase: the architect hit the failure, reverted and reapplied its own literal edits (first note in this file).
- Recurrences: 1 (DFS-064)
- Recommendation: apply, a one-line ordering note prevents a repeatable failure.
- Decision: applied 2026-11-10 by the user; commit `docs: apply DFS-064.L-1, DFS-064.L-2, DFS-064.L-3`

### DFS-064.L-2: Test-author: fakes must replay the core's real event order

- Status: applied
- Impact: shared (agent prompt `test-author.md`)
- Target: `.claude/agents/test-author.md`, and `.claude/archetypes/endpoint/test-guide.md`
- Change: when a fake stands in for the core's listener events, its sequence is copied from the core (for example `ScanPipeline`), or the story adds one real-scan HTTP assertion on every field the criterion names, not only the first phase's.
- Evidence: DFS-064 `plan.json` defect 1: `ScanRegistryPhasesTest`'s fake sent `GroupDone` only in HARD_LINKS, the core sends it after every group of every stage, so the implementation zeroed both hash phases' totals while every test passed. Found only by the by-hand run; it cost a second tests run (15 turns), a second implement (7 turns) and a second verify (22 turns).
- Recurrences: 1 (DFS-064)
- Recommendation: apply, the defect escaped every automated gate.
- Decision: applied 2026-11-10 by the user; commit `docs: apply DFS-064.L-1, DFS-064.L-2, DFS-064.L-3`

### DFS-064.L-3: Verifier: use a fixture of about 1 GB to observe progress change

- Status: applied
- Impact: shared (agent prompt `verifier.md`)
- Target: `.claude/agents/verifier.md`
- Change: when a done-item asks to see progress change, use a fixture of about 1 GB (three identical 400 MB files), because a scan of under 1 MB finishes before the first poll or stream event.
- Evidence: DFS-064 verify attempt 1 could not see `phases` growing on a small fixture; attempt 2 with about 1.2 GB gave about 4 s of FULL_HASH and showed it.
- Recurrences: 1 (DFS-064)
- Recommendation: apply, cheap and repeatable.
- Decision: applied 2026-11-10 by the user; commit `docs: apply DFS-064.L-1, DFS-064.L-2, DFS-064.L-3`

### DFS-064.L-4: Gherkin fence closes after the first scenario

- Status: parked
- Impact: shared (touches a skill or the archetype ticket template)
- Target: `.claude/skills/tickets` (refine), or a `story-gate` lint
- Change: add a refine-time check that every `@AC-n` scenario sits inside the Gherkin fence, which closes only after the last scenario.
- Evidence: DFS-064 ticket: the "```gherkin" block opens at line 68 and closes at line 78, after the first scenario; the `@AC-2` to `@AC-5` scenarios (lines 80-133) sit outside the fence. Unchanged since the ready commit 92c6584, through design, tests and review.
- Recurrences: 1 (DFS-064)
- Recommendation: park, one occurrence and cosmetic; it would matter if a tool parses scenarios from the fenced block.
- Decision: parked 2026-11-10 by the user
