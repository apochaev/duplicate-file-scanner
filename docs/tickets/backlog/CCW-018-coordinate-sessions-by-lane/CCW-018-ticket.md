---
id: CCW-018
title: Coordinate parallel sessions through lane-owned tickets
status: draft
type: feature
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
depends_on: [CCW-017]
touches: []
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
- **Proposal to refine** (from the session that drafted this):
  1. A `lane:` field (`backend` | `frontend`) on every ticket; harness (`CCW`) tickets take the lane of the session that runs harness work.
  2. A request to the other session is a draft ticket in its lane, filed by the requesting session with the need and suggested scenarios; the requester's ticket names it in `depends_on`; the owning session refines it.
  3. `list` shows the session's own lane first and computes "Dispatchable now" for its own lane; `done` autoruns only its own lane's tickets.
  4. `SendMessage` as a doorbell, never as the content: one line pointing at ticket IDs and commits, at four moments (a request filed in the other lane; a close that unblocks the other lane; a shared file changed: `CLAUDE.md`, `settings.gradle.kts`, `cli` wiring; a harness change landed).
  5. Each session learns its lane from the user at the start; the peer is found with `ListAgents` each time.

## Acceptance criteria

The full ID of each criterion is `CCW-018.AC-<n>`.

- **AC-1** <placeholder>

## Scenarios

```gherkin
Feature: <placeholder>
```

## Scope

**Touches:**
- <to be decided in refinement>

**Out of scope:**
- A Linear mirror (deferred, see Context).
- More than two lanes.

## Definition of done

- [ ] Every scenario above is an automated check or a recorded read-through, as noted per scenario
- [ ] `./gradlew build` is green
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- The lane values, and the lane of `CCW` tickets.
- How a session learns its lane, and what a skill does when it does not know it.
- What exactly `done` does with tickets it unblocked in the other lane.
- The message moments, their wording, and what happens when the peer session is not running.
- Whether existing tickets get a `lane:` (backfill) or only new ones.
- How it is proven: a live exchange with the backend session.
