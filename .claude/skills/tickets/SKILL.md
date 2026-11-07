---
name: tickets
description: Manage the project backlog in docs/tickets/ - create, refine, list, start, review, block, close, cancel or validate tickets (user stories in Gherkin). Use when the user mentions tickets, the backlog, stories, refining work, or asks what to work on next.
---

# Tickets

The backlog lives in `docs/tickets/`. A ticket is one user story, refined well enough that an agent can implement it from the ticket and `CLAUDE.md` alone.

Usage: `/tickets <mode> [args]`. Modes: `new`, `refine`, `list`, `start`, `review`, `block`, `unblock`, `done`, `cancel`, `validate`. With no mode, run `list`.

## Layout

```
docs/tickets/
  backlog/{ID}-{slug}/{ID}-ticket.md     every status except done and cancelled
  completed/{ID}-{slug}/{ID}-ticket.md   done and cancelled
```

- The frontmatter `status` is authoritative. The folder is derived from it: only `done` and `cancelled` live in `completed/`. Create the directories if missing.
- The ticket folder may hold attachments (diagrams, sample data). `{ID}-ticket.md` must be complete on its own.
- **ID**: `DFS-NNN` or `CCW-NNN`, zero-padded to three digits; see Namespaces below for which prefix applies and how each sequence numbers itself. Never reuse an ID, in either sequence.
- **Slug**: lowercase ASCII kebab-case from the title, 2 to 6 words.
- New tickets start from `TEMPLATE.md` in this skill's folder; copy it verbatim and fill it in.
- Dates are absolute ISO (`2026-09-21`). Set `updated` on every edit. Keep the frontmatter valid YAML.

## Namespaces

Two independent ID sequences share one backlog. `DFS-NNN` is for tickets that build the duplicate-file-scanner product; `CCW-NNN` ("Claude Code Workflow") is for tickets that build or change the harness itself — skills, agents, archetypes, the `tickets`/`learn` machinery, the story pipeline. `CCW-NNN` and `DFS-NNN` folders sit side by side, unchanged, directly under `docs/tickets/{backlog,completed}/`; there is no separate subtree per namespace.

A ticket's namespace is decided by what its Story is about, not by `touches` alone: `touches` can't decide a `CLAUDE.md` edit on its own, since that file holds both product design decisions and process rules. `new` infers the namespace from the title and conversation context, and asks explicitly only when it cannot decide.

Each namespace numbers itself independently: the next ID for a prefix is the highest number of that prefix found across both `backlog/` and `completed/`, plus one. An ID already used in either sequence — including one whose ticket was later relabeled into the other namespace — is never reused, so a relabel leaves a permanent gap in its old sequence rather than freeing the number.

`depends_on` may name an ID from either namespace; the dependency graph, the cycle check, and the "not all dependencies done" gate treat an ID the same way regardless of its prefix. `validate` notes a cross-namespace link (informational only, not a finding), since it's legitimate but worth a human glance.

## Frontmatter

| Field | Values |
|---|---|
| `id` | `DFS-NNN` or `CCW-NNN` (see Namespaces) |
| `title` | short imperative title |
| `status` | `draft`, `ready`, `in-progress`, `in-review`, `blocked`, `done`, `cancelled` |
| `type` | `feature`, `fix`, `refactor`, `test`, `build`, `docs`, `chore`, `spike` |
| `scope` | list of `file-scanner`, `cli`, `build`, `docs` |
| `archetype` | a directory name under `.claude/archetypes/` (the template set the story pipeline uses), or `none`: implemented by hand, not run through the pipeline. Blank in drafts, required from `ready` on |
| `priority` | `high`, `medium`, `low` |
| `lane` | `backend`, `frontend` or `harness`: the kind of work, which decides the session that owns the ticket (see "Sessions and lanes"). Every `CCW` ticket is `harness`. Required on every non-terminal ticket; completed tickets are not checked (CCW-018) |
| `depends_on` | list of ticket IDs; may cross namespaces (see Namespaces) |
| `touches` | packages or paths this ticket edits (used to judge parallel safety) |
| `models` | optional per-agent overrides, e.g. `models: {architect: {model: opus, effort: high, maxTurns: 25}}`. Agents: `architect`, `test-author`, `implementer`, `verifier`, `retro`. Fields: any subset of `model`, `effort`, `maxTurns`. Resolved in this order: the ticket, then the archetype's `models`, then the agent's own definition |
| `gates` | list of pipeline phase ids after which `/story run` stops for the user, or the reserved `done` (after the last phase), e.g. `gates: [done]` or `gates: [design, done]`. Decided in `refine` for every pipeline ticket and required from `ready` on when `archetype` is not `none`; `/story run` uses it and falls back to the archetype's `gates:` only when the ticket has none (CCW-017) |
| `autorun` | `true` or `false`; absent means `false`. `true` authorizes Claude to start `/story run` for this ticket without the user typing it, through the launch points in "Autonomous launch" below, and never `/story approve`. Only for pipeline tickets: `validate` reports it on an `archetype: none` ticket (CCW-017) |
| `blocked_reason` | required when `blocked`, empty otherwise |
| `created`, `updated`, `completed` | dates; `completed` is set on `done` or `cancelled` |

## Lifecycle

`draft` -> `ready` -> `in-progress` -> `in-review` -> `done`. `in-review` may return to `in-progress`. `blocked` and `cancelled` are reachable from any non-terminal state.

`depends_on` is the structural link between tickets. `blocked` is for blockers outside that graph (waiting on a decision, something external) and needs a `blocked_reason`. A `ready` ticket whose dependencies are not all `done` is simply not dispatchable.

### The `ready` gate

A ticket may be set to `ready` only when all of these hold, and only after the user confirms:

- Story filled in (As a / I want / So that).
- Archetype set: a name, or `none`.
- Lane set: `backend`, `frontend` or `harness` (`harness` for a `CCW` ticket).
- Gates set, when the archetype is not `none`: the ticket's own `gates:` list, decided with the user (name "gates set" when refusing).
- Acceptance criteria written, every scenario tagged with at least one `@AC-n`, and every live (not retired) criterion covered by at least one scenario. Name each unmet item ("archetype set", "every scenario tagged", "every criterion covered") when refusing.
- Scenarios are concrete (specific values, observable outcomes) and cover the happy path, the edge cases, and every safety invariant in `CLAUDE.md` that applies (never delete the last copy, re-verify at execution time, preview is a pure query, hard links are not duplicates, no symlink or junction following, empty files excluded).
- Consistent with the settled design decisions in `CLAUDE.md`; any conflict is resolved or raised.
- `depends_on` lists real ticket IDs, with no cycles.
- `touches` and "Out of scope" are filled in. The frontmatter `touches` lists every path or package the Scope section's Touches list names, and is never empty on a ticket whose archetype is not `none`: the story pipeline's `touches` check reads only the frontmatter (DFS-026.L-1). Each `touches` entry is a bare path or package with nothing after it: a note on what the entry is for goes in the Scope section, since the `touches` check reads an entry containing `/` as a literal path prefix (DFS-027.L-5).
- Definition of done includes the standard checks plus anything specific to the ticket.
- Open questions read "None."

### Acceptance criteria

A `## Acceptance criteria` section sits between Context and Scenarios. Its first line states the full ID: "The full ID of each criterion is `<TICKET-ID>.AC-<n>`." Each criterion is one testable statement on one line, `- **AC-<n>** <statement>`, numbered from 1.

- Scenarios carry `@AC-<n>` tags on the line above `Scenario:` (several tags are allowed). Every scenario needs at least one tag, and every live criterion needs at least one scenario.
- Tests start their `@DisplayName` with the full ID, e.g. `DFS-001.AC-3 Files of equal size form one candidate group`; a Vitest test starts the name given to `it` with it, inside any `describe` (CCW-020). The pipeline's gates trace criterion, scenario and test by ID.
- Every criterion must be observable by a test that can carry its ID. A criterion about removing or retiring something (a test, a stub, a behavior) is stated as the behavior the removal enables — "`fullHash` returns a digest instead of throwing", not "no test asserts that `fullHash` throws" — since no test can carry the ID of its own absence (DFS-003.L-3).
- In a pipeline ticket (archetype not `none`), every live criterion gets a story test, because the traceability gate needs a test name starting with its ID. Do not mark a live criterion "read", "checked by reading" or "covered by OpenApiSpecTest". A check that can only be done by reading (documentation wording, a by-hand run) becomes a Definition of done item, which the verifier ticks, not a criterion. A spec criterion is stated as behavior a test can observe by calling the endpoint (DFS-047.L-1).
- Numbers are never renumbered or reused. To drop a criterion, retire it: `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. A retired criterion needs no scenario or test, and a tag that names it is an error.
- Never delete a criterion. A new one takes the number one above the highest used, retired included. Deleting the last criterion and reusing its number cannot be detected in the file (only git history shows it), so do not.

## Branches and worktrees

A ticket worked outside `main` lives on one branch, `tickets/<ID>-<slug>` (the slug is the ticket folder's), e.g. `tickets/CCW-008-worktree-aware-tickets-lifecycle`. `/story run` uses the same branch name. If the ticket also has a worktree, it sits next to the repo checkout as `../<repo-dir>-<ID>-<slug>`, e.g. `../duplicate-file-scanner-CCW-008-worktree-aware-tickets-lifecycle`. Find either one with `git worktree list --porcelain`: look for the line `branch refs/heads/tickets/<ID>-<slug>`, and never guess the path. A ticket worked directly on `main` has neither.

**Parallel work.** To run several tickets at once, keep the repo checkout on `main` and give every ticket its own worktree (`start --worktree`). Each session then works in its own tree, and `done` merges into the `main` checkout. If nothing has `main` checked out (for example, a checkout was switched to a ticket branch by hand), `done` still works: it opens a temporary close tree for `main` (step 1 of `done`), so no session ever has to switch branches to close a ticket.

## Autonomous launch (CCW-017)

A pipeline ticket with `autorun: true` runs from the user's `ready` confirmation to the product-owner review without the user typing `/story run`. Claude may start `/story run <ID>` on its own only at these three launch points, never `/story approve`, and only for a ticket in one of this session's own lanes (see "Sessions and lanes"; the coordinator owns no lane, so it never launches and hands an autorun ticket it makes ready to the owner instead, see "The coordinator"):

1. **`refine`, right after the ready commit** (see `refine`), when every `depends_on` is `done`.
2. **`done <X>`, right after its close commit** (see `done`, step 4a), for a ticket that X's close made dispatchable.
3. **An unblock message from another session** (CCW-018; see "Sessions and lanes", "Receiving an unblock"), for a ticket the message names.

**Launch check.** Before any launch, refuse, naming the reason and asking the user to re-confirm `ready` (which records a fresh approval commit), when:

- the ticket file has uncommitted changes in the repo checkout (`git status --porcelain -- <ticket file>` prints anything); or
- the ticket file differs from the commit that approved it. The approving commit is `refine`'s ready commit: the newest commit on `main` touching the file with the subject `docs: refine <ID> [<ID>]` (`git log -1 --format=%H -F --grep="docs: refine <ID> [<ID>]" -- <ticket file>`), whose version of the file has both `status: ready` and `autorun: true` (`git show <commit>:<ticket file>`). Then `git diff --quiet <that commit> -- <ticket file>` must succeed. No such commit, or a version without those two values, also refuses. Do not look for "the newest version with `ready` and `autorun: true`" instead: a later edit keeps both values, so it would pass as an approval (found by CCW-017's own mechanical run).

A refused launch sends the same notification as any other stop (the `story` skill's "Notifications"). A launch goes through the `story` skill (`Skill` tool, `story`, args `run <ID>`), invoked from the repo checkout, exactly as if the user had typed it.

## Sessions and lanes (CCW-018)

Several Claude Code sessions may work the backlog at once, all from the same repo checkout on `main`. Each ticket's `lane` says what kind of work it is; each session owns one or more lanes, and it launches, lists as dispatchable and refines only tickets in its own lanes. The one exception is the coordinator (CCW-021, below), which owns no lane and writes tickets in all of them.

**The lane map.** `docs/tickets/lanes.md` maps session names to the lanes they own, one line per session in a fenced block, `<name>: <lane>[, <lane>...]`, or `<name>: coordinator` for the coordinator. A lane is owned by at most one listed session; `coordinator` is a reserved word, not a lane: at most one line holds it, and never together with lanes. Moving a lane (say, harness work) or the coordinator role to another session is a one-line edit there, committed alone on `main`; no ticket changes.

**This session's lanes.** When a mode needs them (`list`, `new`, `refine`, `start`, `done`, `cancel`, an unblock message), take this session's name from `ListAgents` (its first line, "This session is <name>") and look it up in `lanes.md`. A session whose line reads `coordinator` is the coordinator and owns no lane. If the name is not listed, ask the user which lanes this session owns, offer to add the line (committed alone on `main` as `docs: map <name> to <lanes>`), and use the answer for the rest of the conversation whether or not the line is added. Do not store lanes in memory: every session in this project shares it. Session names are set at launch (`claude -n <name>`) or in a running session (`/rename <name>`).

**Another lane's owner.** The session listed for that lane in `lanes.md`, if `ListAgents` shows a peer with exactly that name. Otherwise the lane has no running owner: say so where a message would have gone, and leave the work for that session's next `list`.

**Messages.** `SendMessage` to the owner, sent only at these three moments and the coordinator's ready hand-off below, and no others:

1. **A request filed** for another lane (`new`, request form): `<ID> (<lane> draft) for you: <title>, needed by <requesting ID>; committed in <sha>`.
2. **An unblock** of another lane's `ready` + `autorun` tickets (`done`, step 4a), one message per owning session: `<X> done in <close sha>: unblocks <ID> (<lane>, autorun)[, <ID> ...]; run the launch check and start the lowest`.
3. **A harness change landed:** right after a `harness` ticket's branch is merged into `main`, by `done` step 2 or by hand ahead of it, the merging session messages every other running session listed in `lanes.md`: `<ID> merged in <sha>: changed <skill or agent files>; reread before your next /tickets or /story`. If `done` finds the branch already landed, the message went out at that merge and is not repeated.

**The ready hand-off** (CCW-021), sent by the coordinator only, right after its `ready` commit of a ticket in another session's lane (`refine`; see "The coordinator"): message 2's form, `<ID> ready in <sha>: unblocks <ID> (<lane>, autorun); run the launch check and start the lowest`, for an `autorun` ticket whose `depends_on` are all `done`; otherwise message 1's form, `<ID> (<lane> ready) for you: <title>; committed in <sha>`.

No session messages the coordinator: it reads the backlog's state itself (its catch-up, below), so nothing is reported to it and nothing wakes it.

Every message is one line (or a self-contained first line), names ticket IDs and commits, and carries no ticket content: the receiver reads the ticket from `main`. Edits to shared files (`CLAUDE.md`, `settings.gradle.kts`, wiring) send no message; staging by name and reading `main` cover them, and every message may cost the user an approval click in a session running in another permission mode.

**Receiving an unblock.** For each ticket the message names: ignore it unless its `lane` is one of this session's lanes; otherwise run the launch check ("Autonomous launch") on it in `main`'s tree. Start `/story run` for the lowest ID that passes (launch point 3), and list the others with their commands and any refusal reasons. The message only says when to look: a ticket without its own `autorun` approval commit is refused like any other.

**Requests.** When a ticket in this session's lanes needs work in a lane it does not own, file a request with `new` (request form) instead of doing the work or asking in a message. The owner refines it like any draft.

### The coordinator (CCW-021)

One session may hold the role `coordinator` in `lanes.md` instead of lanes. It keeps every lane's queue full and knows the whole backlog's state, without other sessions reporting to it.

- **Writes tickets in every lane.** It may run `new` (drafts and requests) and `refine` for a ticket in any lane, including the `ready` confirmation and its `docs: refine <ID> [<ID>]` commit, and record decisions. After a `ready` commit in another session's lane it sends that owner the ready hand-off ("Messages"): an `autorun` ticket whose dependencies are all `done` is handed over as an unblock, which the owner handles under "Receiving an unblock" (launch point 3); a ticket with a dependency still open needs only message 1's form, since the owner's `done` on the dependency launches it as before.
- **Never starts, launches or closes.** It never runs `start`, `/story run`, an autonomous launch, `done` or `cancel`, in any lane: those create, merge or remove the owner's branches and worktrees. Asked to, it refuses, naming the lane's owner from `lanes.md` and the command to run there.
- **Commits its own edits at once.** Its edits to shared files on `main` (drafts, refinements, design docs, `lanes.md`) are committed in the turn it makes them, staged by name, never held for a later step: an uncommitted edit in `main`'s checkout blocks another session's merge. It may commit another session's uncommitted draft on `main` only after telling that session in the same turn.
- **Catches up from git, not from messages.** At every session start (startup, resume, `/clear`, compact) a SessionStart hook in `.claude/settings.json` runs `tools/story-gate/story-gate catch-up --hook`. It finds the session's name in the hook input's `session_title` (set by `claude -n <name>` or `/rename`), or else in the environment variable `DFS_SESSION` set at launch, and prints only when that name maps to `coordinator` in `lanes.md`; any other session sees nothing. It prints, in a few lines and without ticket content: every worktree with its ticket ID, branch and `plan.json` state (a human gate shows as `awaiting-approval` with the gate, a blocked phase as `blocked` with the phase); every uncommitted ticket or `plan.json` file, labelled `main checkout` or `<ID> worktree`; and the advisory proposals waiting for the coordinator (`advisory/proposals/ADV-NNN-rN.md` with no response in `advisory/responses/`, or a `received` one; an older revision flagged `superseded by rM`; an invalid response file reported by path). It keeps no state between runs. The coordinator then writes advisory responses by hand per `advisory/README.md`.
- **Mid-session, it runs `/tickets list`** when it needs fresh state; its `list` runs the same script (see `list`). A gate or block waiting for the user already reaches them through the `story` skill's push notification; the coordinator sees it at its next start or `list`. A refused autonomous launch shows in its `list` as a `ready` `autorun` ticket with every dependency `done` and no branch, under "Dispatchable now".

## Modes

**new `<title>`**: infer the namespace (`DFS` or `CCW`, see Namespaces) from the title and conversation context, asking explicitly only when it cannot decide; allocate the next ID from that prefix's own sequence and a slug, create `backlog/{ID}-{slug}/{ID}-ticket.md` from the template with `status: draft`, and fill in whatever the conversation already settles. Do not invent scenarios; leave placeholders and list gaps under Open questions. Set `lane` (`harness` for a `CCW` ticket; otherwise from the Story, asking only when it cannot be decided). Report the path. In the coordinator, a draft in any lane is created this way and committed alone on `main` in the same turn, staged by name, as `docs: draft <ID> [<ID>]` ("The coordinator").

**new `<title>` `--for <lane> --by <requesting ID>`** (request form, CCW-018): a request for work in a lane this session does not own. Create the ticket as above, as a `draft` with that `lane`, a Context naming the requesting ticket, the need and the design reference, and suggested scenarios under Open questions (the owner writes the real ones). Commit it alone on `main`, staged by name, as `docs: request <ID> for <lane> [<ID>, <requesting ID>]`. Add `<ID>` to the requesting ticket's `depends_on` (in its worktree if it has one, otherwise on `main`, committed with its next change). Then send the owning session message 1 ("Sessions and lanes"), or say that the lane has no running owner. A request needs no confirmation beyond the user's ask, the same narrow commit exception as `refine`'s.

**refine `<ID>`**: read the ticket and `CLAUDE.md`, then interview the user to close the gaps against the `ready` gate. Ask in plain terminal text, never with the AskUserQuestion popup, one decision at a time: a short summary of the decision, numbered options each with trade-offs, and a recommendation with its reason. The user answers with a number or keeps discussing. Update the ticket after each answer. If the archetype is blank, make it a decision (a name, or `none`). If the archetype is not `none`, read its `archetype.md` and turn each unresolved edge-case checklist item into a question. Each time a decision closes, add or update an acceptance criterion (next number, see "Acceptance criteria") and tag the scenarios that cover it. State any count (scenarios, criteria, files) only from a mechanical count such as `grep -c`, never from memory or an earlier note. When a ticket changes an output format (JSONL lines, a report layout), search the test trees for tests that assert that format (line types, field names, literal lines), not only for callers of changed constructors, and name them in the ticket (DFS-030.L-2). For a ticket that edits instructions (a skill or an agent prompt), record for each scenario whether it will be run mechanically or only checked by reading, and do not present a read-through as a walkthrough of every mode. When such a ticket changes a count, a name or a list (for example "two launch points"), search `.claude/`, `CLAUDE.md` and `docs/design/` for every other mention of the old form, including spelled-out words such as "two", "both" and "either", and put each file with a hit in `touches` (CCW-018.L-1). Before asking for `ready`, make sure the scenarios test the ticket's own purpose (its "So that") against the real state of the repository and environment it will run in (for example, which branches are checked out where, or which tickets are in flight), not only against a clean setup. When the Definition of done needs a live run across components that are already built, name the assumptions between them that the run must exercise: shared names, who performs a step, and which status a downstream check expects. When a scenario or done-item depends on how an already-built component behaves (an agent's tools, which file runs a kind of test case, what a check reads), read that component's real definition rather than an earlier sketch or assumption, and either add the file that would have to change to `touches` or record the workaround as a decision in the ticket, so it is not discovered during implementation (CCW-005.L-5). When a ticket asserts an existing adapter's observable output (an exit code, an HTTP status) and says "no source change", check the value against that adapter's constants (for example `ScanCommand`'s ERROR and USAGE) before writing the criterion, and name the constant in the ticket (DFS-052.L-4). When a decision places files outside a directory a test controls (a volume root, the user's home folder, a fixed system path), the ticket states the seam tests use instead, so every test stays inside a `@TempDir` (DFS-051.L-1). Before asking for `ready`, count the ticket's decisions and acceptance criteria mechanically; with more than 8 decisions or 15 criteria, propose splitting it at a natural seam (for example the core use case in one ticket, its job wrapper and wiring in another) and, if the user agrees, file the second part with `new` (DFS-051.L-2). Do not ask for `ready` while a review of the ticket is still pending (a product review or another session's answer the ticket or the user says to wait for): fold its outcome in first, so no amendment lands while the architect is running (DFS-051.L-3). For a ticket whose archetype is not `none`, make its gates a decision like any other (CCW-017): the options are `[done]` (runs unattended to the product-owner review), `[design, done]` (also pauses after the architect) and adding a gate after another phase; recommend keeping the design gate for any ticket that plans or executes deletion (CLAUDE.md's safety invariants), and write the answer to the ticket's `gates:`. When the gate is satisfied, show the user the result and ask for confirmation before setting `ready`. For a pipeline ticket, the confirmation also asks whether to set `autorun: true` (see "Autonomous launch"), stating what it means: the run starts now if every dependency is done, or when the last one closes, and stops only at the gates or `blocked`. On confirmation:

- Set `status: ready` (and `autorun`, as answered), then commit the ticket file alone on `main`, staged by name, as `docs: refine <ID> [<ID>]`; confirm with `git status` that nothing else is staged. The story worktree branches from `main`, so an uncommitted ready ticket would not reach it. This commit is part of the confirmation, the same narrow exception as `done`'s close commit.
- With `autorun: true` and every `depends_on` `done`: run the launch check, then start `/story run <ID>` ("Autonomous launch", launch point 1).
- With `autorun: true` and a dependency not `done`: stop after the commit, naming the dependency the ticket waits on; `done` on that dependency launches it.
- Without `autorun`: stop after the commit, naming `/story run <ID>` (pipeline) or `/tickets start <ID> --worktree` (`none`) as the next command.
- In the coordinator, which may refine in any lane but never launches: after the commit, send the lane's owner the ready hand-off ("Sessions and lanes", "Messages") in place of the three bullets above, or say that the lane has no running owner; then stop, naming the command the owner runs.

**list `[status|scope]`**: read the frontmatter of every ticket in both folders and print one table (ID, title, status, lane, scope, archetype, priority, depends_on) covering both the `DFS` and `CCW` namespaces together, not a separate table per namespace. Rows in this session's lanes come first (see "Sessions and lanes"); the rest follow under an "Other lanes" heading. Hide `completed/` unless asked. Then print "Dispatchable now", for this session's lanes only: `ready` tickets whose `depends_on` are all `done`, grouped into sets whose `touches` do not overlap, so each set can safely run in parallel. Mark `archetype: none` tickets "manual" there: the story pipeline refuses them, so they are dispatched by hand. A ticket whose branch `tickets/<ID>-<slug>` already exists has been started by some session, even though `main`'s copy may still say `ready` (`start` edits inside the ticket's worktree and never commits). Leave it out of "Dispatchable now" and list it under "Started elsewhere", with its worktree's path from `git worktree list --porcelain`, or "branch only" when it has none. "Started elsewhere" also covers only this session's lanes. A dispatchable `autorun` ticket that waited for this session (its unblock found no running owner) appears in "Dispatchable now" like any other; offer the launch check and `/story run` for it.

In the coordinator (CCW-021), which owns no lane, `list` shows every lane: the table grouped by lane (`backend`, `frontend`, `harness`), and "Dispatchable now" and "Started elsewhere" for each lane, with no "Other lanes" heading and no launch offered (it names each ticket's owner instead). It then runs `tools/story-gate/story-gate catch-up --session <this session's name>` from the repo checkout and shows its output under the table: worktrees with their `plan.json` states, uncommitted ticket and `plan.json` files, and pending advice, which the frontmatter on `main` does not show. In any other session `list` does not run the script.

**start `<ID>` `[--worktree]`**: require `ready` (or `in-progress` when resuming) and all dependencies `done`; otherwise refuse and say why. The coordinator refuses `start`, `done` and `cancel` for every ticket, naming the lane's owner ("The coordinator"). Then choose the tree to edit in:

- **A worktree already holds the ticket's branch:** enter that worktree. Make the `in-progress` edit to the ticket file inside it, not in the tree `start` was invoked from, and report the worktree's path as the place to work. Never create a second worktree.
- **`--worktree` was given (or the user asked for a worktree) and none exists:** run `git worktree add -b tickets/<ID>-<slug> ../<repo-dir>-<ID>-<slug> main`. Drop `-b` and pass the branch name if the branch already exists. Then make the edit inside the new worktree, as above. If git refuses (for example, the branch is checked out in another tree), stop and report git's message.
- **Otherwise:** set `in-progress` in place, in whatever tree and branch `start` was invoked from, exactly as a ticket worked directly on `main` has always done.

`start` never commits. The status edit stays uncommitted in the tree it was made in.

**review `<ID>`**: implementation finished and awaiting review. Set `in-review` in whatever tree and branch `review` is invoked in; it never creates, enters or removes a worktree. Confirm the Definition of done items are ticked.

**block `<ID>` `<reason>`** / **unblock `<ID>`**: set `blocked` with `blocked_reason`; on unblock clear the reason and return to `ready` or `in-progress` depending on whether work has begun (ask if unclear).

**done `<ID>`**: not in the coordinator (see `start`). Require every Definition of done item ticked (or the user's explicit say-so). Then work through the steps below in order, and stop where a step says so. When no `tickets/<ID>-<slug>` branch exists, skip steps 1, 2, 5 and 6: `done` closes in place, in whatever tree it was invoked from, as it always has.

1. **Find `main`'s tree.** The close happens on `main`. Find the tree that has `main` checked out (`git worktree list --porcelain`, line `branch refs/heads/main`) and run every git command below against it with `git -C <that tree>`, even when `done` was invoked from inside the ticket's worktree. Never update `main`'s ref from outside that tree: a checkout whose branch moves underneath it looks as if it had reverted the change.
   - **No tree has `main`:** open a temporary close tree, `git worktree add ../<repo-dir>-close-<ID> main`, and use it as `main`'s tree for the steps below. It is removed at the end (step 6), whatever happens in between, except on a merge conflict (step 2), where it is left for the user to resolve in.
   - **A leftover close tree:** if the tree holding `main` is `../<repo-dir>-close-<ID>` (an earlier `done` or `cancel` for this ticket stopped on a conflict or was interrupted), reuse it, and remove it at step 6 like a fresh one. If it still has an unresolved merge or other uncommitted changes, stop and tell the user to finish or abort that merge there first.
2. **Has the branch landed? If not, merge it.** Do this before anything else, and change nothing until it is settled.
   - Ancestor check: `git merge-base --is-ancestor tickets/<ID>-<slug> main`. If it passes, the branch has landed (fast-forward or merge). Go to step 3.
   - Fallback, for squash merges: `git log --oneline -F --grep="[<ID>]" tickets/<ID>-<slug>..main`. The range keeps out ticket commits made on `main` before the branch forked, such as the refinement commits. If any commit matches, the branch has landed. Go to step 3.
   - Neither passed: say so, then merge the branch into `main` (`git -C <main tree> merge tickets/<ID>-<slug>`, which fast-forwards when it can and otherwise makes an ordinary merge commit). On a conflict, stop and leave the merge unresolved. Do not close or clean up anything. Tell the user to resolve the conflict by hand in `main`'s tree (name its path, which may be the close tree), commit it and re-run `done`. If `main`'s tree is a shared checkout and git refuses the merge because of another session's uncommitted changes there, stop in the same way and report git's message. Never force a resolution, never rebase the branch, and never pass `--ff-only`, `--squash` or `-X` strategy options.
   - The only commits `done` ever makes on `main` are this merge and step 4's close commit.
   - If this step merged a `harness` ticket, send message 3 ("Sessions and lanes") right after the merge. If the branch had already landed, the merging session sent it then.
3. **Close the ticket.** In `main`'s tree, set `status: done`, `completed` and `updated`, and move the folder to `completed/` (`git mv` when tracked, a plain move otherwise), so the status change and the move land together.
4. **Commit the close.** Stage only that one ticket file by name, then re-`git add` it: `git mv` can silently leave the index holding the file's *pre-edit* content when the frontmatter was edited just before the move (L-020). Confirm with `git diff --cached` that the staged version matches the working-tree file, and with `git status` that nothing else is staged. Commit as `docs: close <ID> [<ID>]`, with no separate yes/no prompt. Running `/tickets done` is itself the request. This is a deliberate, narrow exception to "commits are made only when the user asks" (`CLAUDE.md`), bounded to this one status-and-move change, the same kind of exception the `story` skill documents for its per-phase commits.
4a. **Launch what the close unblocked** (CCW-017, CCW-018). Find every ticket in `main`'s tree with `status: ready`, `autorun: true` and an archetype other than `none`, whose `depends_on` includes `<ID>` and are now all `done`, and whose branch `tickets/<its ID>-<slug>` does not exist (`git branch --list`; an existing branch means some session started it). Split them by `lane`. For each lane this session does not own, launch nothing: send that lane's owner message 2 ("Sessions and lanes"), one message per session naming every such ticket, its lane and the close commit; if the lane has no running owner, say which tickets wait and for which lane. Then, among the tickets in this session's own lanes: if none, go on. Otherwise take the lowest ID, run the launch check ("Autonomous launch") and start `/story run` for it after the cleanup in steps 5 and 6, and list the others, each with the command `/story run <its ID>`. Say which ticket was launched before the cleanup question, so the user knows the session will keep going.
5. **Clean up the worktree and branch.** Show what will be removed (the worktree's path, if any, and the branch) and ask **once** for confirmation. If the user declines, leave both and say so; the close commit stands.
   - **Dirty worktree:** if `git -C <worktree> status --porcelain` prints anything, refuse the whole cleanup (worktree and branch), list the changes, and leave both in place. The close from steps 3 and 4 still stands.
   - **Worktree:** remove it with `git -c core.longpaths=true worktree remove <path>`, never `--force` (`core.longpaths` lets git delete test-report paths over 260 characters on Windows, DFS-036.L-1). If it fails, check `git worktree list --porcelain`:
     - **Still listed** (on Windows, often because the session or an editor still has the directory open): report git's message and the command to re-run, and do not delete the branch, which git would refuse anyway while it is checked out.
     - **No longer listed** but the folder remains (git unregistered it, then failed to delete a file): the folder holds only build output, since the dirty check above passed. Remove it from Git Bash with `rm -rf <path>`, then `git worktree prune`, and go on to the branch. If a file is busy (a server started for the product-owner review still runs from the folder), name the process holding it and ask the user to stop it, then retry; the branch can be deleted meanwhile.
   - **Branch:** delete it with `git -C <main tree> branch -d tickets/<ID>-<slug>`, never `-D`. If git refuses because the branch has commits not reachable from `main` (as it does after a squash merge), report the refusal and leave the branch. Deleting it for good (`git branch -D`) is the user's call.
   - Never touch other refs. Rebase backup refs such as `refs/original/*` stay, since they are the user's recovery path.
6. **Remove the close tree**, if step 1 opened or reused one: `git worktree remove ../<repo-dir>-close-<ID>` (never `--force`; it is clean by now, since the only commits made in it are the merge and the close). This needs no confirmation: `done` created it, and nothing but `main`'s own commits ever lived in it.

Before closing, ask whether any friction met on the ticket is worth recording in its own `learnings.md` (CCW-010: learnings live per ticket, as `<ID>.L-<n>`); if so, name the command `/learn add <short description>` for the user to run (the `learn` skill is the only writer of those entries, and its `add` checks for recurrences across every ticket). Never apply a logged proposal without the user's approval. The last line of the reply is the offer `Draft log entries from this ticket with: /learn reflect <ID>` (the real ID filled in). Launch nothing yourself: `learn` is run only by the user.

**cancel `<ID>` `<reason>`**: same as `done` with `status: cancelled`, plus a `## Resolution` section at the end recording the reason. The one exception is step 2: `cancel` never merges the branch, because cancelled work must not land on `main`. It runs the landing check only to report the result. Step 5's `git branch -d` then refuses an unmerged branch as usual, and that refusal is reported. Discarding the branch with `-D` is left to the user.

**validate**: report, without changing anything, every problem found: folder and `status` disagree; missing or malformed frontmatter fields; missing template sections; `blocked` without a reason; `depends_on` pointing at a missing ticket or forming a cycle; a `ready` or `in-progress` ticket with dependencies not `done`; two `ready` tickets whose `touches` overlap and are not linked by `depends_on`; duplicate IDs; slug or ID in the folder name not matching the file. Also:
- **Archetype:** a name other than `none` with no directory `.claude/archetypes/<name>/` (name the ticket and the archetype). `none` needs no directory.
- **Models:** an agent outside architect, test-author, implementer, verifier, retro, or a field other than `model`, `effort`, `maxTurns` (name the ticket and the key, and list the valid ones).
- **Gates:** an entry that is neither `done` nor a phase id of the ticket's archetype (`design`, `test`/`tests`, `implement`, `verify`); a ready-or-later ticket whose archetype is not `none` and that has no `gates:` (name the ticket).
- **Autorun:** a value other than `true` or `false`; `autorun: true` on an `archetype: none` ticket (name the ticket and the field).
- **Lane:** on a non-terminal ticket (not `done` or `cancelled`), a missing `lane` or a value other than `backend`, `frontend`, `harness`; a `CCW` ticket whose lane is not `harness` (name the ticket and the field).
- **Lane map:** in `docs/tickets/lanes.md`, a lane owned by two names, a lane value outside the three, or a line not of the form `<name>: <lane>[, <lane>...]` or `<name>: coordinator` (name the line); a second line holding `coordinator`, or `coordinator` mixed with lanes on one line (name the line, CCW-021).
- **Criteria:** duplicate numbers; a gap in the numbering 1 to n (a criterion was deleted, not retired); a scenario tag naming a missing or retired criterion. Name the ticket and the ID.
- **Ready-or-later tickets** (`ready`, `in-progress`, `in-review`; drafts, blocked, done and cancelled are not checked): no archetype, no criteria, a scenario without a tag, or a live criterion no scenario covers. Name the ticket and what is missing.
- **Touches (informational note, not a finding):** an entry that resolves to nothing. An entry is a path if it contains `/` or exists at the repository root; otherwise a lowercase dotted name is a Java package, `<module>/src/{main,test}/java/<package as path>`. Entries for files a ticket will create resolve to nothing until the work is done.
- **Namespace boundary (informational note, not a finding):** a `CCW` ticket whose `touches` includes a `file-scanner/` or `cli/` path, or a `DFS` ticket whose `touches` is confined entirely to `.claude/`, `docs/design/` or `docs/learnings/` paths — a hint the ticket may be filed under the wrong prefix (see Namespaces), not an error.
- **Cross-namespace dependency (informational note, not a finding):** a `depends_on` entry whose ID is in the other namespace from the ticket itself. Legitimate; noted only so it isn't missed.

Offer to fix mechanical issues.

## Implementing a ticket (for agents)

- Read the ticket and `CLAUDE.md`; do not require anything else.
- Only implement tickets in `ready` or `in-progress`, and run `start` first.
- Stay within `touches`. If the work needs to go beyond it, stop and report instead of editing other files.
- Done-items are never self-graded on a pipeline ticket (an archetype other than `none`): the implementer leaves them unticked, and the verifier ticks each one it has verified. On an `archetype: none` ticket, implemented by hand, the implementing session ticks each item as it is met and notes the evidence beside it. Either way, run `review` when all are ticked.
- A hand-implemented ticket ends like a pipeline one (CCW-017): before `review`, the implementing session writes `verification.md` in the ticket's folder, opening with the `## Product owner review` section defined in `.claude/agents/verifier.md`, and shows that section to the user as the review.
- An instruction edit that changes a count, a name or a list repeats that search before `review`, and records the search and its hits in `verification.md` (CCW-018.L-1).
- Do not edit other tickets. Commits follow the conventions in `CLAUDE.md`, and only when the user asks.
