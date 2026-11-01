## design (architect) — 2026-11-01

- **Shared helper extracted from existing code vs. "every stub throws".** Ticket decision 5 asks for a classification helper shared with NioFileTreeWalker. Stubbing it and refactoring the walker at design would break every scan test before implementation; writing it for real would be implementing at design. Resolved by adding the helper as a stub and leaving the walker untouched until the implementer rewires it. The archetype could say so for "extract and share" tickets.
- **Turn budget.** The architect ran out of turns before writing design.md (a heredoc batch failed and had to be redone with Write). For a ticket with several new types and a refactor, 25 turns is tight; batching writes earlier would help.

## verify (verifier) — 2026-11-01

- **"Refactor with identical behavior" needs a pinning test, not only unchanged old tests.** NioFileTreeWalker's move onto EntryKind changed the check order (`isOther()` before `isRegularFile()`). It is equivalent only because the JDK's attribute flags are mutually exclusive, which the verifier had to argue from `WindowsFileAttributes`. A small `EntryKind` unit test over a fake `BasicFileAttributes` (each flag combination maps to its kind, reason and detail) would have pinned it cheaply. The test-author could add one for any "extract a shared helper" ticket.
- **Ticket-level DoD "no test uses Thread.sleep" is ambiguous** when a touched test class already holds one (`ScannerAutoConfigurationTest.await`, DFS-031). The verifier read it as "no new sleep in this ticket's tests". Ticket templates could say "no test added by this ticket".

## Entries

### DFS-032.L-1: use-case architect's 25-turn cap is too tight for tickets with several types and a refactor

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/archetype.md` (`models:` architect `maxTurns`)
- Change: raise the architect's `maxTurns` from 25 to 35 for use-case, and add one line to the design guidance: write design.md's Summary early and batch stub writes, so a cap stop leaves a usable design.
- Evidence: DFS-032 plan.json design run note "resumed once via SendMessage after hitting the 25-turn cap with stubs written but no design.md" (40 turns in all); architect note "Turn budget". Same shape as DFS-026.L-7 (verifier cap, pipeline-stage).
- Recurrences: 1 (DFS-032)
- Recommendation: apply, the cap stopped the phase before its main artifact existed.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-022.L-1, L-2, L-3, DFS-032.L-1, L-2, L-3, L-4`

### DFS-032.L-2: archetype gives no rule for a shared helper extracted from existing code

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/archetype.md` ("Where files go")
- Change: add: when a ticket extracts a helper shared with existing code, the architect adds the helper as a stub and leaves the existing consumer untouched; the implementer moves the consumer onto it, and the consumer's existing tests must pass unchanged (the freeze and green checks prove it).
- Evidence: DFS-032 architect note "Shared helper extracted from existing code vs. every stub throws"; resolved that way (EntryKind stubbed at design 73dff48, NioFileTreeWalker moved in implement 2e5e8c5, walker tests unchanged).
- Recurrences: 1 (DFS-032)
- Recommendation: apply, the resolution worked first time and is cheap to write down.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-022.L-1, L-2, L-3, DFS-032.L-1, L-2, L-3, L-4`

### DFS-032.L-3: a refactor claimed to keep behavior identical needs a test pinning the shared helper

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/test-guide.md` ("Cover, per scenario")
- Change: add: when design.md moves existing code onto a new shared helper, the test-author writes a unit test pinning the helper's mapping over fake inputs (each attribute combination to its kind, reason and detail), so equivalence is tested, not argued.
- Evidence: DFS-032 verifier note: the walker's check order changed (isOther before isRegularFile) and was judged equivalent only by reasoning about WindowsFileAttributes; verification.md Known limits lists the detail texts as unpinned.
- Recurrences: 1 (DFS-032)
- Recommendation: apply, closes an argued-not-tested gap for the next extract-and-share ticket.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-022.L-1, L-2, L-3, DFS-032.L-1, L-2, L-3, L-4`

### DFS-032.L-4: the "no test uses Thread.sleep" done-item is ambiguous when a touched test class already has one

- Status: applied
- Impact: local
- Target: `.claude/archetypes/use-case/archetype.md` ("Extra done-items", the Thread.sleep line)
- Change: reword to "No test this ticket adds or changes uses Thread.sleep", so an existing sleep in a touched class (ScannerAutoConfigurationTest.await, DFS-031) neither blocks the item nor needs a reading.
- Evidence: DFS-032 verifier note and verification.md (ticked on the reading "no new sleep"); DFS-022's verifier made the same reading (its verification.md, done-item note).
- Recurrences: 2 (DFS-032: verifier ticked on an interpretation; DFS-022: the same interpretation, same test class)
- Recommendation: apply, a one-line wording fix two verifiers already worked around.
- Decision: applied 2026-11-01 by the user; commit `docs: apply DFS-022.L-1, L-2, L-3, DFS-032.L-1, L-2, L-3, L-4`
