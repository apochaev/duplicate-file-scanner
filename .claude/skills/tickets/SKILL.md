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
| `depends_on` | list of ticket IDs; may cross namespaces (see Namespaces) |
| `touches` | packages or paths this ticket edits (used to judge parallel safety) |
| `models` | optional per-agent overrides, e.g. `models: {architect: {model: opus, effort: high, maxTurns: 25}}`. Agents: `architect`, `test-author`, `implementer`, `verifier`, `retro`. Fields: any subset of `model`, `effort`, `maxTurns`. Resolved in this order: the ticket, then the archetype's `models`, then the agent's own definition |
| `blocked_reason` | required when `blocked`, empty otherwise |
| `created`, `updated`, `completed` | dates; `completed` is set on `done` or `cancelled` |

## Lifecycle

`draft` -> `ready` -> `in-progress` -> `in-review` -> `done`. `in-review` may return to `in-progress`. `blocked` and `cancelled` are reachable from any non-terminal state.

`depends_on` is the structural link between tickets. `blocked` is for blockers outside that graph (waiting on a decision, something external) and needs a `blocked_reason`. A `ready` ticket whose dependencies are not all `done` is simply not dispatchable.

### The `ready` gate

A ticket may be set to `ready` only when all of these hold, and only after the user confirms:

- Story filled in (As a / I want / So that).
- Archetype set: a name, or `none`.
- Acceptance criteria written, every scenario tagged with at least one `@AC-n`, and every live (not retired) criterion covered by at least one scenario. Name each unmet item ("archetype set", "every scenario tagged", "every criterion covered") when refusing.
- Scenarios are concrete (specific values, observable outcomes) and cover the happy path, the edge cases, and every safety invariant in `CLAUDE.md` that applies (never delete the last copy, re-verify at execution time, preview is a pure query, hard links are not duplicates, no symlink or junction following, empty files excluded).
- Consistent with the settled design decisions in `CLAUDE.md`; any conflict is resolved or raised.
- `depends_on` lists real ticket IDs, with no cycles.
- `touches` and "Out of scope" are filled in.
- Definition of done includes the standard checks plus anything specific to the ticket.
- Open questions read "None."

### Acceptance criteria

A `## Acceptance criteria` section sits between Context and Scenarios. Its first line states the full ID: "The full ID of each criterion is `<TICKET-ID>.AC-<n>`." Each criterion is one testable statement on one line, `- **AC-<n>** <statement>`, numbered from 1.

- Scenarios carry `@AC-<n>` tags on the line above `Scenario:` (several tags are allowed). Every scenario needs at least one tag, and every live criterion needs at least one scenario.
- Tests start their `@DisplayName` with the full ID, e.g. `DFS-001.AC-3 Files of equal size form one candidate group`. The pipeline's gates trace criterion, scenario and test by ID.
- Numbers are never renumbered or reused. To drop a criterion, retire it: `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. A retired criterion needs no scenario or test, and a tag that names it is an error.
- Never delete a criterion. A new one takes the number one above the highest used, retired included. Deleting the last criterion and reusing its number cannot be detected in the file (only git history shows it), so do not.

## Branches and worktrees

A ticket worked outside `main` lives on one branch, `tickets/<ID>-<slug>` (the slug is the ticket folder's), e.g. `tickets/CCW-008-worktree-aware-tickets-lifecycle`. `/story run` uses the same branch name. If the ticket also has a worktree, it sits next to the repo checkout as `../<repo-dir>-<ID>-<slug>`, e.g. `../duplicate-file-scanner-CCW-008-worktree-aware-tickets-lifecycle`. Find either one with `git worktree list --porcelain`: look for the line `branch refs/heads/tickets/<ID>-<slug>`, and never guess the path. A ticket worked directly on `main` has neither.

**Parallel work.** To run several tickets at once, keep the repo checkout on `main` and give every ticket its own worktree (`start --worktree`). Each session then works in its own tree, and `done` merges into the `main` checkout. If nothing has `main` checked out (a checkout was switched to a ticket branch in place, as `/story run` does today), `done` still works: it opens a temporary close tree for `main` (step 1 of `done`), so no session ever has to switch branches to close a ticket.

## Modes

**new `<title>`**: infer the namespace (`DFS` or `CCW`, see Namespaces) from the title and conversation context, asking explicitly only when it cannot decide; allocate the next ID from that prefix's own sequence and a slug, create `backlog/{ID}-{slug}/{ID}-ticket.md` from the template with `status: draft`, and fill in whatever the conversation already settles. Do not invent scenarios; leave placeholders and list gaps under Open questions. Report the path.

**refine `<ID>`**: read the ticket and `CLAUDE.md`, then interview the user to close the gaps against the `ready` gate. Ask in plain terminal text, never with the AskUserQuestion popup, one decision at a time: a short summary of the decision, numbered options each with trade-offs, and a recommendation with its reason. The user answers with a number or keeps discussing. Update the ticket after each answer. If the archetype is blank, make it a decision (a name, or `none`). If the archetype is not `none`, read its `archetype.md` and turn each unresolved edge-case checklist item into a question. Each time a decision closes, add or update an acceptance criterion (next number, see "Acceptance criteria") and tag the scenarios that cover it. State any count (scenarios, criteria, files) only from a mechanical count such as `grep -c`, never from memory or an earlier note. For a ticket that edits instructions (a skill or an agent prompt), record for each scenario whether it will be run mechanically or only checked by reading, and do not present a read-through as a walkthrough of every mode. Before asking for `ready`, make sure the scenarios test the ticket's own purpose (its "So that") against the real state of the repository and environment it will run in (for example, which branches are checked out where, or which tickets are in flight), not only against a clean setup. When the Definition of done needs a live run across components that are already built, name the assumptions between them that the run must exercise: shared names, who performs a step, and which status a downstream check expects. When a scenario or done-item depends on how an already-built component behaves (an agent's tools, which file runs a kind of test case, what a check reads), read that component's real definition rather than an earlier sketch or assumption, and either add the file that would have to change to `touches` or record the workaround as a decision in the ticket, so it is not discovered during implementation (CCW-005.L-5). When the gate is satisfied, show the user the result and ask for confirmation before setting `ready`.

**list `[status|scope]`**: read the frontmatter of every ticket in both folders and print one table (ID, title, status, scope, archetype, priority, depends_on) covering both the `DFS` and `CCW` namespaces together, not a separate table per namespace. Hide `completed/` unless asked. Then print "Dispatchable now": `ready` tickets whose `depends_on` are all `done`, grouped into sets whose `touches` do not overlap, so each set can safely run in parallel. Mark `archetype: none` tickets "manual" there: the story pipeline refuses them, so they are dispatched by hand. A ticket whose branch `tickets/<ID>-<slug>` already exists has been started by some session, even though `main`'s copy may still say `ready` (`start` edits inside the ticket's worktree and never commits). Leave it out of "Dispatchable now" and list it under "Started elsewhere", with its worktree's path from `git worktree list --porcelain`, or "branch only" when it has none.

**start `<ID>` `[--worktree]`**: require `ready` (or `in-progress` when resuming) and all dependencies `done`; otherwise refuse and say why. Then choose the tree to edit in:

- **A worktree already holds the ticket's branch:** enter that worktree. Make the `in-progress` edit to the ticket file inside it, not in the tree `start` was invoked from, and report the worktree's path as the place to work. Never create a second worktree.
- **`--worktree` was given (or the user asked for a worktree) and none exists:** run `git worktree add -b tickets/<ID>-<slug> ../<repo-dir>-<ID>-<slug> main`. Drop `-b` and pass the branch name if the branch already exists. Then make the edit inside the new worktree, as above. If git refuses (for example, the branch is checked out in another tree), stop and report git's message.
- **Otherwise:** set `in-progress` in place, in whatever tree and branch `start` was invoked from, exactly as a ticket worked directly on `main` has always done. This is also the case when `/story run` has already checked out the branch in place.

`start` never commits. The status edit stays uncommitted in the tree it was made in.

**review `<ID>`**: implementation finished and awaiting review. Set `in-review` in whatever tree and branch `review` is invoked in; it never creates, enters or removes a worktree. Confirm the Definition of done items are ticked.

**block `<ID>` `<reason>`** / **unblock `<ID>`**: set `blocked` with `blocked_reason`; on unblock clear the reason and return to `ready` or `in-progress` depending on whether work has begun (ask if unclear).

**done `<ID>`**: require every Definition of done item ticked (or the user's explicit say-so). Then work through the steps below in order, and stop where a step says so. When no `tickets/<ID>-<slug>` branch exists, skip steps 1, 2, 5 and 6: `done` closes in place, in whatever tree it was invoked from, as it always has.

1. **Find `main`'s tree.** The close happens on `main`. Find the tree that has `main` checked out (`git worktree list --porcelain`, line `branch refs/heads/main`) and run every git command below against it with `git -C <that tree>`, even when `done` was invoked from inside the ticket's worktree. Never update `main`'s ref from outside that tree: a checkout whose branch moves underneath it looks as if it had reverted the change.
   - **No tree has `main`:** open a temporary close tree, `git worktree add ../<repo-dir>-close-<ID> main`, and use it as `main`'s tree for the steps below. It is removed at the end (step 6), whatever happens in between, except on a merge conflict (step 2), where it is left for the user to resolve in.
   - **A leftover close tree:** if the tree holding `main` is `../<repo-dir>-close-<ID>` (an earlier `done` or `cancel` for this ticket stopped on a conflict or was interrupted), reuse it, and remove it at step 6 like a fresh one. If it still has an unresolved merge or other uncommitted changes, stop and tell the user to finish or abort that merge there first.
2. **Has the branch landed? If not, merge it.** Do this before anything else, and change nothing until it is settled.
   - Ancestor check: `git merge-base --is-ancestor tickets/<ID>-<slug> main`. If it passes, the branch has landed (fast-forward or merge). Go to step 3.
   - Fallback, for squash merges: `git log --oneline -F --grep="[<ID>]" tickets/<ID>-<slug>..main`. The range keeps out ticket commits made on `main` before the branch forked, such as the refinement commits. If any commit matches, the branch has landed. Go to step 3.
   - Neither passed: say so, then merge the branch into `main` (`git -C <main tree> merge tickets/<ID>-<slug>`, which fast-forwards when it can and otherwise makes an ordinary merge commit). On a conflict, stop and leave the merge unresolved. Do not close or clean up anything. Tell the user to resolve the conflict by hand in `main`'s tree (name its path, which may be the close tree), commit it and re-run `done`. If `main`'s tree is a shared checkout and git refuses the merge because of another session's uncommitted changes there, stop in the same way and report git's message. Never force a resolution, never rebase the branch, and never pass `--ff-only`, `--squash` or `-X` strategy options.
   - The only commits `done` ever makes on `main` are this merge and step 4's close commit.
3. **Close the ticket.** In `main`'s tree, set `status: done`, `completed` and `updated`, and move the folder to `completed/` (`git mv` when tracked, a plain move otherwise), so the status change and the move land together.
4. **Commit the close.** Stage only that one ticket file by name, then re-`git add` it: `git mv` can silently leave the index holding the file's *pre-edit* content when the frontmatter was edited just before the move (L-020). Confirm with `git diff --cached` that the staged version matches the working-tree file, and with `git status` that nothing else is staged. Commit as `docs: close <ID> [<ID>]`, with no separate yes/no prompt. Running `/tickets done` is itself the request. This is a deliberate, narrow exception to "commits are made only when the user asks" (`CLAUDE.md`), bounded to this one status-and-move change, the same kind of exception the `story` skill documents for its per-phase commits.
5. **Clean up the worktree and branch.** Show what will be removed (the worktree's path, if any, and the branch) and ask **once** for confirmation. If the user declines, leave both and say so; the close commit stands.
   - **Dirty worktree:** if `git -C <worktree> status --porcelain` prints anything, refuse the whole cleanup (worktree and branch), list the changes, and leave both in place. The close from steps 3 and 4 still stands.
   - **Worktree:** remove it with `git worktree remove <path>`, never `--force`. If it fails (on Windows, often because the session or an editor still has the directory open), report git's message and the command to re-run, and do not delete the branch, which git would refuse anyway while it is checked out.
   - **Branch:** delete it with `git -C <main tree> branch -d tickets/<ID>-<slug>`, never `-D`. If git refuses because the branch has commits not reachable from `main` (as it does after a squash merge), report the refusal and leave the branch. Deleting it for good (`git branch -D`) is the user's call.
   - Never touch other refs. Rebase backup refs such as `refs/original/*` stay, since they are the user's recovery path.
6. **Remove the close tree**, if step 1 opened or reused one: `git worktree remove ../<repo-dir>-close-<ID>` (never `--force`; it is clean by now, since the only commits made in it are the merge and the close). This needs no confirmation: `done` created it, and nothing but `main`'s own commits ever lived in it.

Before closing, ask whether any friction met on the ticket is worth recording in its own `learnings.md` (CCW-010: learnings live per ticket, as `<ID>.L-<n>`); if so, name the command `/learn add <short description>` for the user to run (the `learn` skill is the only writer of those entries, and its `add` checks for recurrences across every ticket). Never apply a logged proposal without the user's approval. The last line of the reply is the offer `Draft log entries from this ticket with: /learn reflect <ID>` (the real ID filled in). Launch nothing yourself: `learn` is run only by the user.

**cancel `<ID>` `<reason>`**: same as `done` with `status: cancelled`, plus a `## Resolution` section at the end recording the reason. The one exception is step 2: `cancel` never merges the branch, because cancelled work must not land on `main`. It runs the landing check only to report the result. Step 5's `git branch -d` then refuses an unmerged branch as usual, and that refusal is reported. Discarding the branch with `-D` is left to the user.

**validate**: report, without changing anything, every problem found: folder and `status` disagree; missing or malformed frontmatter fields; missing template sections; `blocked` without a reason; `depends_on` pointing at a missing ticket or forming a cycle; a `ready` or `in-progress` ticket with dependencies not `done`; two `ready` tickets whose `touches` overlap and are not linked by `depends_on`; duplicate IDs; slug or ID in the folder name not matching the file. Also:
- **Archetype:** a name other than `none` with no directory `.claude/archetypes/<name>/` (name the ticket and the archetype). `none` needs no directory.
- **Models:** an agent outside architect, test-author, implementer, verifier, retro, or a field other than `model`, `effort`, `maxTurns` (name the ticket and the key, and list the valid ones).
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
- Do not edit other tickets. Commits follow the conventions in `CLAUDE.md`, and only when the user asks.
