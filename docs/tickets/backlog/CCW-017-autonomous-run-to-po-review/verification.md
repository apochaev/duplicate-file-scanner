# CCW-017 verification

## Product owner review

### What you can do now

Refine a pipeline ticket with me as before, then answer "ready, autorun". I commit it and start the pipeline myself. You hear from me again (with a push notification if you are away) only at your review, or if the run gets blocked. When a ticket closes, the next autorun ticket waiting on it starts by itself.

### Acceptance criteria

- **AC-1** met (read): the ticket's `gates:` is used when building `plan.json`; the archetype's only when the ticket has none.
- **AC-2** met (read): refine asks the gates question (recommending the design gate for deletion work), and `ready` refuses a pipeline ticket without `gates:`.
- **AC-3** met (mechanical): validate flags `autorun: yes` and `autorun: true` on a hand-implemented ticket; refine asks about autorun.
- **AC-4** met (read): refine commits the ticket alone as `docs: refine <ID> [<ID>]`, then launches, or stops naming the dependency it waits on.
- **AC-5** met (mechanical): after a close, only autorun tickets without a branch are picked; the lowest ID launches, the others are listed with their commands.
- **AC-6** met (mechanical), after a fix: an edit after approval and an uncommitted edit both refuse the launch.
- **AC-7** met (read): the story skill says Claude may start `run` only through the two launch points and never `approve`. Frontmatter (`false`) and text now agree.
- **AC-8** met (read): one notification at the review, at `blocked` and at a refused launch, with examples. "Not sent" isn't treated as an error.
- **AC-9** met (read): the verifier writes this section with these seven headings, then the Summary.
- **AC-10** met (read): the done gate shows this section, or the Summary for older files.
- **AC-11** met: this file.
- **AC-12** pending: the live run on the `scanner-spring` ticket hasn't happened yet.

### Try it

Refine the `scanner-spring` extraction ticket (`docs/design/ui.md` D1) with `/tickets refine`, choose `gates: [done]` and answer "ready, autorun". That run is AC-12's live run, and it closes this ticket's last done-item. Tell the backend session first, since that ticket touches `cli`.

### Decided without you

- **How an approval is recognised:** by `refine`'s own commit subject (`docs: refine <ID> [<ID>]`). My first rule ("newest version that says ready and autorun") let edits committed after approval pass, and the mechanical run caught it. Re-confirming a ticket means another refine commit.
- **Order at `/tickets done`:** it says which ticket it will launch before the cleanup question, then launches it after cleanup, so the worktree cleanup doesn't happen in the middle of a run.
- **Gate names:** `validate` accepts both `test` and `tests` as gate names, since the archetypes and `plan.json` spell that phase differently (a known mismatch, see the story skill).
- **"Not sent" result:** notifications reporting "not sent" (because you're at the terminal) are ignored silently.

### Known limits

- `autorun` doesn't apply to hand-implemented tickets (decision 7).
- When one close unblocks several tickets, only one launches. The rest are listed.
- None of this has run end to end yet (AC-12).
- The read checks confirm the instructions say the right thing, not that every mode behaves correctly in practice.

### Evidence

- The mechanical runs below (scratch repo in the session scratchpad; no real ticket edited).
- `./gradlew build` green in the worktree.
- `git status` shows only files listed under Touches.

### How it went

Implemented by hand in one session. One defect was found by the mechanical AC-6 run and fixed: the approving-commit rule. No retries.

Ready for your approval of the instructions. The ticket stays `in-review` until the AC-12 live run.

## Summary

12 criteria: 11 met (6 by reading, 3 mechanically after one fix, 1 by this file's existence), AC-12 pending its live run. Build green, scope respected.

## Mechanical runs

Scratch repo `ccw017-repo` in the session scratchpad. Throwaway tickets DFS-900 (done) and DFS-901..905.

**AC-3, validate rule:**
```
FINDING DFS-903: autorun 'yes' is not true or false
FINDING DFS-904: autorun true on archetype none
```

**AC-5, lookup after closing DFS-900** (DFS-902 has a branch; DFS-901 and DFS-905 don't):
```
skip DFS-902: branch exists
launch: DFS-901
list: DFS-905 -> /story run DFS-905
```

**AC-6, first rule (newest version with ready + autorun): defect**
```
== AC-6 edited and committed after approval
LAUNCH OK (approved in ea452f7)      <- should refuse
```

**AC-6, fixed rule (refine commit subject), fresh scratch repo:**
```
== no refine commit yet
REFUSE: no approving commit
== approved, unchanged
LAUNCH OK (approved in 054af0a)
== edited and committed after approval
REFUSE: changed since approval 054af0a
== re-confirmed
LAUNCH OK (approved in bfc91de)
== uncommitted edit
REFUSE: uncommitted changes
```

**Against the real repo:** `git log -1 -F --grep="docs: refine CCW-017 [CCW-017]" -- <ticket>` returns `423f388 docs: refine CCW-017 [CCW-017]`.

## Read checks

Each [read] scenario, checked against the edited text:

| Scenario | Where |
|---|---|
| AC-1 ticket gates win / fallback | `story/SKILL.md` preflight step 4 |
| AC-2 gates question, "gates set" | `tickets/SKILL.md` ready gate, refine |
| AC-3/AC-4 commit and launch; waiting on a dependency | `tickets/SKILL.md` refine, "Autonomous launch" |
| AC-7 when Claude may start the skill | `story/SKILL.md` usage paragraph and description |
| AC-8 notification at review and blocked | `story/SKILL.md` "Human gate", "Blocked", "Notifications" |
| AC-9/AC-10 review section shown; fallback | `agents/verifier.md`, `story/SKILL.md` "Human gate" |
