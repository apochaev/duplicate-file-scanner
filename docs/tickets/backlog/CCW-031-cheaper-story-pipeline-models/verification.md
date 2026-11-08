# CCW-031 verification

## Product owner review

### What you can do now
The architect runs on `sonnet/high` for `component`, `endpoint` and `wiring` tickets and stays on `opus/high` for `use-case` and `pipeline-stage`. `docs/tickets/lanes.md` recommends launching `dfs-backend` and `dfs-frontend` with `claude --model sonnet -n <name>`, and keeping `dfs-harness` and `dfs-product` on Opus.

### Acceptance criteria
- AC-1 met (mechanical): `grep -n "architect:"` over the archetypes shows `sonnet, effort: high, maxTurns: 25` for component, endpoint and wiring (line 7 of each), `opus, effort: high, maxTurns: 25` for pipeline-stage and `opus, effort: high, maxTurns: 35` for use-case. Retry scenario (read): the story skill's step 6 reads "one tier above whatever that resolved to (`sonnet`→`opus` for model; one step up for effort; unchanged `maxTurns` unless the archetype says otherwise)", so a failed Sonnet design attempt on a wiring ticket retries on Opus.
- AC-2 met (mechanical): `grep -n "model" docs/tickets/lanes.md` hits line 9 only, which names `backend` and `frontend` with `claude --model sonnet -n dfs-backend` / `dfs-frontend`, and `harness` (`dfs-harness`) and the coordinator (`dfs-product`) as staying on Opus.
- AC-3 met (mechanical): `git diff main -- .claude/agents` prints nothing; `retro.md` and `reflect.md` read `model: sonnet`, `effort: low`.
- AC-4 met (mechanical): `tools/story-gate/story-gate selftest` exits 0: "PASS selftest: 126 cases match their expected verdicts".

### Try it
`grep -n "architect:" .claude/archetypes/*/archetype.md | grep model` and `grep -n model docs/tickets/lanes.md`. Next `dfs-frontend` or `dfs-backend` launch: `claude --model sonnet -n dfs-frontend`.

### Decided without you
- The lane note is one paragraph after the session-naming paragraph, giving both launch commands in full and saying it is a recommendation, not enforced.

### Known limits
- Whether a Sonnet session follows the `story` skill's gates and notifications is unverified until the first Sonnet-driven story (done-item left open, decision 3).
- The three-run confirmation of the architect step-down (decision 4) is a follow-up the user runs.

### Evidence
Old-form search of `.claude/`, `CLAUDE.md` and `docs/design/` for `architect: {model: opus` after the edits, each remaining hit explained:
- `.claude/archetypes/pipeline-stage/archetype.md:7`, `.claude/archetypes/use-case/archetype.md:7`: kept on Opus by decision 1.
- `.claude/skills/tickets/SKILL.md:51` (frontmatter table) and `.claude/skills/tickets/TEMPLATE.md:8`: the `models` field's example syntax, out of scope.
- `docs/design/story-orchestration.md:183`: the shape example of `models:`, out of scope (with the agent default table near line 176).

Files changed against `main`: the three archetype files (line 7 each), `docs/tickets/lanes.md` (one paragraph), the ticket file (status) and this file. All inside `touches` except the ticket's own folder.
