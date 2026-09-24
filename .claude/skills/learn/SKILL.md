---
name: learn
description: Record and manage friction in each ticket's own learnings.md - add an entry, review the open ones across every ticket, decide, apply a change, or reflect on a finished ticket. Run only by the user.
argument-hint: add|review|decide|apply|reflect [args]
disable-model-invocation: true
---

# Learn

Learnings live in each ticket's own `docs/tickets/{backlog,completed}/<ID>-<slug>/learnings.md`: friction found while working that ticket, each entry with evidence, a proposed change and what the user decided. An entry is identified `<TICKET-ID>.L-<n>`, numbered only within its own ticket's file — never a shared, global sequence. This skill is the only writer of the structured entries in these files (the free-text notes phase agents also write there are untouched). It is the hand-driven version of the learning loop (D6 in `docs/design/story-orchestration.md`); the pipeline's `retro` agent and `/story learn` build on it.

Usage: `/learn <mode> [args]`. Modes: `add`, `review`, `decide`, `apply`, `reflect`. With no mode, run `review`.

**Why per-ticket, not one shared file (CCW-010):** two sessions on diverged branches, each proposing an entry against their own copy of a single shared, sequentially-numbered file, can independently compute the same "next" number for two unrelated findings — and once did (`docs/tickets/completed/DFS-002-partial-hash-stage/learnings.md`, entry `DFS-002.L-8`). Since a given ticket is worked by one session at a time, and two different tickets' entries live in two different files, this cannot happen here: there is no shared counter to race, and two sessions on different tickets never even touch the same file.

## Rules

- **Read a ticket's `learnings.md` first, before touching it.** Its "## Entries" section (if any) holds existing entries in the format below; the free-text notes above it, if present, are a separate, older convention (D6) and are never edited by this skill.
- **Nothing is applied without the user's approval.** The user makes every decision; you draft and recommend. Never change status to `applied`, `parked` or `rejected` on your own.
- **Evidence comes from files:** a ticket, a commit or a file, and what happened. Never a session transcript, and never a count taken from memory. Take entry numbers, recurrence counts and other counts from the files with a command (`grep -c`, `grep -o`), not from recollection.
- **Write only** the one ticket's `learnings.md` a new or edited entry belongs to, and, in `apply`, the one target file the user approved (plus that archetype's `archetype.md` when the target is under `.claude/archetypes/`). A recurrence of an entry that lives in a *different*, usually already-`completed`, ticket is the one deliberate exception: edit that entry in place, in its own ticket's file, staged and committed by name (`CLAUDE.md`'s parallel-session convention covers the collision risk, which is low since a recurrence almost always names a ticket no one else is concurrently working). Never write to `docs/learnings/log.md` — it is retired (CCW-010); see its own text.
- **Never commit.** Commits are made only when the user asks. After a write, list the changed files so the user can stage them by name.
- Dates are absolute ISO. Impact is `shared` when the change touches `CLAUDE.md`, an agent prompt or a skill, otherwise `local`; say why when shared.

## Entry format

```
### <TICKET-ID>.L-<n>: <short title>

- Status: proposed | applied | parked | rejected
- Impact: local | shared (touches CLAUDE.md, an agent prompt or a skill)
- Target: <file the change would go in>
- Change: <what would be added or changed, in a sentence or two>
- Evidence: <ticket or commit, and what happened>
- Recurrences: <count of distinct tickets; name each ticket and what happened, repeats within one ticket on its line>
- Recommendation: <apply | park | reject, and why>
- Decision: <date and choice once decided; for applied, the commit>
```

A ticket's first entry creates its `learnings.md` if none exists (`# <ID> learnings`, then `## Entries`, then the entry) — or, if the ticket already has one (free-text notes from D6), appends a `## Entries` section below the existing content, never editing it.

## Modes

**add `[description]`**: record one friction.
1. Take the description from the argument, or ask for it in one question. Name the ticket the friction occurred in — ask if unclear; every entry needs one, since it decides the target file.
2. Compare it with every entry's title, Change and Target, of any status, across every ticket: `grep -rn '^### [A-Z]*-[0-9]*\.L-[0-9]*' docs/tickets/backlog docs/tickets/completed`, then read the matching files' entries. Show up to three candidates, each with its full ID, title, status, and its `Change` and `Evidence` lines. Ask: is it the same friction as one of these? The answers are the candidate's ID (yes), `no`, or `show all` (list every open entry across every ticket, then ask again).
3. **Yes:** the candidate's own ticket is where it's recorded, regardless of which ticket the new occurrence happened in.
   - If the entry's Recurrences field does not name the new ticket, append `; <TICKET>: <what happened> (<date>)` to the field and raise the count by one. If the field is a bare count (`1`), first name the original ticket from the entry's Evidence: `2 (<original ticket>: see Evidence; <TICKET>: <what happened> (<date>))`.
   - If it already names that ticket, add `, again <date>: <what happened>` to that ticket's item and leave the count.
   - If the entry is `parked`, set it to `proposed` and rewrite its Recommendation with the new evidence. That is the re-proposal; keep the earlier decision text in Decision as history.
   - If the entry is `rejected`, say so with its Decision line and write nothing, unless the user says it is a different friction (then treat that as `no`).
   - Write no new entry. If the candidate's ticket differs from the one named in step 1, this is a cross-ticket edit — stage and commit that file by name (see Rules).
4. **No to every candidate:** draft a new entry in the ticket named in step 1. Its number is the highest `<TICKET-ID>.L-<n>` in that ticket's own `learnings.md` plus one (`grep -o '^### [A-Z]*-[0-9]*\.L-[0-9]*'` on that one file), or `1` if the file has no "## Entries" section yet. Fill every field, status `proposed`, Decision empty. Show it, and write it after the user approves or adjusts it.
5. Run the check, on the one ticket file written.

**review**: list the open entries (`proposed` or `parked`) across every ticket as one table with full ID, title, status, recurrences, impact and recommendation, built by scanning every `docs/tickets/{backlog,completed}/*/learnings.md`. Rank by recurrences in distinct tickets (highest first), then `shared` before `local`, then by ID. Below the table give the counts by status, taken from the files. End by naming the commands to decide: `/learn decide <TICKET-ID>.L-<n> park|reject`, `/learn apply <TICKET-ID>.L-<n>`.

**decide `<TICKET-ID>.L-<n> park|reject [reason]`**: record the user's decision. Find the entry in that ticket's own `learnings.md`. Set Status to `parked` or `rejected` and Decision to `<status> <date> by the user` plus the reason if given. Change no other file. Refuse an entry that is already `applied`. `decide <TICKET-ID>.L-<n> apply` is the same as `/learn apply <TICKET-ID>.L-<n>`. Run the check on that one file.

**apply `<TICKET-ID>.L-<n> [<TICKET-ID>.L-<m> ...]`**: put an approved change into its target.
1. Each entry must be `proposed` or `parked`; otherwise refuse and say why.
2. Read the target. Draft the exact edit for the `Change` line and show it compactly: the file, and the lines to add or change. Say if the target is a shared file.
3. Wait. If the user declines, change neither the target nor the entry's file. If the user adjusts the wording, show it again.
4. On approval, write the target. If the target is under `.claude/archetypes/<name>/`, add one to `version` in that archetype's `archetype.md`.
5. Mark each entry `applied`, with Decision `applied <date> by the user; commit `docs: apply <TICKET-ID>.L-<n>``. For several entries in one go, one subject naming all of them. Record the planned subject, not a hash. Do not commit; list the files changed (the target, plus every ticket's `learnings.md` an applied entry lives in).
6. Run the check, on every ticket file touched.

**reflect `<ID>`**: have a fresh, cheap agent draft entries from a finished ticket.
1. Confirm the ticket exists (`docs/tickets/{backlog,completed}/<ID>-*/`); otherwise refuse.
2. Launch the `reflect` agent (`.claude/agents/reflect.md`) with the Agent tool, and give it only the ticket ID and its path. Do not pass anything from this conversation and add no findings of your own: its evidence is the files it reads, which is the point of running it as an agent.
3. Show the user its READ line, so they see whether the history read was cut. If it reports `No friction found`, say so and write nothing.
4. For each finding, confirm mechanically that its cited commit or file exists (`git cat-file -t <hash>`, or a search for the quoted line); drop and report any that do not. The agent only ever compared a finding against ticket `<ID>`'s own `learnings.md` (it does not read other tickets, by design); for a finding it reports `new`, also run the cross-ticket comparison from `add`, step 2 before presenting it, since the friction may already be recorded against a *different* ticket the agent never saw. Then walk the user through the rest one at a time, offering write, reject or skip:
   - `new`: show the drafted entry (numbered within ticket `<ID>`'s own `learnings.md`, per the format above); on write, add it as in `add`, step 4; on reject, write it anyway but as `rejected`, with Decision `rejected <date> by the user`, so a later run reports `matches rejected <TICKET-ID>.L-<n>` and does not propose it again; on skip, write nothing and leave no trace.
   - `recurrence of <TICKET-ID>.L-<n>`: show that entry's Change and Evidence lines and the new evidence; on write, record it as in `add`, step 3 (in the *other* ticket's file, since that is where the original entry lives); on reject or skip, write nothing.
   - `already counted in <TICKET-ID>.L-<n>` and `matches rejected <TICKET-ID>.L-<n>`: report them and write nothing.
5. Run the check, on every ticket file touched.

## The check

After every write, re-read every ticket's `learnings.md` file touched by that write and report anything below before finishing. Report, do not fix silently, and offer the fix. Unlike the retired shared log, this check is per-file: never compare one ticket's numbering against another's.

- An entry missing one of the eight fields, or a status other than `proposed`, `applied`, `parked`, `rejected`.
- Within one ticket's file: a reused local number, or a skipped one (that file's entries must run `.L-1` to `.L-<n>` with none missing or repeated).
- An `applied`, `parked` or `rejected` entry with an empty Decision line.
- A Recurrences count that does not match its lines. A bare count of 1 with no ticket named is accepted (the entry's Evidence names the ticket); a count above 1 must name that many distinct tickets.

This is a weak check: the model that wrote the entry also checks it and shares its blind spots. It looks only at the structure of each file, not at whether an entry is true.
