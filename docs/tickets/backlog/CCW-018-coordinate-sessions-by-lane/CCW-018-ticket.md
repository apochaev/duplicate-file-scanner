---
id: CCW-018
title: Coordinate parallel sessions through lane-owned tickets
status: ready
type: feature
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
depends_on: [CCW-017]
touches:
  - .claude/skills/tickets/SKILL.md
  - .claude/skills/tickets/TEMPLATE.md
  - CLAUDE.md
  - docs/tickets/lanes.md
  - docs/tickets/backlog/DFS-006-walk-directories-in-parallel/DFS-006-ticket.md
  - docs/tickets/backlog/DFS-007-benchmark-hash-vs-compare/DFS-007-ticket.md
  - docs/tickets/backlog/DFS-021-preview-deletion-plan-cli/DFS-021-ticket.md
  - docs/tickets/backlog/DFS-022-preview-deletion-plan/DFS-022-ticket.md
  - docs/tickets/backlog/CCW-017-autonomous-run-to-po-review/CCW-017-ticket.md
  - docs/tickets/backlog/CCW-018-coordinate-sessions-by-lane/
  - docs/tickets/backlog/
blocked_reason:
created: 2026-10-31
updated: 2026-10-31
completed:
---

# CCW-018: Coordinate parallel sessions through lane-owned tickets

## Story

As the product owner running a backend session and a frontend session in parallel
I want each ticket to name the session that owns it, cross-session requests to be tickets linked by `depends_on`, and the other session to be told when something concerns it
So that the two sessions never start each other's work, never wait on something nobody knows is needed, and I do not have to relay messages between them

## Context

- **Two sessions in parallel** (decided 2026-10-31): a backend session (`file-scanner`, `cli`; currently the local session named `DFS-main`) and a frontend session (the planned `server` subproject and its Angular UI, `docs/design/ui.md`). Both work from the same repo checkout on `main` and read the same backlog through git. The UI's tickets #8–#10 (`ui.md`, "Suggested ticket order") depend on four new core features owned by the backend: DFS-022 refined to the U5/U6 plan model, `ListDirectory`, the drift check, and content verify.
- **The gap in CCW-017:** `/tickets done <X>` autoruns every `ready` + `autorun` ticket that X's close unblocked, in the session that ran `done`. When the backend closes DFS-022, that would start the UI's plan-editor ticket inside the backend session.
- **Communication between local sessions exists:** `ListAgents` lists the other local Claude Code sessions on this machine (names change per session, e.g. `DFS-main`), and `SendMessage` delivers a message into one. Verified 2026-10-31 from this session.
- **Memory is shared:** both sessions run in the same project directory and so share its auto-memory folder; a session's lane cannot be stored there.
- **Linear was considered and deferred** (2026-10-31): the pipeline reads tickets from files (`tools/story-gate`, `plan.json` and friends in the ticket folder, a ticket copy per worktree); Linear's free tier caps at 250 issues. A one-way mirror to Linear may come later as its own ticket; `lane` and `depends_on` would map onto Linear teams and "blocked by" relations.
- **Original proposal** (the settled shape is under Decisions):
  1. A `lane:` field (`backend` | `frontend`) on every ticket; harness (`CCW`) tickets take the lane of the session that runs harness work.
  2. A request to the other session is a draft ticket in its lane, filed by the requesting session with the need and suggested scenarios; the requester's ticket names it in `depends_on`; the owning session refines it.
  3. `list` shows the session's own lane first and computes "Dispatchable now" for its own lane; `done` autoruns only its own lane's tickets.
  4. `SendMessage` as a doorbell, never as the content: one line pointing at ticket IDs and commits, at four moments (a request filed in the other lane; a close that unblocks the other lane; a shared file changed: `CLAUDE.md`, `settings.gradle.kts`, `cli` wiring; a harness change landed).
  5. Each session learns its lane from the user at the start; the peer is found with `ListAgents` each time.

## Decisions

1. **Three lanes, `backend` | `frontend` | `harness`** (2026-10-31). A lane names the kind of work, not the session; each session declares which lanes it owns (now: the frontend session owns `frontend` and `harness`, the backend session owns `backend`). Moving harness ownership needs no ticket edits. `CCW` tickets are `harness`.
2. **Session names plus a committed lane map** (2026-10-31). Sessions get stable names (e.g. `dfs-backend`, `dfs-frontend`); `docs/tickets/lanes.md` maps each name to the lanes it owns. A session finds its own name and its peers' with `ListAgents` and looks both up in the map, which tells it its own lanes and whom to message about another lane. If its own name is not in the map, it asks the user which lanes it owns and offers to add the line. Memory and a local file cannot hold this: both sessions share the project memory and the checkout.
3. **Cross-lane unblocks become a third launch point** (2026-10-31). `done` launches only its own lanes' tickets; for each other lane it messages the owning session with the unblocked tickets and the close commit, and that session runs CCW-017's launch check and starts the lowest-ID one. The authorization is still the ticket's own `autorun` approval commit; the message only says when to look. If the owning session is not running, `done` says so and the ticket waits for that session's next `list`.
4. **Three message moments: request filed, unblock, harness change landed** (2026-10-31). No messages for shared-file edits: staging by name and pulling `main` already cover them, and each message may cost the user an approval click in a session running in another permission mode. A request is a draft ticket in the other lane, committed alone on `main` by the requester and named in the requester's `depends_on`; the owner refines it. Messages are one line in the body's first line, point at ticket IDs and commits, and never carry the content.
5. **Backfill open tickets only; `list` by lane** (2026-10-31, default taken without objection). Every non-terminal ticket gets a `lane:` (DFS-006, DFS-007, DFS-021, DFS-022 → `backend`; CCW-017, CCW-018 → `harness`); completed tickets are left untouched and not checked. `list` shows this session's lanes first and the rest under "Other lanes"; "Dispatchable now" covers only its own lanes.
6. **Proof: mechanical runs, plus a live exchange for requests and the harness message** (2026-10-31). The cross-lane launch (AC-4, AC-5) is proven mechanically in a scratch repo; its first real occurrence (expected when `ListDirectory` closes and unblocks the folder-view ticket) is recorded as a learnings entry, not awaited. Sessions are renamed `dfs-frontend` and `dfs-backend` for the live exchange; how to rename a session is confirmed during implementation, and AC-3's question is the fallback. The `docs/tickets/backlog/` entry in `touches` covers the new request ticket folders the live exchange creates.

## Acceptance criteria

The full ID of each criterion is `CCW-018.AC-<n>`.

- **AC-1** A ticket's frontmatter carries `lane:` with one of `backend`, `frontend`, `harness`; every `CCW` ticket is `harness`, and `/tickets validate` reports a missing or other value on a non-terminal ticket and a `CCW` ticket whose lane is not `harness`.
- **AC-2** `docs/tickets/lanes.md` maps session names to lanes, one line per session (`<name>: <lane>[, <lane>...]`), and every lane is owned by at most one listed session; `/tickets validate` reports a lane owned twice or an unknown lane value there.
- **AC-3** A skill that needs the session's own lanes takes this session's name from `ListAgents` and reads its lanes from `lanes.md`; when the name is not listed, it asks the user which lanes this session owns, offers to add the line (committed alone on `main`), and uses the answer for the rest of the conversation either way.
- **AC-4** `/tickets done <X>` launches (CCW-017 step 4a) only tickets in this session's own lanes; for unblocked `ready` + `autorun` tickets in another lane it sends one `SendMessage` per owning session, naming each ticket, its lane and X's close commit, and launches none of them itself.
- **AC-5** A session that receives such a message runs CCW-017's launch check on the named tickets in its own lanes and starts `/story run` for the lowest ID that passes, listing the others; it ignores tickets outside its lanes, and the message alone never authorizes a ticket without its `autorun` approval commit.
- **AC-6** When no running session owns the other lane (no `lanes.md` name for it appears in `ListAgents`), `done` says which tickets wait and for which lane, and that session's next `/tickets list` names them under "Dispatchable now".
- **AC-7** `/tickets new` can file a request for another lane: it creates the ticket as a `draft` with that `lane:`, a Context naming the requesting ticket, the need and the design reference, and suggested scenarios under Open questions; commits it alone on `main` as `docs: request <ID> for <lane> [<ID>, <requesting ID>]`; adds it to the requesting ticket's `depends_on`; and messages the session owning that lane with one line naming both IDs.
- **AC-8** When a `harness` ticket's branch is merged into `main` (by `/tickets done` or ahead of it), the session messages every other running session in `lanes.md` with one line naming the ticket, the merge commit and the skill or agent file that changed.
- **AC-9** No message is sent for edits to shared files; every message's first line is self-contained, names ticket IDs and commits, and carries no ticket content.
- **AC-10** `/tickets list` prints this session's lanes first and the other lanes under "Other lanes", each row showing its lane; "Dispatchable now" and "Started elsewhere" cover only this session's lanes.
- **AC-11** Every non-terminal ticket in the backlog has a `lane:` after this ticket (DFS-006, DFS-007, DFS-021, DFS-022 `backend`; CCW-017, CCW-018 `harness`), and `/tickets validate` reports none missing.
- **AC-12** In a live exchange with the backend session, this session files the three new backend requests through AC-7 (`ListDirectory`, drift check, content verify; each a draft with `lane: backend`, linked from a frontend ticket's `depends_on`), each request's message reaches the backend session and is acknowledged, and CCW-018's merge sends the AC-8 message.

## Scenarios

Each scenario is marked **[mechanical]** (run in a scratch git repo, output in `verification.md`), **[read]** (checked by reading the edited instructions) or **[live]** (observed with the backend session).

```gherkin
Feature: Coordinate parallel sessions through lane-owned tickets

  Background:
    Given docs/tickets/lanes.md lists "dfs-backend: backend" and "dfs-frontend: frontend, harness"
    And both sessions share the repo checkout on main

  # [mechanical]
  @AC-1 @AC-11
  Scenario: validate checks lanes on open tickets
    Given scratch copies of an open ticket without lane, one with "lane: ui", a CCW ticket with "lane: backend", and a done ticket without lane
    When the validate rules for lanes run
    Then the first three are reported, naming ticket and field, and the done ticket is not

  # [mechanical]
  @AC-2
  Scenario: validate checks the lane map
    Given a scratch lanes.md where "frontend" is owned by two names and one line names lane "ops"
    When the validate rules for lanes.md run
    Then both problems are reported

  # [read] [live]
  @AC-3
  Scenario: A session finds its lanes from its name
    Given this session's name from ListAgents is "dfs-frontend"
    When /tickets list needs this session's lanes
    Then it uses frontend and harness without asking

  # [read]
  @AC-3
  Scenario: An unknown session name falls back to a question
    Given this session's name is "duplicate-file-scanner-d1", not in lanes.md
    When a skill needs this session's lanes
    Then it asks which lanes this session owns, offers to add the line, and uses the answer either way

  # [mechanical]
  @AC-4 @AC-6
  Scenario: done launches only its own lanes and messages the rest
    Given scratch tickets DFS-950 (backend, closing), DFS-951 (backend, ready, autorun, depends on DFS-950) and DFS-952 (frontend, ready, autorun, depends on DFS-950)
    When the AC-4 selection runs in a session owning backend
    Then DFS-951 is selected for launch and DFS-952 goes into one message to the frontend owner, naming DFS-952, its lane and the close commit
    And with no frontend owner among running sessions, the output names DFS-952 as waiting for lane frontend

  # [mechanical]
  @AC-5
  Scenario: The receiving session launches only what it owns and what was approved
    Given a message naming DFS-952 (frontend, approved by its refine commit), DFS-953 (frontend, edited after approval) and DFS-951 (backend)
    When the AC-5 handling runs in a session owning frontend
    Then DFS-952 passes the launch check and is selected, DFS-953 is refused as changed since approval, and DFS-951 is ignored

  # [read] [live]
  @AC-7 @AC-9 @AC-12
  Scenario: Filing a request for the backend
    Given the frontend ticket that needs a directory listing
    When this session files the ListDirectory request
    Then a draft ticket with "lane: backend" is committed alone on main as "docs: request <ID> for backend [<ID>, <requesting ID>]"
    And the requesting ticket's depends_on names it
    And dfs-backend receives one message whose first line names both IDs and carries no ticket content
    And the backend session acknowledges it

  # [read] [live]
  @AC-8 @AC-9 @AC-12
  Scenario: A harness change announces itself
    Given CCW-018's branch is merged into main
    When the merge lands
    Then every other running session in lanes.md gets one message naming CCW-018, the merge commit and .claude/skills/tickets/SKILL.md

  # [read]
  @AC-9
  Scenario: Shared-file edits send no message
    Given this session commits a change to CLAUDE.md
    Then no message is sent

  # [read]
  @AC-10
  Scenario: list groups by lane
    Given open tickets in all three lanes
    When /tickets list runs in dfs-frontend
    Then frontend and harness tickets come first, backend tickets under "Other lanes"
    And "Dispatchable now" holds only frontend and harness tickets

  # [mechanical]
  @AC-11
  Scenario: Open tickets are backfilled
    When the lane rule runs over the real backlog after this ticket
    Then every non-terminal ticket has a lane, with DFS-006, DFS-007, DFS-021, DFS-022 backend and CCW-017, CCW-018 harness
```

## Scope

**Touches:**
- `.claude/skills/tickets/SKILL.md`: `lane:` field; a "Sessions and lanes" section (lane map, own-lane lookup, fallback question, messages); `new` files requests (AC-7); `list` by lane (AC-10); `done` step 4a split by lane (AC-4, AC-6) and the harness-landed message (AC-8); the receiving side of an unblock message (AC-5); `validate` lane rules (AC-1, AC-2).
- `.claude/skills/tickets/TEMPLATE.md`: a `lane:` line.
- `CLAUDE.md`: the Tickets section names lanes and `lanes.md`.
- `docs/tickets/lanes.md`: new, the lane map.
- The open tickets DFS-006, DFS-007, DFS-021, DFS-022, CCW-017: one `lane:` line each (AC-11).
- `docs/tickets/backlog/CCW-018-coordinate-sessions-by-lane/`: this ticket and its `verification.md`.
- New request ticket folders under `docs/tickets/backlog/`, created by the AC-12 live exchange.

**Out of scope:**
- A Linear mirror (deferred, see Context).
- Messages for shared-file edits (decision 4).
- Refining the request tickets: the backend session owns them.
- Backfilling completed tickets.

## Definition of done

- [ ] Every [read] scenario checked against the edited files, noted in `verification.md`
- [ ] Every [mechanical] scenario run in a scratch repo, commands and output in `verification.md`
- [ ] The AC-12 live exchange happened: three request tickets on `main`, the backend session's acknowledgements and the AC-8 message recorded in `verification.md`
- [ ] `./gradlew build` is green
- [ ] `/tickets validate` reports nothing new
- [ ] No files changed outside the paths listed under Touches
- [ ] `verification.md` opens with the `## Product owner review` section

## Open questions

None.
