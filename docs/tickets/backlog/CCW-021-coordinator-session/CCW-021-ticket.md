---
id: CCW-021
title: Add a coordinator session that is told of every ticket change
status: ready
type: docs
scope: [docs]
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
  - .claude/skills/story/SKILL.md
blocked_reason:
created: 2026-11-01
updated: 2026-11-01
completed:
---

# CCW-021: Add a coordinator session that is told of every ticket change

## Story

As the user running several Claude Code sessions on this backlog
I want one coordinator session that owns no lane, refines tickets for every lane, and is messaged by the other sessions whenever they create a ticket, change its status or change its content
So that one place knows the whole backlog's state and keeps every lane's queue full, without me relaying between sessions

## Context

Filed 2026-11-01 at the user's request, from the session `dfs-product`.

- **CCW-018** ("Sessions and lanes" in `.claude/skills/tickets/SKILL.md`) lets each session own lanes (`docs/tickets/lanes.md`: `dfs-backend: backend`, `dfs-frontend: frontend, harness`). A session "launches, lists as dispatchable and refines only tickets in its own lanes", and sessions send `SendMessage` "at exactly three moments and no others": a request filed, an unblock, a harness change landed.
- **What the user does now:** a third session, `dfs-product`, only writes, refines, reviews and closes tickets that the other sessions implement, and coordinates the backlog. On 2026-11-01 it refined DFS-046, DFS-047 and DFS-048 (frontend lane) to `ready` at the user's request, which the "refines only tickets in its own lanes" rule does not allow, and it asked `dfs-backend` and `dfs-frontend` by message to report every ticket created, every status change and every content change. Both did (for example DFS-051's state, CCW-020's amended criteria). That instruction lives only in those two conversations: it is lost when a session restarts or compacts, and it contradicts the "no others" rule.
- **The lane map's format** today: one line per session, `<name>: <lane>[, <lane>...]`; `validate` reports any other line form, so a session with no lane cannot be listed.
- **Messages carry no ticket content** (CCW-018): one line naming IDs and commits; the receiver reads the ticket from `main`. A message may cost the user an approval click in a session running in another permission mode.
- **Ticket changes made outside `main`:** `start` edits the ticket inside its worktree and never commits on `main`; the story pipeline commits per phase on the ticket branch; amended criteria during implementation land on the branch (CCW-020's `c17dfef`, `f77921b`). The coordinator cannot see these from `main` without a message.

## Decisions

1. **The coordinator is a role in place of lanes: `<name>: coordinator`** (2026-11-01). `coordinator` is a reserved word in the lane map, not a lane; at most one session holds it, and a session holding it owns no lane, so it can never launch anything ("Autonomous launch" requires a ticket in one of the session's own lanes). `validate` accepts the line and reports a second coordinator or `coordinator` mixed with lanes on one line. Moving the role is a one-line edit, like moving a lane. Not a separate `coordinator: <name>` line (looks like a session line to the parser) and not mixed with lanes (blurs "never launches").
2. **Reported changes: ticket changes plus pipeline stops** (2026-11-01). Every other session messages the coordinator when it: creates a ticket (draft or request); changes a ticket's status (`ready`, `in-progress`, `in-review`, `blocked`, unblocked, `done`, `cancelled`); changes a ticket's criteria, scenarios, `touches`, `depends_on` or decisions, on `main` or on a ticket branch; or reaches a pipeline stop (a human gate, a phase `blocked`, a refused autonomous launch: the moments the `story` skill already sends a push notification for). Ordinary phase commits are not reported (`plan.json` has them), so the `story` skill joins `touches`. Not ticket changes alone (the coordinator could not tell the user what waits on them) and not status alone (misses criteria amended on a branch, as CCW-020's were in `c17dfef` and `f77921b`).
3. **The coordinator writes tickets in every lane but never launches, starts or closes** (2026-11-01). It may `new` (drafts and requests) and `refine` in any lane, including the `ready` confirmation and its `docs: refine <ID>` commit, and record decisions; `start`, `/story run`, autonomous launches, `done` and `cancel` stay with the lane's owner, since they create, merge or remove that session's branches and worktrees. It may commit another session's uncommitted draft on `main` only after telling that session in the same turn (the DFS-045 case, `c4d2486`). It commits its own edits to shared files on `main` (design docs, `lanes.md`, drafts) in the turn it makes them, staged by name, rather than holding them for a later step: an uncommitted edit of its own in `main`'s checkout blocked `dfs-frontend`'s merge of CCW-020 on 2026-11-01 until `51e928b`. After a `ready` commit in another lane, it messages that lane's owner as for a request (message 1's form). Its `list` shows every lane's rows and every lane's "Dispatchable now" and "Started elsewhere", grouped by lane. Not `done`/`cancel` for any lane (would merge and remove worktrees another session works in) and not refine-only-when-asked (every queue refill would wait on the user).
4. **One batched line per turn; nothing sent or queued when the coordinator is absent** (2026-11-01). Message 4's form: `<ID> <what changed> in <sha or "uncommitted">[; <ID> ...]`, one message per turn listing every reported change of that turn, carrying no ticket content (CCW-018). Sent by the session that made the change, never by the coordinator itself. If `ListAgents` shows no peer with the coordinator's name, nothing is sent and nothing is queued; a coordinator that starts (or is resumed) catches up from `git log` on `main`, `git status` and `git worktree list` before its first `list`. A change that also sends an owner message 1, 2 or 3 is still reported to the coordinator, in the same turn's batched line. Not a file inbox (a shared file every session writes, for a rare case) and not one message per change (each may cost the user an approval click).
5. **`archetype: none`; the session joints are run for real, the rest is read** (2026-11-01). No archetype covers instruction edits (CCW-017 and CCW-018 were built by hand); `dfs-frontend` implements it, owning `harness`. Each scenario below is marked `[run]` (exercised for real, recorded in `verification.md` with the commands, messages and shas seen) or `[read]` (checked by reading the changed text). Run for real: the lane map rule, the coordinator's `list`, a batched report from a real ticket change, a pipeline-stop message, the autorun hand-off, and the search for old mentions; these are where CCW-018.L-1 found instruction changes break, at the joints between sessions. The live runs may borrow real work in flight (a gate reached after CCW-021 lands, such as DFS-047's done gate or DFS-048's; DFS-047's design gate comes too early) rather than a throwaway ticket.
6. **An autorun ticket the coordinator makes ready is handed to its owner as an unblock** (2026-11-01, follows from decision 3). The coordinator cannot launch (decision 1), so after its `ready` commit of an `autorun: true` ticket whose `depends_on` are all `done`, it sends the owner message 2's form (`<ID> ready in <sha>: unblocks <ID> (<lane>, autorun); run the launch check and start the lowest`), which the owner handles under "Receiving an unblock" (launch point 3). A ticket made ready with a dependency still open needs no message beyond message 1's form; the owner's `done` on the dependency launches it as today.

## Acceptance criteria

The full ID of each criterion is `CCW-021.AC-<n>`.

- **AC-1** `docs/tickets/lanes.md` lists `dfs-product: coordinator`, and `/tickets validate` accepts it, while it reports a second line holding `coordinator` and a line mixing `coordinator` with lanes, naming the line.
- **AC-2** The `tickets` skill defines message 4: every session other than the coordinator sends it, once per turn, as one line `<ID> <what changed> in <sha or "uncommitted">[; <ID> ...]` without ticket content, for every ticket it created, every status change, and every change to criteria, scenarios, `touches`, `depends_on` or decisions, on `main` or on a ticket branch, also when the same change sends an owner message 1, 2 or 3.
- **AC-3** The `story` skill's notifications also send the coordinator message 4 at each stop that needs the user: a human gate, a phase `blocked`, a refused autonomous launch.
- **AC-4** When `ListAgents` shows no peer with the coordinator's name, no message 4 is sent or queued; the coordinator never sends message 4; a coordinator that starts or is resumed catches up from `git log` on `main`, `git status` and `git worktree list` before its first `list`.
- **AC-5** The coordinator may `new` and `refine` tickets in every lane, including the `ready` confirmation and its commit, and may commit another session's uncommitted draft on `main` only after telling that session in the same turn; it commits its own edits to shared files on `main` in the turn it makes them; it never runs `start`, `/story run`, an autonomous launch, `done` or `cancel`.
- **AC-6** After its `ready` commit of a ticket in another lane, the coordinator messages the owner: message 2's form for an `autorun` ticket whose dependencies are all `done`, which the owner handles under "Receiving an unblock"; message 1's form otherwise.
- **AC-7** `/tickets list` in the coordinator shows every lane's rows and every lane's "Dispatchable now" and "Started elsewhere", grouped by lane.
- **AC-8** No file under `.claude/`, `CLAUDE.md` or `docs/design/` still states the old message rule ("exactly three moments", "no others", "only when a request is filed"), and `CLAUDE.md`'s Tickets paragraph names the coordinator and message 4.

## Scenarios

`[run]` scenarios are exercised for real across `dfs-product`, `dfs-backend` and `dfs-frontend` after the change lands on `main` and each session has reread the skills (message 3), and recorded in `verification.md` with the commands, the messages received and the shas. `[read]` scenarios are checked by reading the changed text, also recorded there. No scenario is presented as run when it was read.

```gherkin
Feature: Coordinator session

  Background:
    Given docs/tickets/lanes.md maps dfs-backend: backend, dfs-frontend: frontend, harness, and dfs-product: coordinator
    And the three sessions run from the repo checkout on main

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

  @AC-2
  Scenario: [run] A lane session reports one turn's ticket changes in one line
    Given dfs-backend refines a backend draft to ready and amends its depends_on in one turn
    When the turn's ready commit lands on main
    Then dfs-product receives exactly one message from dfs-backend for that turn
    And it reads "<ID> ready in <sha>; <ID> depends_on amended in <sha>" or the same changes in that form
    And it holds no ticket content

  @AC-2
  Scenario: [run] A change on a ticket branch is reported
    Given a ticket in progress in its worktree gets an amended criterion, committed on its branch
    When that turn ends
    Then dfs-product receives a message 4 naming the ticket, the change and the branch commit

  @AC-2
  Scenario: [read] A change that already messages the owner is still reported
    Given a close that unblocks another lane's autorun ticket sends that owner message 2
    Then the tickets skill also has the closing session report the close to the coordinator in that turn's message 4

  @AC-3
  Scenario: [run] A pipeline stop reaches the coordinator
    Given a /story run in dfs-frontend or dfs-backend reaches a human gate
    When the story skill sends its push notification
    Then dfs-product receives a message 4 naming the ticket and the gate

  @AC-4
  Scenario: [read] An absent coordinator gets nothing, and catches up on start
    Given ListAgents in dfs-backend shows no dfs-product
    Then the tickets skill has dfs-backend send and queue nothing for message 4
    And it has a starting coordinator read git log on main, git status and git worktree list before its first list
    And it never has the coordinator send message 4 itself

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
    Then no hit states the old rule
    And CLAUDE.md's Tickets paragraph names the coordinator and message 4
```

## Scope

**Touches:**
- `.claude/skills/tickets/SKILL.md`: "Sessions and lanes" (the coordinator role, message 4, the catch-up, the hand-off of decision 6), `refine`, `new` and `list` (what the coordinator may do and sees), `validate` (the lane map's new line form).
- `docs/tickets/lanes.md`: the line `dfs-product: coordinator` and the role's description.
- `CLAUDE.md`: the Tickets paragraph's sentence on when sessions message each other.
- `.claude/skills/story/SKILL.md`: "Notifications" also messages the coordinator at each stop (decision 2).

**Out of scope:**
- A coordinator that launches or implements anything: it never runs `/tickets start`, `/story run`, an autonomous launch, `done` or `cancel`.
- Replacing messages with a shared log file or polling `git log` alone (considered 2026-11-01; misses uncommitted and branch-only changes).
- A coordinator that also owns lanes (decision 1).
- Changing the three autonomous launch points: the coordinator's hand-off uses launch point 3 as it is (decision 6).

## Definition of done

- [ ] Every scenario above is checked as marked (`[run]` exercised for real, `[read]` read) and recorded in `verification.md`, with the commands, messages and shas of each `[run]`
- [ ] The CCW-018.L-1 search for every mention of the old message rule ("three moments", "exactly three", "no others", "only when a request is filed", and spelled-out counts such as "three" near "message") is recorded in `verification.md` with its hits
- [ ] Message 3 sent to every running session listed in `lanes.md` when the branch merges
- [ ] No files changed outside the paths listed under Touches
- [ ] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
