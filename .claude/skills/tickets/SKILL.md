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
- **ID**: `DFS-NNN`, one global sequence, zero-padded to three digits. Next ID is the highest number found in both `backlog/` and `completed/` plus one. Never reuse an ID.
- **Slug**: lowercase ASCII kebab-case from the title, 2 to 6 words.
- New tickets start from `TEMPLATE.md` in this skill's folder; copy it verbatim and fill it in.
- Dates are absolute ISO (`2026-09-21`). Set `updated` on every edit. Keep the frontmatter valid YAML.

## Frontmatter

| Field | Values |
|---|---|
| `id` | `DFS-NNN` |
| `title` | short imperative title |
| `status` | `draft`, `ready`, `in-progress`, `in-review`, `blocked`, `done`, `cancelled` |
| `type` | `feature`, `fix`, `refactor`, `test`, `build`, `docs`, `chore`, `spike` |
| `scope` | list of `file-scanner`, `cli`, `build`, `docs` |
| `archetype` | a directory name under `.claude/archetypes/` (the template set the story pipeline uses), or `none`: implemented by hand, not run through the pipeline. Blank in drafts, required from `ready` on |
| `priority` | `high`, `medium`, `low` |
| `depends_on` | list of ticket IDs |
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

## Modes

**new `<title>`**: allocate the next ID and slug, create `backlog/{ID}-{slug}/{ID}-ticket.md` from the template with `status: draft`, and fill in whatever the conversation already settles. Do not invent scenarios; leave placeholders and list gaps under Open questions. Report the path.

**refine `<ID>`**: read the ticket and `CLAUDE.md`, then interview the user to close the gaps against the `ready` gate. Ask in plain terminal text, never with the AskUserQuestion popup, one decision at a time: a short summary of the decision, numbered options each with trade-offs, and a recommendation with its reason. The user answers with a number or keeps discussing. Update the ticket after each answer. If the archetype is blank, make it a decision (a name, or `none`). If the archetype is not `none`, read its `archetype.md` and turn each unresolved edge-case checklist item into a question. Each time a decision closes, add or update an acceptance criterion (next number, see "Acceptance criteria") and tag the scenarios that cover it. When the gate is satisfied, show the user the result and ask for confirmation before setting `ready`.

**list `[status|scope]`**: read the frontmatter of every ticket in both folders and print a table (ID, title, status, scope, archetype, priority, depends_on). Hide `completed/` unless asked. Then print "Dispatchable now": `ready` tickets whose `depends_on` are all `done`, grouped into sets whose `touches` do not overlap, so each set can safely run in parallel. Mark `archetype: none` tickets "manual" there: the story pipeline refuses them, so they are dispatched by hand.

**start `<ID>`**: require `ready` (or `in-progress` when resuming) and all dependencies `done`; otherwise refuse and say why. Set `in-progress`.

**review `<ID>`**: implementation finished and awaiting review. Set `in-review`; confirm the Definition of done items are ticked.

**block `<ID>` `<reason>`** / **unblock `<ID>`**: set `blocked` with `blocked_reason`; on unblock clear the reason and return to `ready` or `in-progress` depending on whether work has begun (ask if unclear).

**done `<ID>`**: require every Definition of done item ticked (or the user's explicit say-so). Set `status: done`, `completed`, `updated`, and move the folder to `completed/` (`git mv` when tracked, a plain move otherwise) so the status change and the move land together. Before closing, ask whether any friction met on the ticket is worth recording in `docs/learnings/log.md`; if so, add it there as `proposed` (format in that file's header). Never apply a logged proposal without the user's approval.

**cancel `<ID>` `<reason>`**: same as `done` with `status: cancelled`, plus a `## Resolution` section at the end recording the reason.

**validate**: report, without changing anything, every problem found: folder and `status` disagree; missing or malformed frontmatter fields; missing template sections; `blocked` without a reason; `depends_on` pointing at a missing ticket or forming a cycle; a `ready` or `in-progress` ticket with dependencies not `done`; two `ready` tickets whose `touches` overlap and are not linked by `depends_on`; duplicate IDs; slug or ID in the folder name not matching the file. Also:
- **Archetype:** a name other than `none` with no directory `.claude/archetypes/<name>/` (name the ticket and the archetype). `none` needs no directory.
- **Models:** an agent outside architect, test-author, implementer, verifier, retro, or a field other than `model`, `effort`, `maxTurns` (name the ticket and the key, and list the valid ones).
- **Criteria:** duplicate numbers; a gap in the numbering 1 to n (a criterion was deleted, not retired); a scenario tag naming a missing or retired criterion. Name the ticket and the ID.
- **Ready-or-later tickets** (`ready`, `in-progress`, `in-review`; drafts, blocked, done and cancelled are not checked): no archetype, no criteria, a scenario without a tag, or a live criterion no scenario covers. Name the ticket and what is missing.
- **Touches (informational note, not a finding):** an entry that resolves to nothing. An entry is a path if it contains `/` or exists at the repository root; otherwise a lowercase dotted name is a Java package, `<module>/src/{main,test}/java/<package as path>`. Entries for files a ticket will create resolve to nothing until the work is done.

Offer to fix mechanical issues.

## Implementing a ticket (for agents)

- Read the ticket and `CLAUDE.md`; do not require anything else.
- Only implement tickets in `ready` or `in-progress`, and run `start` first.
- Stay within `touches`. If the work needs to go beyond it, stop and report instead of editing other files.
- Done-items are never self-graded on a pipeline ticket (an archetype other than `none`): the implementer leaves them unticked, and the verifier ticks each one it has verified. On an `archetype: none` ticket, implemented by hand, the implementing session ticks each item as it is met and notes the evidence beside it. Either way, run `review` when all are ticked.
- Do not edit other tickets. Commits follow the conventions in `CLAUDE.md`, and only when the user asks.
