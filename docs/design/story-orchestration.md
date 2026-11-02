# Story orchestration design

Status: design decided (D1 to D10, 2026-09-21); the next step is turning it into tickets. Nothing here is built yet.

## Goal

Dispatch agents to implement fully refined (`ready`) tickets with little supervision: an architect defines the design and ports, an implementer and a test author build it, a verifier proves it green, and a retro step proposes improvements to the templates. Work is resumable from files in the story folder.

## Proposed shape (working draft, refined by the decisions below)

```
/story run DFS-001
  0 preflight      ticket ready? dependencies done? branch, tickets start
  1 architect      design.md + compilable contract stubs               [human gate]
  2 test-author (red gate), then implementer (green)
  3 verifier       deterministic gate, then skeptical review (fix loop, max 2 rounds)
  4 retro          learnings.md proposals
  5 tickets review -> human review -> done
```

Agents (`.claude/agents/`): `architect`, `implementer`, `test-author`, `verifier`, `retro`. Templates come from an archetype catalog (`.claude/archetypes/<name>/`), chosen by an `archetype:` field in the ticket.

Story folder artifacts, next to the ticket: `design.md`, `test-plan.md` (scenario to test map), `plan.json` (resumable state; agents edit only their own phase fields), `progress.md` (append-only log), `verification.md`, `learnings.md` (suggestions, kept separate).

Basis: Anthropic's long-running harness articles (file-based state, JSON progress file, separate evaluator, sprint contract), independent test authoring (AgentCoder), reviewed "reflect" loops for learning. Every phase should be removable, since each encodes an assumption about what the model cannot do alone.

## Decisions

### D1. Orchestration mechanism (decided 2026-09-21)

A skill, `/story run <ID>`, running in the main session, reads `plan.json` and launches the phase agents as ordinary subagents. Each run advances until the next human gate or the end and then returns; running it again resumes from the plan. Routine commands are pre-approved in settings because background subagents surface permission prompts in the main session.

Rejected:
- Agent teams: experimental and disabled by default, in-process teammates cannot be resumed, one team per session, more tokens, poor fit for a mostly sequential pipeline. Revisit only if the pilot shows phases need to negotiate directly.
- Story as a background runner (`context: fork`): human gates are awkward inside a background subagent. It remains a later layer for multi-story dispatch, which the stop-at-gate and resume design already allows.

### D2. Isolation between implementation and testing (decided 2026-09-21)

Sequential, test-first, in one worktree. The test author runs first, against the architect's compilable stubs, and stops when the tests compile and every one fails for the right reason; the implementer then makes them pass. The implementation does not exist when the tests are written, so the tests cannot inherit its mistakes, and there are no extra worktrees, no merge and no Gradle contention.

- **Red gate:** a deterministic script, not an agent, runs the tests and parses the JUnit XML reports. Every story test (display name starting with the ticket ID, D11) must fail with the stubs' `UnsupportedOperationException` (in a TypeScript story, the stubs' `Error`, read from Vitest's JUnit report; CCW-020), and every other test must pass (settled while refining CCW-003). A test that passes, or fails for another reason, goes back to the test author as a defect naming only the offending tests and the reason (about ten lines). Stack traces and full Gradle output never enter an agent's context. This catches vacuous tests and broken tests before the implementer spends a cycle on them.
- **Test freeze:** after the test phase the verifier checks with `git diff` that no test file changed, so the implementer cannot make tests green by editing them.
- **Stubs:** owned by the architect; methods throw `new UnsupportedOperationException("not implemented: <TICKET-ID>")`, or in TypeScript `throw new Error('not implemented: <TICKET-ID>')` (the `component` archetype, CCW-020). The message prefix is what the red gate matches, because real code also throws `UnsupportedOperationException` (immutable collections). Settled while refining CCW-003.
- **Rule for the whole pipeline:** gates are deterministic scripts, and an agent reads only a compact verdict.
- **Parallel option kept:** phases in `plan.json` declare what they depend on, so an archetype for a large story can opt in to running implementer and test author in separate worktrees (both branched from the stub commit, disjoint `src/main` and `src/test`) as a configuration change. Not built now.

Rejected: parallel in separate worktrees as the default (worktree, merge and build-file conflict cost, Windows path-length risk, contract gaps found late, for a small wall-clock gain on small stories); parallel in one tree (Gradle lock contention, independence rests on a hook); compile-only red phase (misses vacuous and broken tests); full test output in agent context (token cost).

### D3. Human gates (decided 2026-09-21)

Default: two gates.
1. After design: the run stops with a compact summary of the contract (about 20 lines).
2. Before `done`: the run stops with a diff summary and `verification.md`.

Between them the pipeline runs unattended on the deterministic gates. A gate marks the plan as awaiting approval; the next `/story run` resumes after the user approves or gives feedback. Gates are configurable per archetype through a `gates:` list in `archetype.md`: trivial archetypes can drop the design gate, and safety-critical ones (anything that plans or executes deletion) can add a gate after the red gate. Commits stay the user's explicit call (CLAUDE.md).

Rejected: three fixed gates (another interruption for modest protection, since tests derive from scenarios already approved at `ready` and the verifier checks scenario coverage); a final gate only (a wrong contract would waste a whole run).

**Amended 2026-10-31 (CCW-017).** Gates are now decided per ticket, during refinement, and recorded in the ticket's `gates:`; the archetype's `gates:` is only a fallback for a ticket that has none. "Final gate only" (`gates: [done]`) is a legitimate choice made knowingly for a ticket where a wasted run is cheap; refinement recommends keeping the design gate for anything that plans or executes deletion. The `done` gate is the product-owner review: the verifier's `## Product owner review` section (what the user can do now, verdicts, how to try it, what was decided without the user, limits, evidence, how the run went). A ticket with `autorun: true` is started by Claude itself, right after its ready commit, when its last dependency closes, or on another session's unblock message (CCW-018), so the user's input is the refinement and the review; every stop sends a push notification. Per-phase commits remain the orchestrator's, as before.

### D4. Archetype catalog (decided 2026-09-21)

Plain files, one directory per archetype, under `.claude/archetypes/<name>/`. Agents read only the files their phase needs, by path, so the catalog has no standing token cost. (Skill descriptions sit in every session's context, and a subagent's `skills:` preload list is fixed in its definition, so it cannot load "the archetype of this story".)

An archetype holds:
- `archetype.md`: frontmatter (`name`, `version`, `phases`, `gates`, per-agent `models`), then when it applies, an edge-case checklist, extra done-items, and where its files go in the hexagonal layers.
- `design.md.tmpl`, `test-plan.md.tmpl`, `impl-guide.md`, `test-guide.md`, and `skeletons/*.java.tmpl`.

Each ticket gets an `archetype:` frontmatter field. The `tickets` skill's `ready` gate adds "archetype set", and `refine` reuses the archetype's edge-case checklist. `plan.json` records the archetype version used, so changes proposed by the retro step are traceable.

Initial set (proposed): `pipeline-stage`, `port-and-adapter`, `use-case`, `domain-model`, `cli-command`, `spike`, `chore-docs`. Only `pipeline-stage` is needed for the DFS-001 pilot; it is written first, from what was learned refining DFS-001, and the rest as stories need them.

Rejected: archetypes as skills (standing token cost, no dynamic preload); `docs/archetypes/` (splits the pipeline machinery across two trees; a rename away if wanted).

### D5. `plan.json` and resume protocol (decided 2026-09-21)

One `plan.json` per story, and the orchestrator is its only writer. Agents write their artifacts and append notes and checkpoints to their own entries in `progress.md`, and report their outcome in their final message. The orchestrator flips a phase to `done` only after that phase's deterministic gate passes, so an agent can never grade its own work or corrupt the state.

Shape:

```json
{
  "ticket": "DFS-001",
  "archetype": {"name": "pipeline-stage", "version": 1},
  "branch": "story/DFS-001",
  "state": "running | awaiting-approval | blocked | done",
  "phases": [
    {"id": "design", "agent": "architect", "after": [], "status": "pending | in-progress | done",
     "runs": [{"attempt": 1, "model": "opus", "effort": "high", "turns": 21, "outcome": "passed | failed-gate | maxTurns"}],
     "artifacts": ["DFS-001-design.md"], "commit": "abc1234"}
  ],
  "gates": [{"after": "design", "status": "pending | approved"}],
  "defects": [{"id": 1, "route": "implementer", "summary": "...", "status": "open | closed"}]
}
```

`after` lists the phases a phase depends on, so an archetype can reorder or parallelize phases later (see D2).

Resume protocol, run by each `/story run`:
1. Read `plan.json`.
2. If the state is awaiting approval and the user approved, mark the gate approved.
3. Pick the first phase that is not done and whose `after` phases are done.
4. If it is `in-progress`, reconcile: when its artifacts and commit exist and its gate script passes, mark it done; otherwise re-run it and hand the agent the notes from `progress.md`. Phases are idempotent.
5. Launch the agent. When it returns, run the phase's deterministic gate. On a pass, mark done and record the commit. On a failure, record a defect and route it to the right agent.
6. After two failed attempts on a phase, set the state to `blocked` and escalate to the user.

Rejected: agents editing their own phase fields (they can mark themselves done, the self-evaluation bias this design guards against, and malformed edits need schema validation); one state file per phase (more files to read at every start, and resume must assemble the picture from several).

**Addendum (2026-09-23, CCW-006 pilot, DFS-002 run).** The resume protocol above had no scripted path for "a phase already `done`, past a human gate, needs to go back for targeted rework" — only the two-failed-attempts-to-`blocked` path and the plain approve-and-continue path existed. DFS-002's verifier sent the tests phase back after the done gate for two targeted test additions, driven entirely by hand. `.claude/skills/story/SKILL.md`'s `approve <ID> [feedback]` mode now scripts this: the named phase and everything after it up to the gate resets to `pending` (or `in-progress` for a phase needing only reconciliation) with a fresh, non-escalated attempt count, and a reopened `tests` phase is verified with `green` rather than `test-phase`, since the red gate's "must fail against stubs" precondition cannot hold once a later phase's commit already exists on the branch (L-025, folded with L-026).

### D6. Learning loop (decided 2026-09-21)

Every agent appends suggestions to `learnings.md` when it hits friction (a template gap, a skeleton that did not fit, an ambiguity in CLAUDE.md). The `retro` agent turns them into proposed diffs against archetype files, agent prompts, skills or CLAUDE.md. It runs only when there is something to learn (`learnings.md` non-empty or the story had defects) and reads only the plan, `progress.md` and the learnings, not transcripts.

After a story, `/story learn <ID>` shows the ranked proposals and the user picks one of three for each:
- **apply now:** applied as a separate commit (for example `docs(archetypes): ...`) and the archetype's `version` is bumped;
- **park:** kept until the suggestion recurs;
- **reject:** recorded so it is not proposed again.

Decisions are recorded in `docs/learnings/log.md`. When a new suggestion matches a parked one, `retro` re-proposes it with the recurrence as evidence, so counting recurrence is a lookup in one file.

Rules:
- Every proposal cites its evidence: the phase, the defect id or the file.
- Proposals to shared files (CLAUDE.md, agent prompts) are flagged as higher impact, since they affect every story and interactive sessions.
- Nothing is applied without the user's approval.

Rejected: review-and-apply with no memory (skipped proposals return, one-offs get approved too eagerly); a recurrence threshold alone (every friction must happen twice, and it needs fuzzy counting across stories); an autonomous loop with regression checks (as in GRASP), too costly and risky this early, worth revisiting once there is a body of stories to test against.

**Manual stand-in (added 2026-09-21).** Until the `retro` agent and `/story learn` exist (CCW-004, CCW-005), `docs/learnings/log.md` is kept by hand in the same format: friction is logged as `proposed` with evidence, the `tickets` skill's `done` mode asks whether there is any, and the user decides apply, park or reject. It gives the first `retro` run real entries and the recurrence counts to work from.

**Addendum (2026-09-23, CCW-010).** Decisions are no longer recorded in one shared `docs/learnings/log.md`. Two sessions on diverged branches each computed the same "next" `L-<n>` for unrelated findings (DFS-002.L-8). Each ticket now keeps its structured entries in its own `learnings.md`, identified `<ID>.L-<n>` and numbered only within that file, so there is no shared counter to race. Counting recurrences is now a scan across tickets' files, not a lookup in one file. The old log is a pointer with an old-to-new lookup table for the 33 migrated entries.

### D7. Verifier scope (decided 2026-09-21)

Two layers.

Deterministic gate (scripts, no tokens; an agent sees only a compact verdict):
- build and tests green (`./gradlew build`, including the ArchUnit and module-export tests), with failing test names and a short reason on failure;
- no `@Disabled`; any skipped test must come from a platform `Assumptions` check (for example the case-sensitivity fixture test on Windows);
- test freeze: no test file changed since the test phase (`git diff`);
- touches: every changed file is inside the ticket's `touches`, its story folder, or its tests;
- acceptance-criterion traceability (see D11): every criterion has a tagged scenario and appears, by ID, at the start of a test's display name and in `test-plan.md`;
- consistency: the ticket status matches the plan, and `plan.json` parses;
- done-items are ticked by the verifier after it has verified them, not by the implementer (the "Implementing a ticket" rules in the `tickets` skill must change accordingly).

LLM review (a per-archetype switch, on for `pipeline-stage` in the pilot): the diff against the scenarios, the CLAUDE.md invariants and `design.md`, plus test quality (meaningful assertions, behaviour rather than restated code). It stays only if the pilot shows it finds things the scripts do not (see D8).

Rejected: a minimal gate (leaves mechanical checks to a model); mutation testing now (slow, adds a plugin, JUnit 6 compatibility risk, heavy for small stories), parked below.

**Addendum (2026-09-23, CCW-006 pilot, DFS-002 run).** The condition above is met: DFS-002's verifier found that AC-1 and AC-9 were each proven only in their matching/positive shape, never a distinguishing/negative one (a constant-returning fake `sampleHash`, or a silently-ignored custom threshold, would have passed the entire deterministic battery — green, disabled, freeze, traceability, all of it). The LLM review stays (CCW-006, AC-9 verdict table).

### D8. Pilot (decided 2026-09-21)

1. **A/B on DFS-001.** The full pipeline on one branch and a plain single-session implementation of the same ticket on another. This answers "is the pipeline worth it at all"; the extra cost is roughly one plain implementation of a small story.
2. **Pipeline on DFS-002.** It answers "does the learning loop pay". DFS-002 is still a draft, so it is refined first, using the `pipeline-stage` archetype's edge-case checklist (which is itself a test of the archetype).

Recorded in both runs:
- tokens and wall-clock time per phase (the exact source of token figures is to be confirmed when building);
- attempts and defects per phase, and which gate caught each;
- the user's interventions: rejected gates and manual fixes;
- anything the LLM review found that the scripts missed (decides whether it stays);
- anything the red gate caught that a compile-only check would have missed;
- learnings proposed and approved;
- the user's verdict on the result: acceptable as is, minor edits, or major rework.

Pruning: after both stories the metrics are read together. A component stays only if it caught something, or prevented rework the user can name, in at least one of the two runs; otherwise it becomes optional for that archetype. No numeric threshold, since two data points do not support one.

Rejected: pipeline only on DFS-001 (no control, so the overhead is easy to rationalise); two pipeline stories without a control (cannot say whether a plain session would have been cheaper).

### D9. Token budget defaults (decided 2026-09-21)

Tiered by leverage, with escalation on retry.

| Agent | Model | Effort |
|---|---|---|
| architect | opus | high |
| verifier (LLM review) | opus | medium |
| implementer | sonnet | medium |
| test-author | sonnet | medium |
| retro | sonnet | low |

- **Escalation:** a second attempt of any phase runs one tier up, through the per-invocation model, which takes precedence over the agent's frontmatter.
- **Configurable through frontmatter**, resolved in this order: the ticket's optional `models:` (a per-story override), then the archetype's `models:` in `archetype.md` (D4), then the agent's own frontmatter. Escalation on retry applies one tier above whatever resolved. Shape: `models: {architect: {model: opus, effort: high, maxTurns: 25}}`, any subset of `model`, `effort` and `maxTurns` per agent. The `tickets` skill's frontmatter table gains an optional `models` field.
- **Learning about models:** `plan.json` logs every run (model, effort, turns, outcome), so `retro` has evidence to propose tuning the archetype's `models:` in both directions: up when a phase needed escalation or exhausted its turns in repeated stories, and down when a strong model showed no defects, no escalations and comfortable turn use across stories. These are ordinary proposals through `/story learn` (D6).
- **Turn caps:** `maxTurns` on every agent as a backstop. Starting guesses: architect 25, test-author 30, implementer 40, verifier 20, retro 15, to be replaced by pilot measurements.
- **Minimal reads:** each agent's prompt names exactly the files to read; nothing is read speculatively.
- **Cache:** `subagentPromptCacheTtl` stays at the five-minute default (each subagent has its own system prompt, so little cache is shared, and one hour costs more per write).
- **No hard per-story token cap yet:** it needs reliable usage figures, which the pilot will show. If wanted later, a soft cap: the orchestrator checks between phases and sets the story `blocked` with a report.

Rejected: cheapest by default with escalation only (a weak contract is not a failure until late, the costliest place to save); the strongest model everywhere (highest spend).

### D10. Gate script implementation (decided 2026-09-21)

Java source-launch programs in `tools/story-gate/`, run as `java tools/story-gate/StoryGate.java <check> <ID>`. Each check prints a verdict of about ten lines and sets an exit code. Multi-file source launch (JDK 22 and later) lets the code split into small files. The launcher uses `$JAVA_HOME`'s `java`, because multi-file launch fails on the older JDKs that are also on this machine's path (17 and 11). The JDK has no JSON parser, so the tools use a small but complete hand-written JSON parser (one file, about 150 lines), because the freeze check must read a field from `plan.json`, not only validate it (settled while refining CCW-003).

The gates are the pipeline's safety net, so they carry a fixture-driven `selftest` built into the tools (settled while refining CCW-003). If they grow, promote them to a small Gradle subproject with JUnit tests (a later ticket).

Rejected:
- Python: a second runtime for every agent and sandbox, and version ambiguity on this machine (`python` is 3.11.9 while `py` resolves to 3.14.3). The runner-up; revisit if the gates grow large and change often, or if Python is preferred.
- bash: fragile XML parsing and no `jq` installed.
- Gradle task: Gradle's startup warning and progress lines add tokens to agent context, and it couples the gates to the product build.

### D11. Acceptance criteria (decided 2026-09-21, while refining CCW-003)

Refinement produces a numbered list of acceptance criteria, in a `## Acceptance criteria` section between Context and Scenarios. Each is a one-line testable statement with the ID `<TICKET-ID>.AC-<n>` (for example `CCW-004.AC-1`).

- Scenarios carry `@AC-n` tags (one or several). Every scenario needs at least one tag, and every criterion needs at least one scenario.
- Tests start their `@DisplayName` (in Vitest, the name given to `it`; the gate reads it after the last ` > ` of the report's name, which prefixes the `describe` names, CCW-020) with the ID(s) they prove, followed by a readable title. The traceability check reads the JUnit XML, where the display name is the test case name, and matches by ID, so retitling a scenario does not break the link.
- IDs are never renumbered or reused; a retired criterion keeps its number and is marked retired.
- The chain is criterion, scenario, test, and each link is checked. The human gates show the criteria with their coverage status (about twenty lines), and the verifier records a verdict per criterion.
- Migration: DFS-001 gets criteria derived from its scenarios (the author confirms the mapping, CCW-001); the drafts are grandfathered until refined.

Rejected: one criterion per scenario (as fine-grained as the scenarios, no requirement-level statement, no compact gate summary); numbering inside scenario titles (the same granularity, and less standard than tags).

### D12. Every stop shows a next step (decided 2026-09-21)

The aspiration is that every point where `/story run` (or `/story learn`) returns control to the user ends its output with an explicit next action, never a bare status the user has to interpret. This applies to every stop, not only the two default human gates (D3):

- **A human gate reached:** the compact summary (D3) ends with the literal command to approve or give feedback (`/story run <ID>` again after approval, or how to reject).
- **A run resumes and stops again** (the next gate, or the same gate re-shown after a failed reconciliation): same rule — the resumed output still ends with the next command.
- **`blocked`** (two failed attempts on a phase, D5): the escalation names the phase, the defect, and the concrete options (retry, edit the ticket/design and resume, or cancel the story), not just "blocked."
- **The pipeline finishes** (`state: done`): says so and names the next thing that becomes possible (`/story learn <ID>` if there is anything to learn, otherwise that there is nothing to learn).
- **`/story learn` shows proposals:** each one ends with the commands to decide it, as `/learn review` already does (apply, park, reject).

This generalizes what `tickets` and `learn` already do at their own stop points (for example `tickets done`'s trailing `/learn reflect <ID>` offer, `learn apply`'s wait-for-approval before writing) to every stop the orchestrator itself introduces, so a run never leaves the user needing to recall a command from memory or ask "what now."

Rejected: a status-only stop that trusts the user to know the pipeline's commands (matches how `/story run` was sketched in D1/D5, but is the gap this decision closes); a single generic "run `/story run` to continue" appended everywhere regardless of state (cheaper, but wrong for `blocked`, where there is no single next command, only a choice among them).

This is a requirement for CCW-005's refinement to turn into acceptance criteria and scenarios (one per stop kind above), not yet reflected in the ticket file itself.

## Build tickets (created as drafts on 2026-09-21)

CCW-001 to CCW-006 exist as drafts in `docs/tickets/backlog/`. Each still needs its own refinement to reach `ready`.

| ID | Ticket | Depends on |
|---|---|---|
| CCW-001 | Extend the `tickets` skill: `archetype` and optional `models` frontmatter, "archetype set" in the `ready` gate, the verifier (not the implementer) ticking done-items, and numbered acceptance criteria (D11). | none |
| CCW-002 | Write the `pipeline-stage` archetype, from what was learned refining DFS-001. | CCW-001 |
| CCW-003 | The story gate tools in Java (`tools/story-gate/`): red gate, build verdict, test freeze, touches, traceability, consistency, and the `plan.json` validator. | none |
| CCW-004 | The five agent definitions, with the D9 models and turn caps. | CCW-002, CCW-003 |
| CCW-005 | The `/story` skill: run and resume, `plan.json`, gates, `learn`, and the learnings log. | CCW-001, CCW-003, CCW-004 |
| CCW-006 | The pilot (a spike, D8): A/B on DFS-001, then the pipeline on DFS-002, with the metrics table and the pruning review. | CCW-005, and DFS-002 refined |
| CCW-007 | The `learn` skill (added 2026-09-21): manages the learnings log (`add`, `review`, `decide`, `apply`) and `reflect`, which launches an agent that drafts entries from a finished ticket. The agent is the seed of `retro` (CCW-004), and `/story learn` (CCW-005) wraps it. | none |

CCW-001 and CCW-003 do not depend on each other and could be built first, in parallel. Once CCW-001 lands, DFS-001 gets `archetype: pipeline-stage`.

## Parked ideas

- Mutation testing (for example PIT) as an extra gate, probably for safety-critical archetypes such as deletion planning. Concerns: slow, adds a plugin, and JUnit 6 engine compatibility is already a known risk for test tooling (see CLAUDE.md on ArchUnit).
- Agent teams, if the pilot shows that phases need to negotiate directly (D1).
- An autonomous learning loop with regression checks (D6).
- Parallel implementer and test author in separate worktrees for large stories, through `after` in `plan.json` (D2).
