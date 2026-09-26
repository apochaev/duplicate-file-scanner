---
name: pipeline-stage
version: 9
phases: [design, test, implement, verify]
gates: [design, done]
models:
  architect: {model: opus, effort: high, maxTurns: 25}
  verifier: {model: opus, effort: medium, maxTurns: 20}
  implementer: {model: sonnet, effort: medium, maxTurns: 40}
  test-author: {model: sonnet, effort: medium, maxTurns: 30}
  retro: {model: sonnet, effort: low, maxTurns: 15}
---

# pipeline-stage

Applies to a ticket that adds or changes one stage of the detection pipeline (CLAUDE.md design decision 3): grouping by size, the partial-hash filter, the full-hash confirmation, and later stages of the same shape (hard-link detection, a persistent hash cache). It does not apply to ports unrelated to the pipeline, use cases, CLI work, or deletion/quarantine logic — those get their own archetypes as they come up (D4).

Written from what refining DFS-001 (group scanned files by size) surfaced. DFS-001, DFS-002 and DFS-003 are the first tickets to carry `archetype: pipeline-stage`.

## Where files go

- The stage's logic lives in `application`. It is not exported.
- A result type the pipeline exposes outside the stage chain (for example `DuplicateSet`) goes in `domain.model` and must be added to `module-info.java`'s `exports` for that package, and covered by `ModuleExportsTest`.
- A value type that is only the currency between stages (for example `CandidateGroup`), and never leaves `application`, stays in `application` — do not export it pre-emptively.
- A secondary port the stage needs (for example `ContentHasher`) goes in `infra.port`; its adapter goes in `infra.adapter`.
- Stubs are owned by the architect and throw `new UnsupportedOperationException("not implemented: <TICKET-ID>")` (CLAUDE.md decision 5 area; convention fixed by D2, matched by `tools/story-gate`'s `StubsCheck`/`RedCheck`, which key off the literal prefix `not implemented:`).
- A port method this ticket deliberately does not implement, deferring it to a later ticket (for example `ContentHasher.fullHash`, deferred by DFS-002 to DFS-003): stub it like any other method (the standard `not implemented: <TICKET-ID>` message) — the implementer is the one who changes it. The implementer replaces that message with a permanent one that does *not* use the `not implemented:` prefix and names the ticket that will implement it (for example `"full hash is implemented by DFS-003"`), so `StubsCheck` never reads it as a leftover stub. See `test-guide.md`, "Testing a port method deferred to a later ticket," for how to keep such a method's test correctly red now and green later without a new stub-recognition mechanism. The ticket that later implements the method finishes the handover in two steps. Its architect re-stubs the method back to `not implemented: <its own ID>`, because the deferring ticket's permanent message does not start with `not implemented:`, so `RedCheck` would reject the new story tests failing on it; the architect also names the deferring ticket's test file in `design.md`, since the test-author reads only files the design names. Its test-author removes the deferring ticket's stand-in test, which would otherwise fail `RedCheck` as a regression and then `green` once the method works, and writes tests for the method's real behavior. See `test-guide.md` (DFS-003.L-2).

## Edge-case checklist

Work through every item that applies to this stage; skip and say why for one that does not. Each item traces back to a DFS-001 decision (named in parentheses) or a CLAUDE.md convention this archetype generalizes.

1. **Accumulation and merging.** Does this stage accumulate results from input that could arrive in parts (a walk in progress, parallel workers)? If so: give it an `add`/`merge` shape, document that it is deliberately not thread-safe, make `merge(other)` copy `other`'s entries and leave `other` unchanged, and never let output depend on insertion or arrival order. If the stage instead consumes a finished `List` of groups from the previous stage (as DFS-002 and DFS-003 do), it has nothing to merge — say so and move on. (DFS-001: the mergeable, non-thread-safe accumulator.)
2. **Inter-stage currency invariants.** Whatever record carries data into and out of this stage (a candidate group, a hashed group, ...), does its constructor reject the cases that break its own definition (too few files, a size/hash mismatch among members, a null entry), and take an immutable defensive copy? (DFS-001: `CandidateGroup`'s invariants.)
3. **Deterministic output order.** Is the stage's output ordered the same way regardless of arrival order — for a grouping/accumulating stage, largest group first with paths ascending inside each group; for a filtering stage, the order it received its input in? State the canonical order in the design doc. (DFS-001: largest-first, ascending paths.)
4. **Immutable results.** Are the values this stage returns immutable, so a later stage (possibly on another thread, per DFS-005/DFS-006) cannot see them change underneath it? (DFS-001: returned groups are immutable.)
5. **Repeated input.** Can the same file reach this stage twice (for example because two scanned start locations overlap)? If so, does it collapse to one entry rather than inflating a group into a false duplicate, and — when two occurrences disagree (for example a different modification time) — which one wins, independent of insertion order? (DFS-001: equal paths collapse, keeping the later modification time.)
6. **Configuration and its defaults.** Does this stage have a tunable (a minimum size, a sample size, a threshold)? Say what the default is, where it is enforced (inside the stage itself, so a caller cannot forget it), and how it reaches the stage — a constructor argument for now, later a field of the scan config record in `domain.model` (CLAUDE.md's "Spring-friendliness rules"). (DFS-001: the minimum-size constructor argument, default 1.)
7. **Memory or performance, measured.** For a stage that must hold data across the whole walk before it can drop anything (as size-grouping does — a unique size is only known after every file is seen), measure actual heap for a realistic synthetic scale (DFS-001 used 1 million entries) and record the figure in the ticket before closing it, rather than assuming a bound. A stage that only ever holds one candidate group at a time (the hashing stages) can note that instead of measuring. (DFS-001: the plain in-memory map, memory measured.)
8. **Groups of one are dropped.** After this stage runs, does every group with fewer than two surviving files get dropped, so later stages and the final report never see a singleton "duplicate" of one file? (CLAUDE.md decision 3.)
9. **Path-order processing.** For a stage that reads file content (the hashing stages), are files within a group processed in ascending path order, for spinning-disk locality? Is there a size threshold below which a cheaper stage (the partial-hash sample) is skipped entirely, and is that threshold named and justified? (CLAUDE.md decision 3: hash in path order; skip the partial stage under ~128 KB.)
10. **Failure mid-stage.** What happens to a file that becomes unreadable, or changes size, between the previous stage and this one? Is it dropped from its group and reported, or does the whole group fail? How is that reported before DFS-005's progress listener exists (a `learnings.md` note is enough if the ticket punts on it, but the design doc must say which)?
11. **New secondary ports.** If this stage introduces a port (for example a hasher), does its interface match the stage decomposition (a sample method here, a full-hash method in a later ticket, or both together — the design doc states which and why), and is the application-level stage tested against a fake implementation of the port while only the NIO adapter is tested against real temp files (CLAUDE.md's test conventions)?
12. **Large and near-duplicate fixtures.** If this stage's behavior depends on file content past a threshold (the 128 KB partial-hash sample, full-hash confirmation), does the test plan include a same-size, differs-only-in-the-middle-byte pair generated in code, not committed to `fixtures/`?
13. **Reused across concurrent scans.** Does anything in this stage hold state in a field that would be shared if two scans ran at once (CLAUDE.md's "no state in singleton fields")? If it accumulates (item 1), one instance belongs to one scan; say so in the design doc.
14. **Input/output currency named.** Does the ticket's Story or Context name the type(s) this stage consumes and produces — the currency shared with its neighbor stages — or explicitly say the architect is free to choose them? If neither, raise it now rather than leaving the architect to invent an unspecified type at design time. (CCW-005: FIX-002's fixture demonstration, where the architect had to invent `CandidateGroup` unprompted.)

## Extra done-items

Beyond the standard checks (every scenario automated and passing, `./gradlew build` green including ArchUnit and module-export tests, no files changed outside `touches`):

- Every item of the checklist above is either addressed in the design doc or explicitly marked not applicable with a one-line reason.
- A stage that measures memory or benchmarks anything (checklist item 7) records the number in the ticket, not only in `progress.md`.
- A stage that adds an exported `domain.model` type updates `module-info.java`'s `exports` and keeps `ModuleExportsTest` green.
