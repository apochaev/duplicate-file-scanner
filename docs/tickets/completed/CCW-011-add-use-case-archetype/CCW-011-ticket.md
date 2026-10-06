---
id: CCW-011
title: Add a use-case archetype
status: done
type: docs
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: []
touches: [.claude/archetypes/use-case]
blocked_reason:
created: 2026-10-05
updated: 2026-10-06
completed: 2026-10-06
---

# CCW-011: Add a use-case archetype

## Story

As the story pipeline's agents (architect, test-author, implementer, verifier)
I want an archetype written for use cases and the services that implement them
So that tickets like DFS-018 get a checklist, templates and guides that ask the right questions, instead of stretching `pipeline-stage`

## Context

DFS-005.L-8 (`docs/tickets/completed/DFS-005-run-scan-end-to-end/learnings.md`): `pipeline-stage` fit DFS-005's composing service poorly. None of its skeletons fit, checklist items 2 and 7 had nothing to say, and `design.md.tmpl`'s "this stage" wording read oddly. DFS-018 (track scans asynchronously) is the first ticket to carry `archetype: use-case` and depends on this one; more use cases follow (DFS-019, deletion preview and execution, undo/purge, location overlaps).

The phase agents read these files from an archetype directory (their real definitions in `.claude/agents/`): `archetype.md` (architect: edge-case checklist, "where files go", `models`), `design.md.tmpl` (architect), `test-plan.md.tmpl` and `test-guide.md` (test-author), and `impl-guide.md` (implementer). `pipeline-stage` also has a `skeletons/` folder; the plan from DFS-018's refinement is to ship no Java skeletons until a second use case shows what they should look like. `/story run`'s phase table is fixed across archetypes (`.claude/skills/story/SKILL.md`), so the five agents and their checks need no change.

Seed for the edge-case checklist, drawn from DFS-018's refinement decisions (`docs/tickets/backlog/DFS-018-track-scans-asynchronously/DFS-018-ticket.md`, decisions 1 to 12):

- Unknown or stale IDs: what each command and query returns for an ID it never issued (DFS-018: `Failure(UNKNOWN_SCAN)`).
- Results as values: which outcomes are `Result.Failure` values and which stay exceptions (null arguments) (decision 4).
- Status model: every state including the transient ones (queued, stopping), the legal transitions, which states are terminal, and what each command does in each state (decision 3).
- Races: which transitions can collide and which outcome wins, and where that is decided atomically (decisions 3, 8).
- Validation before side effects: what is checked on the caller's thread before anything is queued or recorded (decision 6).
- State placement: per-request state lives behind a port, the service holds only its ports and the executor (decision 8, CLAUDE.md's "no state in singleton fields").
- Caller-supplied callbacks: what happens when a listener or callback throws (decision 7).
- Failures off the caller's thread: where an asynchronous failure is recorded so it is not lost; `Error` handling (decision 7).
- Retention: how long state is kept, and whether removal is in scope (decision 9).
- Testability: a seam for any composed collaborator that is hard to drive (decision 12), a manual executor for queued states, latches instead of `Thread.sleep` (decision 10).

What reads an archetype (checked 2026-10-05 against the real files): the phase agents read the five files above, and the architect also reads every file under `skeletons/` (an absent folder means none); the `story` skill reads only `archetype.md`'s frontmatter (`phases`, `gates`, `models`); `/tickets validate` checks only that `.claude/archetypes/<name>/` exists; no `tools/story-gate` check reads anything archetype-specific.

Decisions (refinement, 2026-10-05):

1. **Archetype `none`, plus an independent coverage read-through.** This ticket writes instruction files only, so the pipeline's red/green, freeze and stubs gates have nothing to check (precedent: CCW-002 built `pipeline-stage` the same way). It is implemented by hand, and before `review` a fresh general-purpose agent, given only this ticket, DFS-018's ticket and the new files, reports whether an architect working from those files alone would be prompted to settle each of DFS-018's decisions. Gaps it finds are fixed before `review`.
2. **Copy `pipeline-stage` and adapt each file; carry every general rule over verbatim.** The general pipeline rules in `pipeline-stage` are kept word for word or marked "not applicable" with a reason: the stub message convention (`not implemented: <TICKET-ID>`, matched by `StubsCheck`/`RedCheck`), the deferred-port handover (DFS-002.L-1, DFS-003.L-2), the `assertThrows`-on-stubs warning (CCW-005.L-3), the `@DisplayName` naming convention (D11), the discrimination-test bullet (DFS-002.L-7), the `toRealPath` bullet (DFS-005.L-4), and the `## Summary` and "Checklist disposition" sections of `design.md.tmpl`. Stage-specific content (accumulators, group filtering, sample thresholds, the `skeletons/`) is replaced, and the edge-case checklist is new. A shared file for the general rules is not built here: the agents read only the named archetype's directory, so it would mean changing four agent prompts. The duplication is a known cost, to be logged with `/learn add` and revisited when a third archetype appears.
3. **`models` equal to `pipeline-stage`'s:** architect opus/high/25, verifier opus/medium/20, implementer sonnet/medium/40, test-author sonnet/medium/40, retro sonnet/low/15. DFS-018's deterministic tests pin every transition, so a wrong implementation fails the green gate and the story skill's escalation moves the retry to opus. DFS-018's retro decides whether the defaults need raising.

## Acceptance criteria

The full ID of each criterion is `CCW-011.AC-<n>`.

- **AC-1** `.claude/archetypes/use-case/` holds exactly `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `test-guide.md` and `impl-guide.md`, and no `skeletons/` folder.
- **AC-2** `archetype.md`'s frontmatter parses as YAML with `name: use-case`, `version: 1`, `phases: [design, test, implement, verify]`, `gates: [design, done]`, and a `models` block equal to `pipeline-stage`'s (decision 3).
- **AC-3** `archetype.md` has the sections `## Where files go`, `## Edge-case checklist` and `## Extra done-items`, and an opening paragraph saying it applies to use cases and the services implementing them, and that detection pipeline stages use `pipeline-stage`.
- **AC-4** The edge-case checklist has a numbered item for each of the ten seed topics listed in Context.
- **AC-5** A fresh agent, given only this ticket, DFS-018's ticket and the five new files, finds every one of DFS-018's decisions 2 to 12 prompted by at least one checklist item, or each gap it reports is fixed and the check rerun clean (decision 1).
- **AC-6** Every general rule named in decision 2 appears in the `use-case` files under the same key phrase as in `pipeline-stage` (`not implemented: <TICKET-ID>`, `deferred to a later ticket`, `assertThrows`, `@DisplayName`, `Discrimination`, `toRealPath`, `## Summary`, `Checklist disposition`), or is marked not applicable with a one-line reason.
- **AC-7** No stage-specific content remains in the `use-case` files: no mention of accumulators, `SizeIndex`, `FilterStage`, `StageResult` or sample thresholds.
- **AC-8** `design.md.tmpl` has, besides the sections carried from `pipeline-stage`, a `## State and transitions` section and a `## Concurrency and atomicity` section.
- **AC-9** `test-guide.md` tells the test-author to use a manual executor for queued states, fakes that block on a latch, transitions applied in both orders for races, and one real-thread test with a bounded timeout, and forbids `Thread.sleep`.
- **AC-10** `/tickets validate` reports no archetype finding for DFS-018.

## Scenarios

Each scenario says how it is checked: **mechanical** (a command whose output decides it) or **reading** (a person or agent reads the files; not a walkthrough of any agent's run).

```gherkin
Feature: The use-case archetype

  # check: mechanical (ls)
  @AC-1
  Scenario: The archetype directory holds exactly the five files the agents read
    When the files under ".claude/archetypes/use-case/" are listed
    Then they are exactly "archetype.md", "design.md.tmpl", "test-plan.md.tmpl", "test-guide.md" and "impl-guide.md"
    And there is no "skeletons" folder

  # check: mechanical (parse the frontmatter of both archetypes and compare)
  @AC-2
  Scenario: The frontmatter is what the story skill expects
    When the frontmatter of ".claude/archetypes/use-case/archetype.md" is parsed as YAML
    Then name is "use-case", version is 1, phases is [design, test, implement, verify] and gates is [design, done]
    And models equals the models block of ".claude/archetypes/pipeline-stage/archetype.md"

  # check: mechanical (grep the three headings); reading (the opening paragraph)
  @AC-3
  Scenario: archetype.md has the sections the architect relies on and says what it applies to
    When "archetype.md" is read
    Then it has the headings "## Where files go", "## Edge-case checklist" and "## Extra done-items"
    And its opening paragraph says it applies to use cases and their services, and that pipeline stages use "pipeline-stage"

  # check: reading (map each seed topic to a numbered item)
  @AC-4
  Scenario: Every seed topic has a checklist item
    When each of the ten seed topics in Context is looked up in the edge-case checklist
    Then each one matches a numbered item

  # check: reading, by a fresh general-purpose agent with no conversation history (decision 1)
  @AC-5
  Scenario: The checklist would prompt every decision DFS-018 had to make
    Given a fresh agent given only CCW-011's ticket, DFS-018's ticket and the five new files
    When it maps each of DFS-018's decisions 2 to 12 to the checklist items that would prompt it
    Then every decision has at least one item
    Or every gap it reports is fixed and a rerun reports none

  # check: mechanical (grep each key phrase in both archetypes)
  @AC-6
  Scenario Outline: A general rule is carried over from pipeline-stage
    When "<phrase>" is searched for in ".claude/archetypes/pipeline-stage/" and ".claude/archetypes/use-case/"
    Then it is found in both
    Or the use-case file that would hold it marks the rule not applicable with a one-line reason

    Examples:
      | phrase                       |
      | not implemented: <TICKET-ID> |
      | deferred to a later ticket   |
      | assertThrows                 |
      | @DisplayName                 |
      | Discrimination               |
      | toRealPath                   |
      | ## Summary                   |
      | Checklist disposition        |

  # check: mechanical (case-insensitive grep)
  @AC-7
  Scenario: No stage-specific content was left behind
    When ".claude/archetypes/use-case/" is searched case-insensitively for "accumulat", "SizeIndex", "FilterStage", "StageResult" and "sample threshold"
    Then nothing is found

  # check: mechanical (grep the headings)
  @AC-8
  Scenario: The design template asks for the state model and its concurrency
    When "design.md.tmpl" is read
    Then it has the headings "## State and transitions" and "## Concurrency and atomicity"
    And it still has "## Summary" and "## Checklist disposition"

  # check: mechanical (grep "Thread.sleep", "latch", "manual executor", "both orders", "bounded timeout"); reading (that Thread.sleep is forbidden, not recommended)
  @AC-9
  Scenario: The test guide covers deterministic async testing
    When "test-guide.md" is read
    Then it describes a manual executor, fakes blocking on a latch, races tested by applying transitions in both orders, and one real-thread test with a bounded timeout
    And it forbids Thread.sleep

  # check: mechanical (run /tickets validate)
  @AC-10
  Scenario: DFS-018's archetype now resolves
    When /tickets validate is run
    Then it reports no archetype finding for DFS-018
```

## Scope

**Touches:**
- `.claude/archetypes/use-case/`: `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `test-guide.md`, `impl-guide.md` (new)

**Out of scope:**
- Java skeletons for use cases (until a second use-case ticket shows their shape)
- Changes to `pipeline-stage` or to the phase agents
- Implementing DFS-018

## Definition of done

- [x] Every scenario above is checked the way it says (mechanical or reading) and passes, with the evidence noted beside this item. AC-1: `ls -A` lists exactly the five files, no `skeletons/`. AC-2: frontmatter `name: use-case`, `version: 1`, phases and gates as stated; the `models` block is byte-identical to `pipeline-stage`'s (`diff`). AC-3: each heading found once; the opening paragraph was read. AC-4: read; seed topics map to items 1, 2, 3, 4, 5, 6, 8, 9, 12, 13. AC-5: see the implementation notes. AC-6: seven phrases found in both archetypes; `## Summary` only in `use-case` (deviation noted above). AC-7: zero hits (Python, case-insensitive). AC-8: all four headings found. AC-9: `thread.sleep` 2, `latch` 3, `manual executor` 2, `both orders` 1, `bounded timeout` 1 (Python); `Thread.sleep` is forbidden on lines 31 and 69 of `test-guide.md`. AC-10: see the next item.
- [x] `/tickets validate` reports no archetype finding for DFS-018: its archetype rule, run on this branch, finds `.claude/archetypes/use-case/` for DFS-018 and no finding for any ticket. On `main` it holds once this branch is merged.
- [x] No files changed outside the paths listed under Touches: `git status` shows only `.claude/archetypes/use-case/` and this ticket file (a `grep.exe.stackdump` left by the crashing grep was deleted).
- [x] The fresh agent's coverage report (AC-5) is summarized in this ticket, with any gap it found and how it was fixed: see the implementation notes (no gap; four tightenings applied).

## Implementation notes (2026-10-05)

- **AC-6 deviation (a refinement mistake).** `## Summary` is not in `pipeline-stage`'s files: its architect gets that section from `.claude/agents/architect.md`, not from `design.md.tmpl`. So that row cannot be "found in both". `use-case/design.md.tmpl` carries `## Summary` anyway, because the architect prompt requires it. The other seven phrases are found in both.
- **Checklist beyond the seed.** The ten seed topics are items 1, 2, 3, 4, 5, 6, 8, 9, 12 and 13. Items 7 (progress), 10 (query results), 11 (executor contract) and 14 (exported surface) were added to cover DFS-018 decisions 2, 5 and 11 and the exported-types question no seed topic named.
- **AC-5 coverage report (fresh general-purpose agent, given only this ticket, DFS-018's ticket and the five files).** Gaps: none; every one of DFS-018's decisions 2 to 12 is prompted by at least one item (2: items 7, 11, 14; 3: 3, 4; 4: 1, 2; 5: 10, 14; 6: 5, 2; 7: 8, 9; 8: 4, 6; 9: 12; 10: 13 and the test guide; 11: 2, 11; 12: 13). Its four tightenings were applied: item 5 now covers validation failures only and hands executor rejection to item 11 (it had contradicted item 11); item 7 asks that the snapshot be kept current from the same events the listener gets; item 9 asks that a failed run be a successful query whose state says failed; item 11 asks what the pre-created record becomes on rejection. No rerun was needed, since it reported no gap.
- **Reported, not fixed here (outside `touches`).** DFS-018's decision 8 and Definition of done say the service has "no field other than its ports and the executor", but its decision 12 gives it a `ScanRunner` seam field; the archetype's wording ("its ports, its seams and the executor") is the right one, and DFS-018's should be aligned before its run, or the verifier may flag a correct implementation. Two references carried verbatim from `pipeline-stage` are wrong in both archetypes: "CLAUDE.md decision 5 area" for the stub convention (decision 5 is about factories), and "CLAUDE.md, 'Implementing a ticket'" (that section is in the `tickets` skill, not CLAUDE.md).
- **Tooling.** In Git Bash, `grep -i -F` crashes (`msys-2.0.dll`, leaving `grep.exe.stackdump`) and prints nothing, which reads as zero matches; the AC-7 and AC-9 checks were redone in Python.

## Open questions

None.
