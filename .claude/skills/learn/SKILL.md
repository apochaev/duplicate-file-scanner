---
name: learn
description: Record and manage friction in the learnings log (docs/learnings/log.md) - add an entry, review the open ones, decide, apply a change, or reflect on a finished ticket. Run only by the user.
argument-hint: add|review|decide|apply|reflect [args]
disable-model-invocation: true
---

# Learn

The learnings log is `docs/learnings/log.md`: friction found while working, each entry with evidence, a proposed change and what the user decided. This skill is the only writer of that file. It is the hand-driven version of the learning loop (D6 in `docs/design/story-orchestration.md`); the pipeline's `retro` agent and `/story learn` build on it.

Usage: `/learn <mode> [args]`. Modes: `add`, `review`, `decide`, `apply`, `reflect`. With no mode, run `review`.

## Rules

- **Read the log first, every time.** Its header holds the rules, the statuses and the entry format (`L-<n>`, eight fields). This skill does not repeat them; if the two disagree, the log wins and you say so.
- **Nothing is applied without the user's approval.** The user makes every decision; you draft and recommend. Never change status to `applied`, `parked` or `rejected` on your own.
- **Evidence comes from files:** a ticket, a commit or a file, and what happened. Never a session transcript, and never a count taken from memory. Take entry numbers, recurrence counts and other counts from the file with a command (`grep -c`, `grep -o`), not from recollection.
- **Write only** `docs/learnings/log.md` and, in `apply`, the one target file the user approved (plus that archetype's `archetype.md` when the target is under `.claude/archetypes/`). Nothing else.
- **Never commit.** Commits are made only when the user asks. After a write, list the changed files so the user can stage them by name.
- Dates are absolute ISO. Impact is `shared` when the change touches `CLAUDE.md`, an agent prompt or a skill, otherwise `local`; say why when shared.

## Modes

**add `[description]`**: record one friction.
1. Take the description from the argument, or ask for it in one question.
2. Compare it with every entry's title, Change and Target, of any status. Show up to three candidates, each with its ID, title, status, and its `Change` and `Evidence` lines. Ask: is it the same friction as one of these? The answers are the candidate's ID (yes), `no`, or `show all` (list every open entry, then ask again).
3. **Yes:** name the ticket the friction occurred in (ask if unclear).
   - If the entry's Recurrences field does not name that ticket, append `; <TICKET>: <what happened> (<date>)` to the field and raise the count by one. If the field is a bare count (`1`), first name the original ticket from the entry's Evidence: `2 (<original ticket>: see Evidence; <TICKET>: <what happened> (<date>))`.
   - If it already names that ticket, add `, again <date>: <what happened>` to that ticket's item and leave the count.
   - If the entry is `parked`, set it to `proposed` and rewrite its Recommendation with the new evidence. That is the re-proposal; keep the earlier decision text in Decision as history.
   - If the entry is `rejected`, say so with its Decision line and write nothing, unless the user says it is a different friction (then treat that as `no`).
   - Write no new entry.
4. **No to every candidate:** draft a new entry. Its number is the highest `L-<n>` in the log plus one, from `grep -o '^### L-[0-9]*'`. Fill every field, status `proposed`, Decision empty. Show it, and write it after the user approves or adjusts it.
5. Run the check.

**review**: list the open entries (`proposed` or `parked`) as a table with ID, title, status, recurrences, impact and recommendation. Rank by recurrences in distinct tickets (highest first), then `shared` before `local`, then by number. Below the table give the counts by status, taken from the file. End by naming the commands to decide: `/learn decide L-<n> park|reject`, `/learn apply L-<n>`.

**decide `L-<n> park|reject [reason]`**: record the user's decision. Set Status to `parked` or `rejected` and Decision to `<status> <date> by the user` plus the reason if given. Change no other file. Refuse an entry that is already `applied`. `decide L-<n> apply` is the same as `/learn apply L-<n>`. Run the check.

**apply `L-<n> [L-<m> ...]`**: put an approved change into its target.
1. The entry must be `proposed` or `parked`; otherwise refuse and say why.
2. Read the target. Draft the exact edit for the `Change` line and show it compactly: the file, and the lines to add or change. Say if the target is a shared file.
3. Wait. If the user declines, change neither the target nor the log. If the user adjusts the wording, show it again.
4. On approval, write the target. If the target is under `.claude/archetypes/<name>/`, add one to `version` in that archetype's `archetype.md`.
5. Mark the entry `applied`, with Decision `applied <date> by the user; commit `docs: apply L-<n>``. For several entries in one go, one subject: `docs: apply L-001 and L-005`. Record the planned subject, not a hash. Do not commit; list the files changed.
6. Run the check.

**reflect `<ID>`**: have a fresh, cheap agent draft entries from a finished ticket.
1. Confirm the ticket exists (`docs/tickets/{backlog,completed}/<ID>-*/`); otherwise refuse.
2. Launch the `reflect` agent (`.claude/agents/reflect.md`) with the Agent tool, and give it only the ticket ID and its path. Do not pass anything from this conversation and add no findings of your own: its evidence is the files it reads, which is the point of running it as an agent.
3. Show the user its READ line, so they see whether the history read was cut. If it reports `No friction found`, say so and write nothing.
4. For each finding, confirm mechanically that its cited commit or file exists (`git cat-file -t <hash>`, or a search for the quoted line); drop and report any that do not. Then walk the user through the rest one at a time:
   - `new`: show the drafted entry; on approval write it as in `add`, step 4.
   - `recurrence of L-<n>`: show that entry's Change and Evidence lines and the new evidence; on approval record it as in `add`, step 3.
   - `already counted in L-<n>` and `matches rejected L-<n>`: report them and write nothing.
5. Run the check.

## The check

After every write, re-read the log and report anything below before finishing. Report, do not fix silently, and offer the fix.

- An entry missing one of the eight fields, or a status other than `proposed`, `applied`, `parked`, `rejected`.
- A reused number, or a skipped number (entries must run `L-1` to `L-<n>` with none missing or repeated).
- An `applied`, `parked` or `rejected` entry with an empty Decision line.
- A Recurrences count that does not match its lines. A bare count of 1 with no ticket named is accepted (the entry's Evidence names the ticket); a count above 1 must name that many distinct tickets.

This is a weak check: the model that wrote the entry also checks it and shares its blind spots. It looks only at the structure of the log, not at whether an entry is true.
