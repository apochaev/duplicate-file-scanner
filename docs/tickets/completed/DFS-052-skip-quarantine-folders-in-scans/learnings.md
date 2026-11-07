# DFS-052 learnings

- **L-1 (design): the ticket's CLI exit code contradicted the existing contract.** Decision 3 and AC-5 say a location in a quarantine makes the CLI exit 2, while Scope says "no CLI source change"; `ScanCommand` returns `ERROR` (1) for every `StartScan` failure (DFS-020.AC-13) and 2 is `USAGE`. Refinement wrote the exit code from memory instead of from `ScanCommand`'s constants. The architect kept exit 1 (design.md open note 6). Suggestion: when a ticket asserts an existing adapter's observable output (exit codes, HTTP status) with "no source change", refinement should check the claim against the adapter's constants.
- **L-2 (design): a predicate wired into a hot path breaks every scan at design.** Making the AC-2 "unchanged behavior" scenarios red requires the walker stub on every directory and the `ScanService` stub on every start, so 15 existing classes across all four projects fail at design (all but `LaunchScriptTest` directly on the stub, so only it needed declaring). Expected for a check on a shared path; the red gate's root-cause matching made it cheap. No change suggested.
- **L-3 (verify): a user decision that contradicts the ticket text leaves the ticket stale through every phase.** The user chose exit 1 at the design gate, but decision 3, AC-5 and the CLI refusal scenario still say exit 2 at verify, so the verifier must grade AC-5 against a decision recorded only in `design.md`, `test-plan.md` and `plan.json`. Suggestion: when the user overrides ticket wording at a gate, the orchestrator should file the ticket correction then (or carry it as an explicit close-time action item), so the scenario text and the test agree before verification.

### DFS-052.L-4: Refinement must check asserted adapter output against the adapter's constants

- Status: applied
- Impact: shared (touches a skill: `.claude/skills/tickets/SKILL.md`)
- Target: `.claude/skills/tickets/SKILL.md` (refine)
- Change: Add a refine checklist step: when a ticket asserts an existing adapter's observable output (exit code, HTTP status) and says "no source change", verify the value against the adapter's constants (e.g. `ScanCommand` ERROR/USAGE) before writing the acceptance criterion.
- Evidence: L-1 above: decision 3 and AC-5 said exit 2 while Scope said "no CLI source change"; `ScanCommand` returns ERROR (1); the user chose exit 1 at the design gate (design.md open note 6).
- Recurrences: 1 (DFS-052)
- Recommendation: apply; a cheap check that prevents a design-gate deviation and a user interruption.
- Decision: applied 2026-11-07 by the user; commit `docs: apply DFS-052.L-4`

### DFS-052.L-5: Correct the ticket when the user overrides its wording at a gate

- Status: parked
- Impact: shared (touches the orchestrator: `.claude/skills/story/SKILL.md`, `approve` mode)
- Target: `.claude/skills/story/SKILL.md` (approve with a decision), or CLAUDE.md Tickets
- Change: When the user's gate decision contradicts ticket text, the orchestrator files the ticket correction at that gate, or records it as an explicit close-time action item, so the ticket, tests and verification agree.
- Evidence: L-3 above: decision 3, AC-5 and the CLI refusal scenario still said exit 2 at verify; plan.json verify note "AC-5 graded against exit 1 (user decision), ticket text says 2".
- Recurrences: 1 (DFS-052)
- Recommendation: park; a single occurrence and the verifier handled it correctly. Revisit if it recurs.
- Decision: parked 2026-11-07 by the user
