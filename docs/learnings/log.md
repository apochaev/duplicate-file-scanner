# Learnings log

Friction found while working on this project, with a proposed change and what was decided. This is the manual stand-in for the `retro` agent and `/story learn` (design decision D6 in `docs/design/story-orchestration.md`, built by DFS-011 and DFS-012). When those exist they read and extend this same file, so keep the entry format.

## Rules

- **Nothing is applied without the user's approval.** A proposal stays `proposed` until the user decides.
- **Every entry cites evidence:** a ticket, commit or file, and what happened. No evidence, no entry.
- **Statuses:** `proposed`, `applied` (name the commit), `parked` (wait for it to recur), `rejected` (recorded so it is not proposed again).
- **Recurrence is a lookup:** when the same friction happens again, add a line under Recurrences instead of a new entry. A parked entry that recurs is re-proposed with that evidence.
- **Count recurrences in distinct tickets.** A repeat within the same ticket is noted on that ticket's line and does not raise the count, so one occasion is not counted several times.
- **Shared files are higher impact.** A change to `CLAUDE.md`, an agent prompt or a skill affects every story and every session; say so in the entry.
- **Who adds entries:** anyone who hits friction, in a session, and `/tickets done` asks whether there is any (see the skill). Entries are numbered `L-<n>`, never reused.

## Entry format

```
### L-<n>: <short title>

- Status: proposed | applied | parked | rejected
- Impact: local | shared (touches CLAUDE.md, an agent prompt or a skill)
- Target: <file the change would go in>
- Change: <what would be added or changed, in a sentence or two>
- Evidence: <ticket or commit, and what happened>
- Recurrences: <count of distinct tickets; name each ticket and what happened, repeats within one ticket on its line>
- Recommendation: <apply | park | reject, and why>
- Decision: <date and choice once decided; for applied, the commit>
```

## Entries

### L-001: State counts only from a mechanical count

- Status: applied
- Impact: shared (the `tickets` skill)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a ticket, note or summary states a count (scenarios, criteria, files), take it from a mechanical count such as `grep -c`, never from memory or from an earlier note.
- Evidence: refining DFS-010, the scenario count was stated as 24 and the mechanical check gave 32. Migrating DFS-001 to acceptance criteria (commit `b64bda7`), the stated 21 scenarios were 17.
- Recurrences: 2 (DFS-010 refinement; DFS-001 migration)
- Recommendation: apply. It has already happened twice, and the cost of the rule is one sentence.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-001 and L-005` (the sentence is in the `refine` mode of the skill).

### L-002: A criterion that names a set lists its members

- Status: parked
- Impact: shared (the `tickets` skill, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when an acceptance criterion or scenario refers to a set ("ready-or-later", "every existing ticket"), the ticket lists the members, so implementation does not have to interpret it.
- Evidence: DFS-008 decision 1 said `validate` flags "ready-or-later" tickets without defining "later". Implementation had to decide that done, cancelled and blocked tickets are not checked, and recorded it as a deviation (commit `ac2fcd1`).
- Recurrences: 1
- Recommendation: park until it recurs. One occurrence is not yet a pattern.
- Decision: parked 2026-09-21 by the user, as recommended.

### L-003: Run the check on a sample when a scenario says a generated file passes it

- Status: parked
- Impact: shared (the `tickets` skill, and later the archetype edge-case checklists)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a scenario claims that a generated artifact (a template output, a new ticket) passes a check, refinement builds a small sample and runs the check on it before the ticket becomes `ready`.
- Evidence: DFS-008's scenario said a new draft has "an empty criteria section" and that `validate` reports nothing, but its own tag rules made that impossible (the placeholder scenarios' tags would dangle). Found only at implementation; the scenario was reworded and the template ships a placeholder criterion (commit `ac2fcd1`).
- Recurrences: 1
- Recommendation: park until it recurs.
- Decision: parked 2026-09-21 by the user, as recommended.

### L-004: Do not hide errors from staging commands

- Status: applied
- Impact: shared (guidance for every agent that commits, so an agent prompt or `CLAUDE.md`)
- Target: `CLAUDE.md`, "Git commits" (or the implementer prompt once DFS-011 exists)
- Change: run `git add` and similar commands without redirecting stderr, and check `git status` before committing.
- Evidence: preparing commit `ac2fcd1`, `git add` included a path that no longer existed after `git mv`, with stderr sent to `/dev/null`. Staging of the other files silently did not happen, and only the `git status` check showed it.
- Recurrences: 1
- Recommendation: park. It was caught, and the fix is a habit more than a rule. Revisit if it recurs.
- Decision: applied 2026-09-21 by the user, overriding park; commit `docs: apply L-004` (a bullet in `CLAUDE.md`, "Git commits").

### L-005: With parallel sessions, stage explicit paths

- Status: applied
- Impact: shared (`CLAUDE.md`, "Git commits")
- Target: `CLAUDE.md`, "Git commits"
- Change: when the working tree holds changes from another session or worktree, stage the files of the task by name (never `git add -A` or `git commit -a`) and confirm with `git status` that nothing else is staged.
- Evidence: commit `ac2fcd1` was prepared while another session had changed DFS-010's ticket (status `in-progress`) and created `.claude/worktrees/`. Naming the paths kept both out of the commit. This is a practice that worked, recorded so it is not lost, and it becomes more likely to matter once stories run in parallel.
- Recurrences: 1
- Recommendation: apply. It is a rule about a mistake that is easy to make and costly to undo, and the pipeline will make parallel sessions normal.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-001 and L-005` (a bullet in `CLAUDE.md`, "Git commits").

### L-006: A `cd` in a shell command moves the session's working directory

- Status: parked
- Impact: local (working habit)
- Target: `CLAUDE.md`, "Commands" (or agent prompts)
- Change: use `git -C <dir>` or a subshell `(cd <dir> && ...)` instead of a bare `cd`, and prefer absolute paths, because the primary working directory of the session changes and later relative commands run somewhere else.
- Evidence: during the DFS-008 work, the session reported its primary working directory changing after a command that started with `cd` into `docs/tickets/backlog` (three times).
- Recurrences: 2 (DFS-008: three times in one session, counted once; DFS-014 refinement: a `cd` into `docs/tickets/backlog` moved the working directory again, several times)
- Recommendation: park. It caused no failure, only environment notices, and it is behaviour of the tool rather than the project. Revisit if it causes a wrong-directory error.
- Decision: parked 2026-09-21 by the user, as recommended.

### L-007: Commits name their ticket in the subject

- Status: applied
- Impact: shared (`CLAUDE.md`, "Git commits")
- Target: `CLAUDE.md`, "Git commits"
- Change: a commit made for a ticket ends its subject line with the ticket ID in brackets, e.g. `[DFS-001]`.
- Evidence: refining DFS-014 (the learn skill), finding a ticket's commits by searching for its ID: commit `ac2fcd1` names DFS-008 only in its body, and nothing else links a ticket to its commits.
- Recurrences: 1
- Recommendation: apply. The user asked for it, and the `learn` skill's `reflect` mode relies on it.
- Decision: applied 2026-09-21 on the user's instruction; commit `docs: require ticket ID in commit subjects`.

### L-008: The tickets lifecycle is not worktree-aware

- Status: applied
- Impact: shared (the `tickets` skill: `start`, `review` and `done`; possibly a note in `CLAUDE.md`)
- Target: `.claude/skills/tickets/SKILL.md`, `start`, `review` and `done` modes
- Change: `start` creates or enters the ticket's worktree and marks `in-progress` there, so no status edit is left in the main checkout; `done` runs against `main`, verifies the ticket's work is on `main`, moves the ticket folder there, and removes the worktree and branch after confirmation.
- Evidence: DFS-010 (2026-09-21). `start` edited the main checkout before the worktree existed, and the merge would have been refused until that edit was discarded. `main` advanced during the work (`b64bda7` to `36ab0f7`), so the branch was replayed onto it (commits `bbe0841`, `2fd6948`). After the merge the worktree and two branches were still there.
- Recurrences: 1
- Recommendation: apply, as a ticket (DFS-015) rather than a one-line edit, because it changes three modes and adds a destructive step (removing worktrees and branches) that needs its own safeguards and decisions. DFS-012 (`/story`) needs the same semantics, so the two should share one design.
- Decision: applied 2026-09-23 by the user, through ticket CCW-008 (the Recommendation's "as a ticket") rather than `/learn apply`; commits `e2238ac`, `dc17636`, closed by `2f8224e`. The shipped design differs from Change in naming (`tickets/<ID>-<slug>`) and in `done` merging the branch itself (L-020), per CCW-008's revised decisions.

### L-009: Scenarios about instructions are only read, not run

- Status: applied
- Impact: shared (the `tickets` skill, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when a ticket edits instructions (a skill or an agent prompt), refinement records for each scenario whether it can be run mechanically or only checked by reading, so a read-through is not presented as a walkthrough of every mode.
- Evidence: DFS-008 ticket, "Walkthrough evidence" (commit `ac2fcd1`): "Scenarios about instructions (refine, the implementer rule, `list`, `CLAUDE.md`) were checked by confirming the skill and `CLAUDE.md` state the rule in words, which is a weaker check than running them", against its done-item "A walkthrough of each mode". Found by `/learn reflect DFS-008` on 2026-09-21.
- Recurrences: 3 (DFS-008: see Evidence; DFS-014: agent-side scenarios covered only by the written rules and a stand-in run, "These show the words are present, which is weaker than running them" (2026-09-21); DFS-016: applied the rule, separating six scenarios (S1-S3, S9-S11, S17, about SKILL.md/CLAUDE.md text) as "Checked by reading only, not run (per L-009)" from the mechanically-run ones in its Walkthrough evidence section (2026-09-22))
- Recommendation: apply. Two tickets that edit instructions now state the same limit; DFS-014 already separates what ran from what was only read, which the rule would make routine. The edit is one sentence in `refine`.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-009 and L-012` (a sentence in the `refine` mode of the `tickets` skill).

### L-010: Recurrences names its ticket even at count 1

- Status: parked
- Impact: local (the log's own rules and format; the skill already copes with a bare count)
- Target: docs/learnings/log.md, Rules and Entry format
- Change: an entry's Recurrences field names its originating ticket from the start (`1 (DFS-008)`), so a second ticket has something to append to and the check can compare names with the count.
- Evidence: DFS-014 ticket, "Deviations found while implementing": "Entries such as L-002 have `Recurrences: 1` with no ticket named, so adding a second ticket had nothing to append to." Six entries still have a bare 1 (mechanical count).
- Recurrences: 1 (DFS-014)
- Recommendation: park. The skill's `add` already names the original ticket from Evidence when a second one arrives, and the check accepts a bare 1, so nothing breaks. Apply it if entries keep being written without a ticket name.
- Decision: parked 2026-09-21 by the user, as recommended.

### L-011: A new agent can't be launched in the turn that creates its file

- Status: rejected
- Impact: shared (the `tickets` skill, `refine` guidance for tickets that create agents)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: for a ticket that creates an agent, refinement plans the real-run done-item for after the agent is registered, not in the turn that creates the file, and does not tick it on a stand-in run.
- Evidence: DFS-014 ticket, "Deviations found while implementing": "`Agent` with `subagent_type: reflect` failed with 'Agent type 'reflect' not found'". Commit `033d31c` left the real-run item unticked ("Not yet met"); commit `9cb7c7c` ticked it after the agent had registered later in the same session ("The real run").
- Recurrences: 1 (DFS-014)
- Recommendation: park. One occurrence, handled openly. DFS-011 creates five agents and is where it would recur.
- Decision: rejected 2026-09-21 by the user, overriding park; treated as a one-off of this session's agent-registration timing, not a general rule.

### L-012: A finding declined in `reflect` leaves no record, so it is proposed again

- Status: applied
- Impact: shared (the `learn` skill, `reflect` mode)
- Target: `.claude/skills/learn/SKILL.md`, `reflect` mode, step 4
- Change: for each finding the user answers write, reject or skip. Reject writes the entry with status `rejected` and the decision date, so a later run reports it as "matches rejected L-<n>" and does not propose it again. Skip leaves no trace, as today.
- Evidence: DFS-014 ticket, last paragraph, added in `9cb7c7c`: "`reflect` leaves no trace of a finding the user declines, and nothing records which tickets it has run on".
- Recurrences: 1 (DFS-014)
- Recommendation: apply. The gap follows from the design, and the change is a few lines in one step of the skill.
- Decision: applied 2026-09-21 by the user, as recommended; commit `docs: apply L-009 and L-012` (step 4 of `reflect` mode in the `learn` skill).

### L-013: A filter-stage skeleton's null-check runs before its not-implemented throw

- Status: applied
- Impact: shared (an archetype skeleton every pipeline-stage story's architect reads)
- Target: `.claude/archetypes/pipeline-stage/skeletons/FilterStage.java.tmpl`
- Change: reorder the skeleton's guard so `throw new UnsupportedOperationException("not implemented: <TICKET-ID>")` comes before `Objects.requireNonNull(groups, "groups")`, so every stub method is uniformly red in the test phase regardless of which acceptance criterion a test targets; the implementer moves the null-check back above the real logic during implementation.
- Evidence: `docs/tickets/completed/CCW-004-story-agents/CCW-004-ticket.md`, "Demonstration evidence" — running the pipeline live against fixture ticket FIX-001, the architect's stub followed the skeleton verbatim (`requireNonNull` before the throw), which made FIX-001.AC-3's null-argument test pass outright against the stub instead of failing red. The architect, test-author and verifier each independently noticed and flagged it in the story's `learnings.md`/`verification.md` without being told to look for it.
- Recurrences: 1 (CCW-004)
- Recommendation: apply. Three independent agents converged on the same root cause and the same fix on a single run; it will recur on the first real filter-stage ticket with an argument-validation acceptance criterion. (A related half of this friction — whether a red-gate should tolerate a validation AC passing off the stub — was already addressed separately, in the same ticket, by adding explicit guidance to `.claude/agents/test-author.md`; this entry is only the remaining skeleton-ordering fix, which belongs to the archetype, not the agent.)
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-013` (the stub's `requireNonNull` moved to a guiding comment after the throw in `FilterStage.java.tmpl`; `pipeline-stage`'s `archetype.md` `version` bumped 1 → 2).

### L-014: StageResult.java.tmpl skeleton contradicts architect contract and sibling skeleton

- Status: applied
- Impact: shared (an archetype skeleton every pipeline-stage story's architect reads)
- Target: `.claude/archetypes/pipeline-stage/skeletons/StageResult.java.tmpl`
- Change: drop the pre-throw `Objects.requireNonNull(members, "members")` (the same ordering issue L-013 fixed in `FilterStage.java.tmpl`, left unfixed here) and reword the architect-directed comment from instructing the architect to enforce invariants and take a defensive copy itself, to "architect: state the invariants in javadoc; implementer enforces them" — matching the actual architect/implementer contract (the architect writes stubs only, never real logic).
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`, "Demonstration evidence" — running the pipeline live against fixture ticket FIX-002, the architect found the skeleton's own comments telling it to "enforce this record's own invariants ... and take an immutable defensive copy ... before assigning the field," which contradicts the architect contract and repeats the pre-throw ordering L-013 already fixed in the sibling skeleton. Worked around manually (invariants moved to javadoc, `requireNonNull` dropped) rather than following the skeleton as written.
- Recurrences: 1 (CCW-005)
- Recommendation: apply. The architect had to manually detect and route around a documented contradiction in the skeleton during this run; fixing it removes that rediscovery cost for every future pipeline-stage ticket that uses a stage-result-shaped skeleton, and keeps the L-013 fix consistent across all of `pipeline-stage`'s skeletons rather than just `FilterStage.java.tmpl`.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-014, L-015 and L-016` (the stub's `requireNonNull` moved to a guiding comment after the throw in `StageResult.java.tmpl`; `pipeline-stage`'s `archetype.md` `version` bumped 2 → 3).

### L-015: A pipeline-stage ticket should name its stage's input/output currency types

- Status: applied
- Impact: shared (the `pipeline-stage` edge-case checklist every ticket's refinement walks through)
- Target: `.claude/archetypes/pipeline-stage/archetype.md`, edge-case checklist
- Change: add a checklist item requiring a `pipeline-stage` ticket's refinement to name the stage's input and output currency types explicitly, or say the architect is free to choose them, so the architect never has to invent an unspecified type at design time.
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`, "Demonstration evidence" — fixture ticket FIX-002 named the stage class (`SmallGroupFilter`) and a member accessor (`long size()`) but not the type the stage filters, so the architect had to invent `CandidateGroup` at design time with no ticket guidance.
- Recurrences: 1 (CCW-005)
- Recommendation: apply. Low-risk clarification to the refinement checklist; likely to recur on the next `pipeline-stage` ticket refined without this fixture's pattern in mind.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-014, L-015 and L-016` (new checklist item 14 in `archetype.md`; `version` bumped 2 → 3).

### L-016: test-guide.md should warn against assertThrows for L-013-style stubs

- Status: applied
- Impact: shared (an archetype guide every pipeline-stage story's test-author reads)
- Target: `.claude/archetypes/pipeline-stage/test-guide.md`
- Change: add a note that `assertThrows(ExpectedType.class, ...)` against a stub whose precondition throw comes after its `UnsupportedOperationException` (the L-013 ordering) misreports under the red gate: `assertThrows` wraps the mismatch as its own `AssertionFailedError` instead of letting the stub's exception propagate, and `RedCheck` reads only the top-level JUnit XML failure type. Recommend a `try { ...; fail(...); } catch (ExpectedType expected) {}` block instead.
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`, "Demonstration evidence" — FIX-002's test-author discovered this while writing the AC-3 null-argument test against `SmallGroupFilter`'s L-013-ordered stub, and flagged it explicitly as a general trap for any L-013-style stub.
- Recurrences: 1 (CCW-005)
- Recommendation: apply. The test-author explicitly flagged this as a general trap worth documenting, and it cost extra gate iterations to discover; a one-line addition to `test-guide.md` prevents every future pipeline-stage test-author from rediscovering it.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-014, L-015 and L-016` (new bullet in `test-guide.md`'s "What not to do"; `archetype.md` `version` bumped 2 → 3).

### L-017: Live demonstration found integration bugs invisible at design/refinement time

- Status: applied
- Impact: shared (`.claude/skills/tickets/SKILL.md`, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: at refinement, make sure the scenarios test the ticket's own purpose (its "So that") against the real state of the repository and environment it will run in, not only against a clean setup; and when the Definition of done needs a live run across already-built components, name the assumptions between them that the run must exercise (shared names, who performs a step, which status a downstream check expects).
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`, "Real bugs found only by running this for real" — three integration bugs found only by live demonstration: the `test`/`tests` phase-id spelling mismatch between `pipeline-stage/archetype.md` (CCW-002) and `FreezeCheck` (CCW-003); no agent prompt (CCW-004) included a commit step though `plan.json`/`FreezeCheck` expected one; Finishing omitted `tickets review <ID>` so `consistency` failed at the finish line. Two follow-up fix commits (`5cc00ea`, `7079a6b`) were needed after the initial build commit (`3a28e3d`).
- Recurrences: 2 (CCW-005: see Evidence; CCW-008: first version of the parallel-work feature assumed a tree always has `main` checked out, false in the repo it was built in; fixed by decision 4, AC-19 to AC-21 (2026-09-22))
- Recommendation: re-proposed on a second ticket. Apply a broader form: at refinement, turn the ticket's stated purpose and the real repo/environment state into at least one scenario, not only the seams between components.
- Decision: parked 2026-09-22 by the user, as recommended; re-proposed with CCW-008; applied 2026-09-22 by the user; commit `docs: apply L-017`.

### L-018: A ticket's design-time assumption about another component's capability was wrong until it was run for real

- Status: parked
- Impact: shared (`.claude/skills/tickets/SKILL.md`, `refine` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `refine` mode
- Change: when refining a criterion that assumes another already-built component's capability (for example, what tools an agent has), check that component's real definition rather than carrying forward an earlier design-doc sketch.
- Evidence: `docs/tickets/completed/CCW-005-story-orchestrator-skill/CCW-005-ticket.md`'s `AC-11` changed from "the retro agent... appends proposed entries to `docs/learnings/log.md`" to "...which returns proposals in its report (it never writes)" — the ticket's own text names the cause: the design doc's D6 sketch and this ticket's own Context predate CCW-004 actually building `retro.md`, whose tool list (`Read, Grep, Glob, Bash`, no `Write`) was only discovered by launching it for real.
- Recurrences: 1 (CCW-005)
- Recommendation: park. One occurrence; the underlying cause (a design doc describing agents before they were built) is specific to this harness-bootstrapping phase and may not recur once agents are stable.
- Decision: parked 2026-09-22 by the user, as recommended.

### L-019: A ticket's touches list widened after it became ready

- Status: rejected
- Impact: local
- Target: none (recorded for pattern-watching only)
- Change: none proposed; watch whether a ticket's touches list needing a mid-implementation addition recurs.
- Evidence: commit `f4b4e94` set `CCW-005` `ready` with `touches: [.claude/skills/story, .claude/agents, docs/learnings]`; commit `3a28e3d` (the same commit as the initial build, the first thing done once `in-progress`) added `.claude/settings.json`, needed because the pipeline's background subagents needed pre-approved commands — not anticipated at refinement.
- Recurrences: 1 (CCW-005)
- Recommendation: reject. Single, minor, and already self-corrected within the same commit with no downstream cost; not distinct enough from ordinary scope discovery to warrant a rule.
- Decision: rejected 2026-09-22 by the user, as recommended.

### L-020: `tickets done` should commit its own changes and merge the story branch into main

- Status: applied
- Impact: shared (`.claude/skills/tickets/SKILL.md`, `done` mode)
- Target: `.claude/skills/tickets/SKILL.md`, `done` mode
- Change: after moving the ticket folder and updating frontmatter, `done` re-stages that one file by name, confirms with `git diff --cached` that the staged content matches the working tree (`git mv` can silently leave the index holding pre-edit content), and commits it itself as a narrow, documented exception to "commits only when asked." If a branch `tickets/<ID>-<slug>` exists and differs from the branch `done` runs from, merge it in (fast-forward if possible, otherwise an ordinary merge commit; on conflict, stop and tell the user to resolve it by hand) and delete it only once the merge succeeds.
- Evidence: closing CCW-004, the `git mv`-based close commit (`45adfd1`) staged pre-edit frontmatter despite the working tree already having the edit — caught only via a `0 insertions/0 deletions` stat, needing corrective commit `a6a2624`. Closing CCW-005 hit the identical bug, caught this time only because of the CCW-004 lesson. The user then asked directly for `done` to commit and merge, with implementation left to best judgement.
- Recurrences: 2 (CCW-004: original, corrected by `a6a2624`; CCW-005: recurred identically, caught by explicit re-staging)
- Recommendation: apply. Two occurrences of the identical stale-index failure across two tickets is a real mechanical pattern; the merge-into-main half serves the same close-out moment and the user asked for both together. Overlaps with CCW-008 ("Make the tickets lifecycle worktree-aware") — worth a glance when that ticket is next refined, in case it wants to extend this further rather than duplicate it.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-020` (the `done` mode addition in `.claude/skills/tickets/SKILL.md`).

### L-021: `design.md.tmpl` missing checklist disposition item 14

- Status: applied
- Impact: shared (archetype template every pipeline-stage architect writes from)
- Target: `.claude/archetypes/pipeline-stage/design.md.tmpl`
- Change: add item 14 ("Input/output currency named") to the template's "Checklist disposition" list, matching `archetype.md`'s 14-item checklist (added by L-015).
- Evidence: `docs/tickets/backlog/DFS-001-group-files-by-size/learnings.md`, design section — "Template gap: checklist item 14 is missing from `design.md.tmpl`... I added item 14 to this `design.md` by hand. The template should list it too."
- Recurrences: 1 (DFS-001)
- Recommendation: apply — direct fallout from L-015 not being propagated to the template that actually renders it; will recur on every pipeline-stage ticket until fixed.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-021 and L-022`.

### L-022: `Accumulator.java.tmpl` contradicts L-013 and lacks a validated-configuration constructor pattern

- Status: applied
- Impact: shared (archetype skeleton every accumulator-shaped pipeline-stage story's architect reads)
- Target: `.claude/archetypes/pipeline-stage/skeletons/Accumulator.java.tmpl`
- Change: (1) drop the pre-throw `Objects.requireNonNull(...)` in `add`/`merge`'s stub bodies — the same L-013/L-014 ordering bug, now found on a third skeleton — replacing with the same "implementer: add requireNonNull" guiding comment used in the other two; (2) add an example of a constructor taking validated configuration (e.g. `SizeIndex(long minSize)`) that itself throws the not-implemented exception, with a no-arg constructor delegating to it, so a validation-bearing constructor fails red correctly.
- Evidence: `docs/tickets/backlog/DFS-001-group-files-by-size/learnings.md`, design section — both `Accumulator.java.tmpl` bullets: "`skeletons/Accumulator.java.tmpl` contradicts L-013... I left the checks out of the `SizeIndex` stub. The skeleton should drop them and carry the same 'implementer: add requireNonNull' comment as the other two." and "The skeletons do not cover an accumulator whose constructor takes validated configuration... I made the constructor throw too... The skeleton could show this pattern."
- Recurrences: 1 (DFS-001) — third skeleton file independently found with the L-013-shaped bug (after `FilterStage.java.tmpl`, `StageResult.java.tmpl`); worth a glance at whether `pipeline-stage`'s remaining skeletons need the same audit rather than waiting for each to surface one at a time.
- Recommendation: apply, consistent with L-013/L-014.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-021 and L-022`.

### L-023: A DoD item requiring a measured figure should get a committed, reproducible benchmark, not a throwaway class

- Status: applied
- Impact: shared (`pipeline-stage`'s `impl-guide.md`, the archetype every implementer reads)
- Target: `.claude/archetypes/pipeline-stage/impl-guide.md`
- Change: when a ticket's Definition of done requires a measured figure (heap, timing, throughput) recorded before closing, the implementer writes it as a committed, `@Disabled` (or otherwise excluded-from-default-run) benchmark in the test tree, not a throwaway class run outside the build — so the figure is reproducible by anyone later, including the verifier or a human closing the ticket, instead of resting on an unverifiable one-off run.
- Evidence: `docs/tickets/completed/DFS-001-group-files-by-size/DFS-001-ticket.md` — the implementer's heap measurement used "a throwaway `HeapMeasure` class run directly against `file-scanner`'s compiled classes (not committed, not part of the build)"; the verifier explicitly could not reproduce it and left the done-item unticked for that reason. The closing session (2026-09-22) had to write `SizeIndexHeapBenchmark.java` after the fact and re-run it to actually tick the item. Independently, the pilot's control-branch implementation of the same ticket made the identical choice (its own throwaway, uncommitted `HeapMeasure`-equivalent) — two separate implementations converged on the same non-reproducible pattern with no guidance against it.
- Recurrences: 1 (DFS-001) — though the pattern recurred within one ticket, in both the pipeline and control implementations independently; not counted twice per the log's own rule (repeats within one ticket are noted, not double-counted).
- Recommendation: apply. Two independent implementations made the same non-reproducible choice with no template guidance either way; a one-line addition to `impl-guide.md` (or wherever the archetype's benchmark guidance best fits) closes the gap before it recurs on DFS-002 or any future pipeline-stage ticket with a measured DoD item.
- Decision: applied 2026-09-22 by the user, as recommended; commit `docs: apply L-023`.

### L-024: No convention for a port method deliberately deferred to a later ticket

- Status: applied
- Impact: shared (the `pipeline-stage` archetype's stub convention every architect and test-author reads)
- Target: `.claude/archetypes/pipeline-stage/archetype.md`, "Where files go" (documents the two-stage stub-message convention) and `test-guide.md` (documents the conditional-rethrow test pattern)
- Evidence: `docs/tickets/backlog/DFS-002-partial-hash-stage/learnings.md`, design section — "the archetype and `impl-guide` do not cover a port method that is intentionally left unimplemented for a later ticket... A convention would help, for example a distinct prefix such as `deferred to:` that `StubsCheck` ignores." Independently re-raised in the test-phase learnings ("Retry (defect 1): a method stubbed until a future ticket is not designed for in this archetype"), and directly caused `plan.json` defect 1 (test-phase attempt 1 failed the gate, escalated to opus for attempt 2).
- Recurrences: 1 (DFS-002) — hit independently by both the architect and the test-author in one story
- Recommendation: apply. Two independent phases hit the same undesigned case and it caused a real gate failure and an unnecessary model escalation, not just friction. Applied as documentation rather than the originally proposed `RedCheck`/`StubsCheck`/`TestResult` code change: a code-level `"deferred to:"` prefix does not actually solve the problem, since `RedCheck` only runs *before* implementation, while the stub still carries the generic message — a test asserting the eventual, differently-worded message still mismatches at that point regardless of what prefix `RedCheck` recognizes. It also would have collided with `GreenCheck`, which fails unconditionally on any `FAILED` test — a test designed to stay red forever would permanently break `green`. The pattern that actually resolved this in DFS-002 (test-author's conditional rethrow: catch, and re-throw unchanged only while the message still starts with `not implemented:`) needs no gate code change at all — it turns green on its own once the implementer's permanent message lands, exactly like every other AC test. The fix is documenting that pattern as a named convention so future test-authors don't have to reinvent it under gate pressure.
- Decision: applied 2026-09-23 by the user; commit `docs: apply L-024, L-025 and L-026` (new bullet in `archetype.md`'s "Where files go"; new "Testing a port method deferred to a later ticket" section in `test-guide.md`; `pipeline-stage`'s `archetype.md` `version` bumped 5 → 6).

### L-025: Resume protocol has no scripted path for sending a passed/gated phase back for targeted rework

- Status: applied
- Impact: shared (`.claude/skills/story/SKILL.md`, D5 resume protocol)
- Target: `.claude/skills/story/SKILL.md`, `approve <ID> [feedback]` mode
- Change: add a named step (analogous to the existing `blocked`-retry carve-out) for "a phase already `done`, past a human gate, needs to go back for more work": which downstream phases reset to `pending` vs `in-progress` (for reconciliation) vs are left alone, that the attempt counter/D9 escalation does not apply (this is human-directed, not automatic), and how the deterministic checks should be re-run given a later phase's commit already exists on the branch.
- Evidence: `docs/tickets/backlog/DFS-002-partial-hash-stage/learnings.md` — "D5's resume protocol (`SKILL.md`) has no built-in path for 'a phase already marked `done`, past a human gate, needs to go back for more work'... This time it was driven by hand... Candidate for `/story learn`: give this case an actual named step in the resume protocol." Corroborated by `plan.json`'s tests-phase note: "human-directed reopen after the done gate... not an automatic-retry escalation."
- Recurrences: 1 (DFS-002)
- Recommendation: apply. The orchestrator explicitly flagged this as ad hoc while doing it by hand and asked for a scripted procedure; will recur any time a verifier's "send it back" verdict is accepted. L-026 folded into this same edit rather than applied as a separate `tools/story-gate` code change (see its own entry below).
- Decision: applied 2026-09-23 by the user; commit `docs: apply L-024, L-025 and L-026` (rewrote the `approve <ID> [feedback]` "With feedback" bullet in `SKILL.md` with the scripted reset-and-reconcile procedure; added a cross-reference in resume-protocol step 3).

### L-026: `story-gate`'s `test-phase` check cannot validate tests reopened after implementation already exists

- Status: applied
- Impact: shared (`.claude/skills/story/SKILL.md`, folded into L-025's edit)
- Target: `.claude/skills/story/SKILL.md`, `approve <ID> [feedback]` mode (not `tools/story-gate` — see Decision)
- Change: give `story-gate` a way to validate newly added/changed tests in a reopened tests phase without requiring the whole working tree's `src/main` to be pre-implementation — for example diffing test results against the recorded stub commit's tree in isolation, or an explicit "reopened, verify green not red" mode instead of always demanding the red-gate shape.
- Evidence: `docs/tickets/backlog/DFS-002-partial-hash-stage/learnings.md`, "test retry 2" entry — "`RedCheck`... has no notion of 'the tests phase was reopened after implementation already happened'... `RedCheck` reports 13 'passed against stubs' problems — the exact inverse of what it is designed to catch."
- Recurrences: 1 (DFS-002) — the same underlying "reopen a passed phase" scenario as L-025, but a distinct, purely mechanical tooling gap in the check itself rather than the orchestrator's process
- Recommendation: apply. Confirmed tooling gap; contributed to the test-author agent burning through its turn budget trying to satisfy an unsatisfiable check. Applied as an orchestrator-side procedural rule, not a `RedCheck`/`TestPhaseGate` code change: a code fix that makes "these tests would fail against a wrong implementation" checkable without `src/main` reverted to stubs is mutation-testing-shaped, which CLAUDE.md's parked ideas already reject as too slow and an engine-compatibility risk. What actually resolved this in DFS-002 was simpler — verify a reopened tests phase with a plain green run (`green`), since `red`'s whole model assumes pre-implementation and does not apply once a later phase's commit already exists. Folded into L-025's `SKILL.md` edit since both are the same "send a passed phase back" scripted step.
- Decision: applied 2026-09-23 by the user; commit `docs: apply L-024, L-025 and L-026` (the same `SKILL.md` edit as L-025, which documents using `green` rather than `test-phase` to verify a reopened `tests` phase).

### L-027: `DisabledCheck`/`FreezeCheck` scan the whole test tree, not just the current story's own scope

- Status: proposed
- Impact: shared (`tools/story-gate/DisabledCheck.java`, `FreezeCheck.java`)
- Target: `tools/story-gate/DisabledCheck.java`, `tools/story-gate/FreezeCheck.java`
- Change: scope both checks to the ticket's `touches` (or at minimum give `DisabledCheck` a carve-out for a deliberately-committed manual benchmark, distinct from a real skipped/incomplete test) instead of scanning/diffing the entire test tree regardless of which ticket is running.
- Evidence: `docs/tickets/backlog/DFS-002-partial-hash-stage/plan.json`, defect 2 — "`disabled` check failed on `SizeIndexHeapBenchmark.java` (DFS-001's own committed benchmark, unrelated to anything DFS-002 touched)... Then `freeze` failed for the same reason."
- Recurrences: 1 (DFS-002) — two consecutive false-positive gate failures from a completely unrelated file
- Recommendation: apply, as a ticket (CCW-009) rather than a direct edit — real code across two files with genuine design questions (what "the ticket's own scope" resolves to for these checks), the same reasoning L-008 already set precedent for. Concrete, reproduced false-positive gate failure; will recur for any story that runs after a prior story's test-tree-touching learning gets committed.
- Decision:

### L-028: Checklist item 10 conflates "unreadable" and "changes size" as one undesigned case

- Status: parked
- Impact: local (this ticket's design only)
- Target: `.claude/archetypes/pipeline-stage/archetype.md`, edge-case checklist item 10
- Change: split checklist item 10 into two explicit prompts (unreadable-during-read vs. size-changes-during-read) so a ticket's refinement is asked to decide both, rather than leaving one undecided for the architect to defer unprompted.
- Evidence: `docs/tickets/backlog/DFS-002-partial-hash-stage/learnings.md`, design section — "Checklist item 10 asks about both 'unreadable' and 'changes size'. The ticket only decided the unreadable case. I deferred the size-change case in `design.md` rather than invent behavior that no AC tests."
- Recurrences: 1 (DFS-002)
- Recommendation: park. Single occurrence with no gate failure attached; worth revisiting if a future pipeline-stage ticket hits the same ambiguity.
- Decision: parked 2026-09-22 by the user, as recommended.

### L-029: Ambiguity between test-guide's "cover rejected inputs" prompt and the no-invented-scenarios rule

- Status: parked
- Impact: shared (`.claude/archetypes/pipeline-stage/test-guide.md` and the test-author's own top-level instructions)
- Target: `.claude/archetypes/pipeline-stage/test-guide.md`, "Cover, per scenario" section
- Change: clarify that the test-guide's invalid-input prompt only applies when the ticket's own acceptance criteria name an invalid-input scenario, removing the ambiguity the test-author had to resolve unprompted.
- Evidence: `docs/tickets/backlog/DFS-002-partial-hash-stage/learnings.md`, test section — "the `pipeline-stage` test-guide... says to test whatever the design doc's public API/Configuration sections say is rejected... But the top-level test-author instructions say not to invent scenarios beyond the ticket's own ACs... I followed the top-level instruction and left these untested... Flagged in the final report as a possible refinement gap."
- Recurrences: 1 (DFS-002)
- Recommendation: park. Resolved correctly without a gate failure this time, but the ambiguity itself is real and shared across every pipeline-stage ticket.
- Decision: parked 2026-09-22 by the user, as recommended.

### L-030: test-guide.md never prompts a discrimination test for an adapter that must distinguish inputs

- Status: applied
- Impact: shared (`pipeline-stage`'s `test-guide.md`, every pipeline-stage test-author reads)
- Target: `.claude/archetypes/pipeline-stage/test-guide.md`, "Cover, per scenario" section
- Change: add a bullet requiring, for any adapter whose job is to distinguish inputs (a hasher, a comparator, any port mapping distinct inputs to distinct outputs), at least one test that feeds it two different real inputs and asserts the outputs actually differ — not only that identical inputs produce matching outputs. A test suite proving only the matching case cannot catch a constant-returning or partially-ignoring implementation.
- Evidence: `docs/tickets/backlog/DFS-002-partial-hash-stage/verification.md`, "Friction" — "the test guide does not require discrimination tests for a hashing adapter. This retry was needed only because of that gap." Directly caused the done-gate reopen: AC-1 and AC-9 originally proved only that matching/identical inputs behaved correctly; a constant-returning `sampleHash`, or a silently-ignored custom threshold, would have passed the entire deterministic battery (green, disabled, freeze, traceability) undetected. Caught only by the verifier's qualitative review, not by any script.
- Recurrences: 1 (DFS-002)
- Recommendation: apply. This is the specific gap that forced a whole extra tests-phase attempt (35 turns, 348s) and a second verify pass on DFS-002; a one-line addition to the test-guide would have caught it during the original tests phase instead of at the human gate.
- Decision: applied 2026-09-23 by the user; commit `docs: apply L-030` (new "Discrimination" bullet in `test-guide.md`'s "Cover, per scenario" section; `pipeline-stage`'s `archetype.md` `version` bumped 6 → 7).

### L-031: A refined ticket had to be reconciled mid-implementation with a same-day learning applied to the same skill

- Status: rejected
- Impact: local (no target; a watch-only observation)
- Target: none
- Change: none; watch whether a ticket that waits in the backlog while `/learn apply` changes its target skill needs re-reconciling again.
- Evidence: `docs/tickets/completed/CCW-008-worktree-aware-tickets-lifecycle/CCW-008-ticket.md`, "Revised (2026-09-22), at implementation time": L-020 (commit `7bc54cc`) changed `done` after CCW-008 was refined, so the merge behavior, naming convention and close-commit subject were re-decided, six criteria retired (AC-2, AC-3, AC-4, AC-7, AC-9, AC-10) and replaced by AC-12 to AC-17.
- Recurrences: 1 (CCW-008)
- Recommendation: park. Single occurrence, corrected within the same ticket; L-020's own Recommendation had already flagged the overlap with CCW-008, so the existing safeguard worked.
- Decision: rejected 2026-09-22 by the user.

*(Numbered `L-024` on the branch this entry was written on (`main`); renumbered to `L-031` on 2026-09-23 while merging `tickets/DFS-002-partial-hash-stage` into `main`, since that number was independently taken there by a different entry (now this log's actual `L-024`) before either branch saw the other's commits. See `L-033` for the general friction this collision itself surfaced.)*

### L-032: `/story run` checks its branch out in place instead of using a worktree

- Status: proposed
- Impact: shared (`.claude/skills/story/SKILL.md`, a skill)
- Target: `.claude/skills/story/SKILL.md`, Preflight step 2
- Change: Preflight step 2 creates the story branch off `main` (not the current branch tip). It then runs `/tickets start <ID> --worktree`, so the branch lives in its own worktree `../<repo-dir>-<ID>-<slug>`, where every phase agent then works. The repo checkout stays on `main`, as `CLAUDE.md`'s parallel-work convention (CCW-008) expects.
- Evidence: Preflight step 2 on `main` reads "Create it off the current branch tip if it does not exist; check it out if it does." `/story run DFS-002` left the repo checkout on `tickets/DFS-002-partial-hash-stage`. While CCW-008 was being built, no tree had `main` checked out. The first version of CCW-008's `done` refused in exactly that state, and closing CCW-008 needed the temporary close tree (`docs/tickets/completed/CCW-008-worktree-aware-tickets-lifecycle/CCW-008-ticket.md`, decision 4 and Out of scope). Branching "off the current branch tip" also means a story started from another ticket's branch inherits that branch's unmerged commits.
- Recurrences: 1 (CCW-008)
- Recommendation: apply, as a ticket rather than a one-line edit. The preflight, the resume protocol and the per-phase commits all assume one checkout, and agents would have to be pointed at the worktree path, which `/story run` needs to test for real (L-017). Until then, `done`'s close tree keeps closes working.
- Decision:

*(Numbered `L-025` on `main`; renumbered to `L-032` for the same reason as `L-031`, above.)*

### L-033: Two sessions working on diverged branches independently allocate the same next learnings-log number

- Status: proposed
- Impact: shared (`.claude/skills/learn/SKILL.md`, `add`/`apply`/`reflect` steps that allocate the next `L-<n>`; `.claude/skills/story/SKILL.md`'s `learn` mode does the same)
- Target: `.claude/skills/learn/SKILL.md` (and `.claude/skills/story/SKILL.md`'s `learn` mode, which allocates the same way)
- Change: a per-story learnings log, kept in the story's own folder alongside `learnings.md`/`progress.md`, rather than every session computing "highest `L-<n>` in the shared `docs/learnings/log.md`, plus one" directly against a file that a parallel session on a different branch may have already advanced. Global numbering and merging into the one shared log would happen at a single serialization point (closing the ticket, or merging its branch), not at proposal time. Discussed in conversation before this collision was hit for real, not yet designed.
- Evidence: closing DFS-002, `main` had diverged with a full CCW-008 implementation done in a parallel session on the same machine, overlapping this session's working hours (commits `e2238ac` through `c0a2555`, 2026-09-22 23:25 to 2026-09-23 07:15). Both sessions independently proposed a next entry by reading their own branch's copy of `docs/learnings/log.md` and computing "highest number plus one" — each landed on `L-024` and `L-025` for two entirely unrelated findings (this session: the deferred-stub convention and the resume-protocol gap; the other: a mid-implementation reconciliation observation and a worktree-checkout gap). Neither session could have detected the collision without seeing the other's uncommitted or unmerged work. Resolved by hand at merge time: the other session's two entries renumbered to `L-031`/`L-032` (see their own notes).
- Recurrences: 1 (DFS-002/CCW-008, discovered together at merge time — one collision, two colliding entries)
- Recommendation: apply. This is a structural gap, not a one-off mistake — any two sessions working on diverged branches that both propose learnings will collide the same way, and the log's own rule ("numbers are never reused") was already broken once between two branches before either was merged. Needs real design (how/when the per-story entries get a global number, what a story's own learnings file looks like before that point) rather than a one-line fix, so likely its own ticket.
- Decision:
