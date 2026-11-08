## Product owner review

### What you can do now
Paste a printed `claude -n <ID> --permission-mode auto "<command>"` line into a new terminal and that session runs the ticket, `done` included. No session looks up lanes, sends messages or starts another session; `autorun` is gone.

### Acceptance criteria
- AC-1 met (read): `refine` prints the line, or names the open dependency and prints none.
- AC-2 met (read, grep): every story stop ends with the launch line; "safe to /clear" is absent from the story skill.
- AC-3 met (read): `done` step 4a prints one line per unblocked ticket with the "do not run together" note.
- AC-4 met (grep): see the SendMessage note under Known limits.
- AC-5 met (read): the lane lookup and the question are gone from every mode.
- AC-6 met (read): `list` groups by lane, covers every lane, no "Other lanes" heading.
- AC-7 met (selftest, read): `lanes.md` holds only `dfs-product: coordinator`; the hook prints for it and nothing for `CCW-099`; `validate` rule rewritten.
- AC-8 met (selftest): `--lanes` lists a backend and a harness ticket with launch lines for `CCW-099`, nothing for the coordinator or no name.
- AC-9 partly met: gone from the skills, template, `CLAUDE.md` and DFS-060; see Known limits.
- AC-10 met (grep): every line has `--permission-mode auto` and the ticket ID after `-n`; `.claude/settings.json` has no diff.
- AC-11 met (grep): no old-model hits remain in `CLAUDE.md` or the design doc.
- AC-12 met: `story-gate selftest` passes (126 cases).

### Try it
`tools/story-gate/story-gate selftest`; `tools/story-gate/story-gate catch-up --session CCW-099 --lanes` from the repo checkout. The live run (done item 5) is yours: make a ticket ready in the coordinator and paste the line it prints.

### Decided without you
- Lane view, a continuing hand-implemented ticket keeps the text "continue in its worktree, then /tickets review <ID>" instead of a launch line (nothing to start).
- Dropped the "not launched" notification example from the story skill and added the finish to the notification stops.

### Known limits
- Five open tickets (CCW-023, DFS-039, DFS-045, DFS-056, DFS-058) still carry a commented `# autorun: false` template line, and DFS-056 and DFS-058 mention it in text; they are outside `touches`, so AC-9's backlog search is not clean. CCW-032 mentions lanes.
- `SendMessage` still appears once in the story skill (resuming a stopped subagent), so AC-4's grep for it is not literally empty.
- Archetype docs (`component`, `endpoint`) mention "the lane that owns it"; outside `touches`.
- Not run: the live run across a real coordinator and a new terminal.

### Evidence
`story-gate selftest`: PASS, 126 cases. Old-form search (autorun, ListAgents, launch point, owner, message 1 to 3, safe to /clear, ready hand-off) over `.claude/`, `CLAUDE.md`, `docs/design/`: remaining hits are my own "retired" wording, "product owner", the SendMessage above and the archetype lines above.

### How it went
Done by hand in one session, no pipeline. A shell heredoc mangled `\n` escapes in two Java files; fixed.

Ready for the user's approval once the live run is done.
