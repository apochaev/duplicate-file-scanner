# DFS-030 learnings

- **architect:** `touches` named only `ScanPipeline.java` in `application` main, so the per-folder accumulator had to be a nested class (`ScanPipeline.FolderCounter`) instead of its own file the way `SizeIndex` is. When a pipeline-stage ticket adds an accumulator, refinement could list its file in `touches` (or say explicitly that it goes inside an existing file).
- **architect:** the ticket's list of existing `new ScanResult(...)` callers (decision 2) missed `CliApplicationTest`, which asserts the JSONL line types `["set","summary"]` and so has to change with the new `scan`/`folder` lines. It sits inside the `cli` test `touches`, so this ticket is not blocked, but a ticket that changes an output format should search for tests asserting that format, not just for constructor callers.
- **test-author:** the design asks for a `@Disabled` heap benchmark, but DisabledCheck rejects any `@Disabled` in the story tests; wrote it as a plain method (as DFS-001 did). Real-scan AC-9 test was dropped: a real scan on the stub ends FAILED with empty output, so it cannot fail with the raw stub exception.

- DFS-030: Instant.toString() pads fractions to 3/6/9 digits; design said to use it, tests want minimal fractions, so ScanCommand.iso trims trailing zeros.
- **verifier:** `overlaps.sql` stops roll-up at the roots with `starts_with(anc, root || '/')`. A drive root (`F:\` becomes `F:/`) or `/` already ends in `/`, so nothing matches and `folders` comes out empty. The DuckDB done-item used one fixtures root only, so it could not catch this. A done-item that hand-checks a path-prefix query should also try a drive-root (or filesystem-root) root.
- **verifier:** running `overlaps.sql` from Python DuckDB needs `con.execute(script)` followed by `con.fetchall()` before the next query (DuckDB 2.0 keeps the multi-statement result open), and `PYTHONIOENCODING=utf-8` on a Windows console for the box-drawn tables.

## Entries

### DFS-030.L-1: `touches` naming only the host file forced the folder counter into a nested class

- Status: applied
- Impact: local (one archetype)
- Target: `.claude/archetypes/pipeline-stage/archetype.md`, checklist item 1 (Accumulation)
- Change: add: when the stage accumulates, refinement either lists the accumulator's own file in `touches` (as `SizeIndex` has its own file) or records in a decision that it goes inside an existing file, so the architect is not forced into a nested class by omission.
- Evidence: DFS-030 architect note above; `ScanPipeline.FolderCounter` is a package-private nested class because `touches` named only `ScanPipeline.java` in `application` main (design commit 19d638e).
- Recurrences: 1 (DFS-030)
- Recommendation: apply; a one-line hint, and it avoids a design compromise nobody chose.
- Decision: applied 2026-10-31 by the user; commit `docs: apply DFS-030.L-1, L-2, L-3 [DFS-030]`

### DFS-030.L-2: Refinement listed constructor callers but not tests asserting the changed output format

- Status: applied
- Impact: local (the `tickets` skill's refine guidance)
- Target: `.claude/skills/tickets/SKILL.md`, refine mode
- Change: add: when a ticket changes an output format (JSONL lines, a report layout), refine searches the test trees for tests asserting that format (line types, field names, literals) as well as for callers of changed constructors, and names them in the ticket.
- Evidence: DFS-030 decision 2 listed four `new ScanResult(...)` callers; the architect found `CliApplicationTest` (asserts line types `["set","summary"]`) missing (architect note above). Downstream counterpart for test-authors: DFS-027.L-4.
- Recurrences: 1 (DFS-030)
- Recommendation: apply.
- Decision: applied 2026-10-31 by the user; commit `docs: apply DFS-030.L-1, L-2, L-3 [DFS-030]`

### DFS-030.L-3: Both impl-guides still tell benchmarks to be `@Disabled`, which DisabledCheck rejects

- Status: applied
- Impact: shared (two archetypes' guides)
- Target: `.claude/archetypes/pipeline-stage/impl-guide.md:16`, `.claude/archetypes/use-case/impl-guide.md:19`
- Change: replace "`@Disabled` (or otherwise excluded-from-default-run)" with "a plain class with a `main` method, no `@Test` or `@Disabled`", matching `implementer.md` (DFS-026.L-5); the `disabled` check rejects `@Disabled` in a story's tests.
- Evidence: DFS-030 design and ticket decision 10 asked for a `@Disabled` benchmark; the test-author wrote a plain method instead (test-author note above); `grep -rn Disabled .claude` shows only the two guides still say it. Follows DFS-001.L-3 and DFS-026.L-5.
- Recurrences: 1 (DFS-030)
- Recommendation: apply; a stale sentence, two files.
- Decision: applied 2026-10-31 by the user; commit `docs: apply DFS-030.L-1, L-2, L-3 [DFS-030]`

### DFS-030.L-4: A done-item hand-checking a path-prefix query used only one ordinary root

- Status: proposed
- Impact: shared (an agent prompt)
- Target: `.claude/agents/verifier.md`
- Change: add: when a done-item hand-checks code that matches paths by prefix, also try a drive root (`C:\`) and the filesystem root (`/`), whose trailing separator breaks naive `root || '/'` checks.
- Evidence: `overlaps.sql` roll-up was empty for drive roots; the verifier found it by trying one, after the done-item's single fixtures root passed (verifier note above; fixed in 9b619d4).
- Recurrences: 1 (DFS-030)
- Recommendation: park; one data point, and SQL-analysis tickets are rare.
- Decision:
