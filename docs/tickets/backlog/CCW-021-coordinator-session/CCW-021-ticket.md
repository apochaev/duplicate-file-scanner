---
id: CCW-021
title: Add a coordinator session that catches up from git
status: draft
type: feature
scope: [docs, build]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: harness
depends_on: []
touches:
  - .claude/skills/tickets/SKILL.md
  - docs/tickets/lanes.md
  - CLAUDE.md
  - .claude/settings.json
  - tools/story-gate
blocked_reason:
created: 2026-11-01
updated: 2026-11-02
completed:
---

# CCW-021: Add a coordinator session that catches up from git

## Story

As the user running several Claude Code sessions on this backlog in parallel
I want one coordinator session that owns no lane, refines tickets for every lane, and rebuilds the whole backlog's state and the pending advice from git and the working trees whenever it starts or clears, without other sessions messaging it
So that one place knows the backlog's state and keeps every lane's queue full, without me relaying between sessions and without waking sessions for reports, keeping per-turn token cost and latency low

## Context

Filed 2026-11-01 at the user's request, from the session `dfs-product`; first made ready in `697a6a3` with push messages (decisions 2 and 4), reopened the same day (`340684c`) and re-refined to pull (decisions 7 to 13).

- **CCW-018** ("Sessions and lanes" in `.claude/skills/tickets/SKILL.md`) lets each session own lanes (`docs/tickets/lanes.md` on 2026-11-01: `dfs-backend: backend`, `dfs-frontend: frontend`, `dfs-harness: harness`). A session "launches, lists as dispatchable and refines only tickets in its own lanes", and sessions send `SendMessage` "at exactly three moments and no others": a request filed, an unblock, a harness change landed.
- **What the user does now:** a third session, `dfs-product`, only writes, refines, reviews and closes tickets that the other sessions implement, and coordinates the backlog. On 2026-11-01 it refined DFS-046, DFS-047 and DFS-048 (frontend lane) to `ready` at the user's request, which the "refines only tickets in its own lanes" rule does not allow, and it asked `dfs-backend` and `dfs-frontend` by message to report every ticket created, every status change and every content change. Both did (for example DFS-051's state, CCW-020's amended criteria). That instruction lives only in those two conversations: it is lost when a session restarts or compacts, and it contradicts the "no others" rule.
- **The lane map's format** today: one line per session, `<name>: <lane>[, <lane>...]`; `validate` reports any other line form, so a session with no lane cannot be listed.
- **Messages carry no ticket content** (CCW-018): one line naming IDs and commits; the receiver reads the ticket from `main`. A message may cost the user an approval click in a session running in another permission mode.
- **Ticket changes made outside `main`:** `start` edits the ticket inside its worktree and never commits on `main`; the story pipeline commits per phase on the ticket branch; amended criteria during implementation land on the branch (CCW-020's `c17dfef`, `f77921b`). The coordinator cannot see these from `main`'s frontmatter alone; the catch-up reads every worktree (decision 8).
- **Token cost** (measured from this project's transcripts, `advisory/consultations/consult-001-question.md`): long-lived main sessions dominate; dfs-backend about 755M cache-read tokens over 2,915 calls, about 260k context per call; pipeline subagents under 10% of the total. Every turn resends the whole context, and every message wakes its receiver for a full turn at that size.
- **Advisory intake:** an advisor (Codex) publishes proposals under `advisory/proposals/`; the coordinator owns intake and writes responses under `advisory/responses/` per `advisory/README.md` and `advisory/schemas/response.schema.json` (committed in `eae7820`). ADV-001-r1 asks for discovery at the coordinator's next session start with no added context when nothing is pending.
- **story-gate** (`tools/story-gate/`) is a multi-file Java program run by the source launcher (`java tools/story-gate/StoryGate.java`, launchers `story-gate` and `story-gate.cmd`), with its own `Json`, `Git`, `Ticket` and `Story` classes and a self-test harness (`SelfTest`, `SelfTestCase`). Git Bash has no `jq`, and the project installs nothing globally.

## Decisions

1. **The coordinator is a role in place of lanes: `<name>: coordinator`** (2026-11-01). `coordinator` is a reserved word in the lane map, not a lane; at most one session holds it, and a session holding it owns no lane, so it can never launch anything ("Autonomous launch" requires a ticket in one of the session's own lanes). `validate` accepts the line and reports a second coordinator or `coordinator` mixed with lanes on one line. Moving the role is a one-line edit, like moving a lane. Not a separate `coordinator: <name>` line (looks like a session line to the parser) and not mixed with lanes (blurs "never launches").
2. **(Superseded by decision 7.) Reported changes: ticket changes plus pipeline stops** (2026-11-01). Every other session messages the coordinator when it: creates a ticket (draft or request); changes a ticket's status (`ready`, `in-progress`, `in-review`, `blocked`, unblocked, `done`, `cancelled`); changes a ticket's criteria, scenarios, `touches`, `depends_on` or decisions, on `main` or on a ticket branch; or reaches a pipeline stop (a human gate, a phase `blocked`, a refused autonomous launch: the moments the `story` skill already sends a push notification for). Ordinary phase commits are not reported (`plan.json` has them), so the `story` skill joins `touches`. Not ticket changes alone (the coordinator could not tell the user what waits on them) and not status alone (misses criteria amended on a branch, as CCW-020's were in `c17dfef` and `f77921b`).
3. **The coordinator writes tickets in every lane but never launches, starts or closes** (2026-11-01). It may `new` (drafts and requests) and `refine` in any lane, including the `ready` confirmation and its `docs: refine <ID>` commit, and record decisions; `start`, `/story run`, autonomous launches, `done` and `cancel` stay with the lane's owner, since they create, merge or remove that session's branches and worktrees. It may commit another session's uncommitted draft on `main` only after telling that session in the same turn (the DFS-045 case, `c4d2486`). It commits its own edits to shared files on `main` (design docs, `lanes.md`, drafts) in the turn it makes them, staged by name, rather than holding them for a later step: an uncommitted edit of its own in `main`'s checkout blocked `dfs-frontend`'s merge of CCW-020 on 2026-11-01 until `51e928b`. After a `ready` commit in another lane, it messages that lane's owner as for a request (message 1's form). Its `list` shows every lane's rows and every lane's "Dispatchable now" and "Started elsewhere", grouped by lane. Not `done`/`cancel` for any lane (would merge and remove worktrees another session works in) and not refine-only-when-asked (every queue refill would wait on the user).
4. **(Superseded by decision 7.) One batched line per turn; nothing sent or queued when the coordinator is absent** (2026-11-01). Message 4's form: `<ID> <what changed> in <sha or "uncommitted">[; <ID> ...]`, one message per turn listing every reported change of that turn, carrying no ticket content (CCW-018). Sent by the session that made the change, never by the coordinator itself. If `ListAgents` shows no peer with the coordinator's name, nothing is sent and nothing is queued; a coordinator that starts (or is resumed) catches up from `git log` on `main`, `git status` and `git worktree list` before its first `list`. A change that also sends an owner message 1, 2 or 3 is still reported to the coordinator, in the same turn's batched line. Not a file inbox (a shared file every session writes, for a rare case) and not one message per change (each may cost the user an approval click).
5. **`archetype: none`; the session joints are run for real, the rest is read** (2026-11-01, run list revised in the re-refinement). No archetype covers instruction edits plus a tool subcommand (CCW-017 and CCW-018 were built by hand); `dfs-harness` implements it, owning `harness`. Each scenario below is marked `[run]` (exercised for real, recorded in `verification.md` with the commands, outputs and shas seen) or `[read]` (checked by reading the changed text). Run for real: the lane map rule, the catch-up after a real `/clear` in dfs-product and in a lane session, the advisory listing (with scratch proposals, never committed), the coordinator's `list`, a pipeline stop seen in the catch-up, the autorun hand-off, and the search for old mentions; these are where CCW-018.L-1 found instruction changes break, at the joints between sessions. The script's parsing is also covered by story-gate self-test cases (decision 13). The live runs may borrow real work in flight (a gate reached after CCW-021 lands, such as DFS-047's or DFS-051's) rather than a throwaway ticket.
6. **An autorun ticket the coordinator makes ready is handed to its owner as an unblock** (2026-11-01, follows from decision 3). The coordinator cannot launch (decision 1), so after its `ready` commit of an `autorun: true` ticket whose `depends_on` are all `done`, it sends the owner message 2's form (`<ID> ready in <sha>: unblocks <ID> (<lane>, autorun); run the launch check and start the lowest`), which the owner handles under "Receiving an unblock" (launch point 3). A ticket made ready with a dependency still open needs no message beyond message 1's form; the owner's `done` on the dependency launches it as today.
7. **Pull, not push; advisory discovery folded in; the work split in three** (2026-11-01, re-refinement, decided by the user). Decisions 2 and 4 are superseded: no session reports ticket changes to the coordinator; the coordinator reads the state itself. Reason: long-lived main sessions dominate this project's token use (dfs-backend about 260k context per call, pipeline subagents under 10% of the total; `advisory/consultations/consult-001-question.md`), and every message wakes a session for a full turn at its current context size. The goal is parallel sessions optimized for token usage and speed, so sessions are disposable: state lives on disk and a session rebuilds it cheaply after a `/clear`. ADV-001-r1's advisory discovery (`advisory/proposals/ADV-001-r1.md`, contract in `advisory/README.md`) is the same mechanism and is built here. Split: CCW-021 builds the coordinator role and the catch-up (this ticket); CCW-022 adds the context-hygiene rules (where sessions clear and compact, the "safe to /clear" lines), depending on this ticket; CCW-023 wraps the contextual Codex consultation (ADV-002), depending on this ticket.
8. **The catch-up script prints the full picture at session start, and nothing at any other time** (2026-11-01, revised the same day by the user). At session start (startup, resume, `/clear`, compact) it prints, in a few lines: every worktree with its ticket ID and branch; each worktree's `plan.json` state and phase; uncommitted ticket and `plan.json` files in `main` and in every worktree. It prints IDs, branches, states and paths, never ticket content, and keeps no state between runs (no cursor). Mid-session the coordinator runs `/tickets list` when it needs fresh state; a gate waiting for the user already reaches them through the `story` skill's push notification. Not changes-only with a commit cursor (a cleared session would get no picture, and one commit cannot mark "seen" across branches) and not the changes on each prompt (first recorded, then dropped: with frequent clears it bought little, and it needed a cursor, a second hook and most of the verification). Can be added later without changing the rest.
9. **Pending advice is listed at session start only** (2026-11-01, the user's choice). At session start the catch-up also lists every advisory proposal (`advisory/proposals/ADV-NNN-rN.md`; any other file, such as a `*.tmp`, is ignored) that has no response in `advisory/responses/` or a response with status `received`, one line each: ID, revision, title, path. An older revision is flagged `superseded by rM` when `ADV-NNN-rM.md` exists with M greater than N, cross-checked against the newer file's `Supersedes:` line; the coordinator records a `superseded` response for it instead of evaluating it. A response file that is not valid JSON is reported by path. Nothing advisory is printed on a prompt, and the advisory part keeps no cursor: it is read fresh at every start. Enough because sessions clear often (CCW-022 clears the coordinator after every refine), so a proposal waits at most until the next start; this is ADV-001's "next processing point". Not new proposals on each prompt (a cursor for little gain) and not pending proposals on every prompt (a line on every turn while advice waits).
10. **The session-start hook finds its session by name; lanes stay in `lanes.md`** (2026-11-01). The hook takes the session's name from its own input if Claude Code supplies it there (to check during implementation), otherwise from the environment variable `DFS_SESSION`, set when the session is launched and kept through every `/clear`. It looks the name up in `docs/tickets/lanes.md` and prints the catch-up only when the name maps to `coordinator`; in any other session, or with no name, it prints nothing. With the variable, dfs-product is restarted once after this ticket lands (`$env:DFS_SESSION='dfs-product'; claude -n dfs-product` in PowerShell); the user then keeps sessions running and uses `/clear`, not new launches. The skills keep finding a session's lanes by `ListAgents` and its name. Asked and decided no: carrying each session's lanes in a launch variable (`DFS_LANES`) instead of `lanes.md`. Its premise was that names may be lost on `/clear`, but on 2026-11-01 `ListAgents` still reported "This session is dfs-product" right after a `/clear`; a variable would also make moving a lane a relaunch instead of a one-line edit and make "one owner per lane" uncheckable. Not a rule in the tickets skill to run the script on the first turn after a clear (after a `/clear` the session holds only CLAUDE.md and memory, so nothing would trigger it) and not the hook printing in every session (lane sessions' picture after a clear is CCW-022's to add).
11. **The coordinator's `/tickets list` runs the same script** (2026-11-01). Besides the session-start hook, the coordinator's `/tickets list` runs the catch-up script and shows its output under the table, so mid-session it sees worktree edits, `plan.json` states and pending advice, which the frontmatter on `main` does not show. The worktree and advisory logic lives only in the script; the skill calls it. No hook on each prompt and no other trigger. Not the session-start hook alone (decision 8 sends the coordinator to `/tickets list` for fresh state, which today shows no worktree edits or `plan.json` states) and not the script in every session's `list` (a lane-session change, CCW-022's open question).
12. **Pipeline stops are seen by pull; the `story` skill is not changed** (2026-11-01). Message 4 was never built (no mention of the coordinator under `.claude/`, `CLAUDE.md` or `docs/design/` on 2026-11-01), so nothing is removed. A human gate shows as `plan.json` state `awaiting-approval` and a blocked phase as `blocked`, both in the catch-up's per-worktree line with the gate or phase; a refused autonomous launch, which happens before any `plan.json` exists, shows in the coordinator's `list` as a `ready` `autorun` ticket with every dependency `done` and no branch, under "Dispatchable now". The user hears of every stop at once through the `story` skill's push notification; the coordinator sees it at its next `/clear` or `list`. The `story` skill leaves `touches` (CCW-022 edits it for its stop lines). Not a message for stops only (wakes the coordinator for a full turn at its context size, the cost decision 7 removed, while the stop is the user's to act on).
13. **The catch-up is a `catch-up` subcommand of story-gate** (2026-11-01). It reuses story-gate's `Json`, `Git`, `Ticket` and `Story` classes (the source launcher compiles only files in the program's own folder, so reuse means living there), and its parsing (worktree lines, `plan.json` states, uncommitted files, the advisory listing, the session-name lookup) is covered by new cases in story-gate's self-test harness. The hook in `.claude/settings.json` and the coordinator's `list` call it through story-gate's launcher. JVM start-up is paid once per session start or `list`; the implementer measures it and records the time in `verification.md`. Not a Bash script (no `jq` in Git Bash, so JSON would be read by pattern matching with no test harness) and not a separate Java tool (would copy the JSON and git classes, which then drift).

## Acceptance criteria

The full ID of each criterion is `CCW-021.AC-<n>`.

- **AC-1** `docs/tickets/lanes.md` lists `dfs-product: coordinator`, and `/tickets validate` accepts it, while it reports a second line holding `coordinator` and a line mixing `coordinator` with lanes, naming the line.
- **AC-2 (retired)** The `tickets` skill defines message 4: every session other than the coordinator sends it, once per turn, as one line `<ID> <what changed> in <sha or "uncommitted">[; <ID> ...]` without ticket content, for every ticket it created, every status change, and every change to criteria, scenarios, `touches`, `depends_on` or decisions, on `main` or on a ticket branch, also when the same change sends an owner message 1, 2 or 3. Retired 2026-11-01: pull replaces push (decision 7).
- **AC-3 (retired)** The `story` skill's notifications also send the coordinator message 4 at each stop that needs the user: a human gate, a phase `blocked`, a refused autonomous launch. Retired 2026-11-01: pipeline stops are seen by pull (decision 12), see AC-14.
- **AC-4 (retired)** When `ListAgents` shows no peer with the coordinator's name, no message 4 is sent or queued; the coordinator never sends message 4; a coordinator that starts or is resumed catches up from `git log` on `main`, `git status` and `git worktree list` before its first `list`. Retired 2026-11-01: pull replaces push (decision 7); the catch-up is AC-9 and AC-10.
- **AC-5** The coordinator may `new` and `refine` tickets in every lane, including the `ready` confirmation and its commit, and may commit another session's uncommitted draft on `main` only after telling that session in the same turn; it commits its own edits to shared files on `main` in the turn it makes them; it never runs `start`, `/story run`, an autonomous launch, `done` or `cancel`.
- **AC-6** After its `ready` commit of a ticket in another lane, the coordinator messages the owner: message 2's form for an `autorun` ticket whose dependencies are all `done`, which the owner handles under "Receiving an unblock"; message 1's form otherwise.
- **AC-7** `/tickets list` in the coordinator shows every lane's rows and every lane's "Dispatchable now" and "Started elsewhere", grouped by lane.
- **AC-8** No file under `.claude/`, `CLAUDE.md` or `docs/design/` still states the old message rule ("exactly three moments", "no others", "only when a request is filed") without the coordinator's message after a `ready` commit in another lane (decisions 3 and 6), and `CLAUDE.md`'s Tickets paragraph names the coordinator role and its catch-up, and no message 4.
- **AC-9** At session start (startup, resume, `/clear`, compact) in the coordinator, the catch-up prints every worktree with its ticket ID, branch and `plan.json` state and phase, and every uncommitted ticket or `plan.json` file in `main` and every worktree, as IDs, branches, states and paths without ticket content.
- **AC-10 (retired)** On each user prompt in the coordinator, the catch-up prints only the branch commits and uncommitted ticket or `plan.json` changes made since its last print, and prints nothing when nothing changed. Retired 2026-11-01: the catch-up runs only at session start (decision 8, revised).
- **AC-11** At session start in the coordinator, the catch-up lists each advisory proposal with no response or a `received` one (ID, revision, title, path), flags an older revision `superseded by rM` when a newer revision exists, ignores files not named `ADV-NNN-rN.md`, and reports an invalid response file by path.
- **AC-12** The session-start hook prints the catch-up only in the session whose name (from the hook's input, or else `DFS_SESSION`) maps to `coordinator` in `lanes.md`, and prints nothing in any other session or when no name is available.
- **AC-13** In the coordinator, `/tickets list` runs the catch-up script and shows its output (worktrees, `plan.json` states, uncommitted ticket and `plan.json` files, pending advice) under the table; in a lane session `list` is unchanged.
- **AC-14** The catch-up shows a worktree whose `plan.json` state is `awaiting-approval` with its pending gate and one whose state is `blocked` with its blocked phase; the `story` skill sends the coordinator no message.
- **AC-15** `story-gate catch-up` exists, and story-gate's self-tests include passing cases for: a worktree line with ticket ID, branch, `plan.json` state and phase; an uncommitted ticket file; a pending, an evaluated, a superseded and a `*.tmp` proposal; an invalid response file; and no session name (no output, exit 0).

## Scenarios

`[run]` scenarios are exercised for real across `dfs-product`, `dfs-backend`, `dfs-frontend` and `dfs-harness` after the change lands on `main` and each session has reread the skills (message 3), and recorded in `verification.md` with the commands, the outputs and messages seen, and the shas. Scratch proposals and lane-map lines used by a run are never committed. `[read]` scenarios are checked by reading the changed text, also recorded there. No scenario is presented as run when it was read.

```gherkin
Feature: Coordinator session

  Background:
    Given docs/tickets/lanes.md maps dfs-backend: backend, dfs-frontend: frontend, dfs-harness: harness and dfs-product: coordinator
    And the four sessions run from the repo checkout on main
    And dfs-product's name is available to the session-start hook (from the hook's input, or DFS_SESSION set at launch)

  @AC-1
  Scenario: [run] The lane map accepts one coordinator
    When /tickets validate runs
    Then it reports nothing about lanes.md

  @AC-1
  Scenario Outline: [run] The lane map refuses a misused coordinator
    Given lanes.md also holds the line "<line>" (in a scratch copy, never committed)
    When /tickets validate runs
    Then it reports that line, naming it

    Examples:
      | line                          |
      | dfs-other: coordinator        |
      | dfs-backend: backend, coordinator |

  @AC-9
  Scenario: [run] A cleared coordinator gets the full picture
    Given worktrees hold DFS-047, DFS-051 and CCW-021, and CCW-021's worktree holds an uncommitted edit to its ticket file
    When the user runs /clear in dfs-product
    Then the catch-up prints one line per worktree with its ticket ID, branch and plan.json state and phase
    And it names CCW-021's uncommitted ticket file
    And it prints no ticket content

  @AC-11
  Scenario: [run] Pending advice is listed at session start
    Given advisory/proposals/ holds ADV-001-r1.md with a received response, ADV-002-r1.md with an evaluated response, and a scratch ADV-003-r1.md with no response
    When the user runs /clear in dfs-product
    Then the catch-up lists ADV-001 r1 and ADV-003 r1 with their titles and paths
    And it does not list ADV-002 r1

  @AC-11
  Scenario: [run] A superseded revision is flagged
    Given a scratch ADV-003-r2.md whose Supersedes line names ADV-003-r1
    When the user runs /clear in dfs-product
    Then the catch-up flags ADV-003 r1 "superseded by r2" and lists ADV-003 r2 as pending

  @AC-11
  Scenario: [run] Temporary files are ignored
    Given a scratch ADV-004-r1.md.tmp in advisory/proposals/
    When the user runs /clear in dfs-product
    Then the catch-up does not mention the .tmp file

  @AC-12
  Scenario: [run] Only the coordinator gets the catch-up
    Given dfs-product was launched with its name available to the hook, and lanes.md maps dfs-product: coordinator
    When the user runs /clear in dfs-product and then in dfs-backend
    Then dfs-product's new session shows the catch-up
    And dfs-backend's new session shows nothing from the hook

  @AC-12
  Scenario: [run] No name means no output
    Given the hook script is run with no session name in its input and DFS_SESSION unset
    Then it prints nothing and exits with status 0

  @AC-13
  Scenario: [run] The coordinator's list shows mid-session state
    Given dfs-product's session started before a lane session ran /tickets start on a ready ticket in a new worktree, leaving the edit uncommitted
    When /tickets list runs in dfs-product
    Then the catch-up output under the table names that ticket's worktree and its uncommitted ticket file

  @AC-13
  Scenario: [read] A lane session's list is unchanged
    Then the tickets skill runs the catch-up script from list only in the coordinator

  @AC-14
  Scenario: [run] A pipeline stop shows in the catch-up
    Given a /story run in dfs-frontend or dfs-backend has stopped at a human gate, with plan.json state awaiting-approval
    When the user runs /clear in dfs-product
    Then the catch-up line for that worktree shows awaiting-approval and the pending gate
    And dfs-product received no message from the story run

  @AC-14
  Scenario: [read] A blocked phase shows in the catch-up
    Then the catch-up script prints state blocked and the blocked phase for a worktree whose plan.json is blocked

  @AC-5
  Scenario: [read] The coordinator writes tickets but never starts, launches or closes
    Then the tickets skill lets the coordinator run new and refine in every lane, including the ready commit
    And it lets the coordinator commit another session's uncommitted draft only after telling that session in the same turn
    And it has the coordinator commit its own edits to shared files on main in the turn it makes them
    And its start, done, cancel and autonomous launch rules refuse the coordinator, since it owns no lane
    And the story skill's run is never started by the coordinator

  @AC-6
  Scenario: [run] An autorun ticket made ready by the coordinator is handed over as an unblock
    Given dfs-product refines a frontend ticket with autorun true whose dependencies are all done
    When its ready commit lands on main
    Then dfs-frontend receives "<ID> ready in <sha>: unblocks <ID> (frontend, autorun); run the launch check and start the lowest"
    And dfs-frontend runs the launch check on it under "Receiving an unblock"

  @AC-6
  Scenario: [read] A ticket made ready with an open dependency
    Given dfs-product makes ready a frontend ticket whose dependency is not done
    Then the tickets skill has it send dfs-frontend message 1's form only
    And the owner's done on the dependency launches the ticket as before

  @AC-7
  Scenario: [run] The coordinator's list covers every lane
    When /tickets list runs in dfs-product
    Then the table holds the backend, frontend and harness tickets, grouped by lane
    And "Dispatchable now" and "Started elsewhere" are shown for each lane

  @AC-8
  Scenario: [run] No old mention of the message rule remains
    When .claude/, CLAUDE.md and docs/design/ are searched for "three moments", "exactly three", "no others" and "only when a request is filed"
    Then no hit states the old rule without the coordinator's message after a ready commit
    And CLAUDE.md's Tickets paragraph names the coordinator role and its catch-up
    And no file mentions a message 4

  @AC-15
  Scenario: [run] story-gate's self-tests cover the catch-up
    When tools/story-gate/story-gate selftest runs
    Then it passes, including the catch-up cases for a worktree line, an uncommitted ticket file, pending, evaluated, superseded and .tmp proposals, an invalid response file, and no session name
```

## Scope

**Touches:**
- `.claude/skills/tickets/SKILL.md`: "Sessions and lanes" (the coordinator role, its catch-up and `DFS_SESSION`, the coordinator's message after a `ready` commit and the hand-off of decision 6, the message rule's new wording), `refine`, `new` and `list` (what the coordinator may do and sees; its `list` runs the catch-up), `validate` (the lane map's new line form).
- `docs/tickets/lanes.md`: the line `dfs-product: coordinator`, the role's description, and launching a session with `DFS_SESSION`.
- `CLAUDE.md`: the Tickets paragraph's sentence on when sessions message each other, naming the coordinator and its catch-up.
- `.claude/settings.json`: the SessionStart hook calling `story-gate catch-up`.
- `tools/story-gate`: the `catch-up` subcommand and its self-test cases.

**Out of scope:**
- A coordinator that launches or implements anything: it never runs `/tickets start`, `/story run`, an autonomous launch, `done` or `cancel`.
- Any report message from lane sessions to the coordinator (decisions 2, 4 and 12).
- A catch-up on each prompt, or any cursor kept between runs (decision 8); hence no `advisory/.runtime/` and no `.gitignore` change.
- The catch-up in lane sessions, and where sessions clear or compact (CCW-022).
- The contextual Codex consultation wrapper (CCW-023).
- Lanes carried in a launch variable (decision 10).
- A coordinator that also owns lanes (decision 1).
- Changing the three autonomous launch points: the coordinator's hand-off uses launch point 3 as it is (decision 6).
- Writing advisory responses: the coordinator does that by hand per `advisory/README.md`; this ticket only lists what is pending.

## Definition of done

- [ ] Every scenario above is checked as marked (`[run]` exercised for real, `[read]` read) and recorded in `verification.md`, with the commands, messages and shas of each `[run]`
- [ ] The CCW-018.L-1 search for every mention of the old message rule ("three moments", "exactly three", "no others", "only when a request is filed", and spelled-out counts such as "three" near "message") is recorded in `verification.md` with its hits
- [ ] `tools/story-gate/story-gate selftest` passes
- [ ] Whether the SessionStart hook's input carries the session's name is checked against the installed Claude Code and recorded in `verification.md`, with the route taken (hook input or `DFS_SESSION`)
- [ ] The catch-up's run time and its output size (lines) at a real session start are recorded in `verification.md`
- [ ] Message 3 sent to every running session listed in `lanes.md` when the branch merges
- [ ] No files changed outside the paths listed under Touches
- [ ] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
