# DFS-031 learnings

## design (architect)

- **A criterion that is a pure static guard cannot go red.** AC-4 ("scanner-spring depends on nothing in application/infra, enforced by an ArchUnit test") is a story test by traceability, but an ArchUnit rule passes against stubs, and `RedCheck` rejects a passing story test. The wiring `test-guide.md` says guard tests "carry no criterion ID", which conflicts with a ticket making one a criterion. Worked around by adding a stub-reaching runtime half to the AC-4 test (no port bean's class is in application/infra). Suggest: the wiring archetype (and refinement) say that a guard rule is a done-item, not an acceptance criterion, or that a guard criterion must pair the static rule with a runtime check that reaches a stub.
- **Moving a composition root needs the old one deleted at design.** With `@ConditionalOnMissingBean`, a still-present `cli` `ScannerConfiguration` makes every autoconfigured bean back off, so AC-5 would pass against the stubs. The architect deleted it (and the test compiling against it) in the design phase. The archetype's "Where files go" covers adding a composition root, not moving one; a line on "delete the replaced wiring at design so the app reaches the new stubs" would help.
- **`ApplicationContextRunner` and the red gate (unverified, from Spring Boot's documented behaviour).** AssertJ's `assertThat(context).hasSingleBean(...)` on a failed context throws an `AssertionError` whose trace is message text, not a `Caused by:` chain, so the gate would not see the stub as root cause; `context.getBean(...)` throws `IllegalStateException` with the startup failure as cause. Worth a line in the wiring `test-guide.md`.

## verify (verifier)

- **"Any one of N" criteria tested with one instance.** AC-3 says "its own bean of one port type (StartScan, CancelScan, GetScanStatus or GetScanResult)", and the test-author covered only `GetScanResult`. Accepted because the four beans share one annotation, but a parameterized test over the four port types would have cost little. Suggest: the wiring `test-guide.md` says that an override criterion listing several bean types gets one case per type (a `@ParameterizedTest`), or the ticket names one representative explicitly.
- **A "same report before and after" done-item needs the pre-change build.** Reproducing it took a detached temporary worktree at the refine commit, built with `dfs.bat`, then removed. A refactor archetype could document this recipe (worktree path outside the repo, fixtures folder only, compare after dropping timestamp lines and `startedAt`/`finishedAt`), so each verifier does not reinvent it.
- **Do not run `gradlew --stop` during verification.** It stops every Gradle daemon on the machine, including those of parallel worktree sessions. The verifier ran it once before removing the temporary worktree. Removal works without it (`git worktree remove` succeeded; build outputs are ignored files).

## Entries

### DFS-031.L-1: `wiring` test-guide still says guard tests carry no criterion ID

- Status: applied
- Impact: local (one archetype's test guide)
- Target: `.claude/archetypes/wiring/test-guide.md`
- Change: replace "guard tests carry no criterion ID" with DFS-026.L-3's rule: a criterion with no code path through a stub (a static guard such as an ArchUnit rule) gets a plain test with no story ID, and its story test also reaches a stub (for example by starting the context and checking the provided beans).
- Evidence: DFS-031 architect note "A criterion that is a pure static guard cannot go red" above: AC-4's ArchUnit rule passes against stubs, so the architect added a runtime bean check (design commit 2bec7f1). DFS-026.L-3 (applied) states the rule, but it never reached the wiring guide.
- Recurrences: 1 (DFS-031)
- Recommendation: apply; the architect had to work around the guide to pass the red gate.
- Decision: applied 2026-10-31 by the user; commit `docs: apply DFS-031.L-1, L-2, L-4 [DFS-031]`

### DFS-031.L-2: Moving a composition root needs the old wiring deleted at design

- Status: applied
- Impact: local (one archetype)
- Target: `.claude/archetypes/wiring/archetype.md`, "Where files go"
- Change: add: when a ticket moves a composition root (for example into an autoconfigure module), the architect deletes the replaced wiring and the tests that compile against it in the design phase; otherwise `@ConditionalOnMissingBean` lets the old beans win and the new story tests pass against the stubs.
- Evidence: DFS-031 architect note "Moving a composition root needs the old one deleted at design" above; design commit 2bec7f1 deleted `cli`'s `ScannerConfiguration` and `ScannerConfigurationTest`.
- Recurrences: 1 (DFS-031)
- Recommendation: apply; a concrete trap avoided only by the architect's judgement.
- Decision: applied 2026-10-31 by the user; commit `docs: apply DFS-031.L-1, L-2, L-4 [DFS-031]`

### DFS-031.L-3: `ApplicationContextRunner` assertions may hide the stub from the red gate

- Status: proposed
- Impact: local (one archetype's test guide)
- Target: `.claude/archetypes/wiring/test-guide.md`
- Change: add: read a context in a story test through `context.getBean(...)`, which throws with the startup failure as its cause, rather than `assertThat(context).hasSingleBean(...)` on a failed context, whose trace may not show the stub as root cause.
- Evidence: DFS-031 architect note "`ApplicationContextRunner` and the red gate (unverified, from Spring Boot's documented behaviour)" above; the tests in ddf443f used `getBean` and passed the red gate, so the AssertJ form was never tried.
- Recurrences: 1 (DFS-031)
- Recommendation: park; unverified, and verify it against the red gate before writing it into a guide.
- Decision:

### DFS-031.L-4: The verifier ran `gradlew --stop`, stopping other sessions' Gradle daemons

- Status: applied
- Impact: shared (an agent prompt: `.claude/agents/verifier.md`)
- Target: `.claude/agents/verifier.md`
- Change: add: never run `gradlew --stop` (or kill Java or Gradle processes); it stops every Gradle daemon on the machine, including parallel worktree sessions'. A temporary worktree is removed with `git worktree remove` without stopping daemons.
- Evidence: DFS-031 verifier note "Do not run `gradlew --stop` during verification" above: it ran it once before removing its temporary worktree, while the DFS-035 and CCW-017 worktrees were active; the removal works without it.
- Recurrences: 1 (DFS-031)
- Recommendation: apply; a safety rule that matters on every story now that sessions run in parallel.
- Decision: applied 2026-10-31 by the user; commit `docs: apply DFS-031.L-1, L-2, L-4 [DFS-031]`

### DFS-031.L-5: A criterion listing several bean types was tested with one

- Status: proposed
- Impact: local (one archetype's test guide)
- Target: `.claude/archetypes/wiring/test-guide.md`
- Change: add: an override criterion that lists several bean types ("any one of N") gets one parameterized case per type, named per DFS-018.L-1 so each invocation keeps the criterion ID.
- Evidence: DFS-031 verifier note "'Any one of N' criteria tested with one instance" above: AC-3 names four port types; `ScannerAutoConfigurationTest` (ddf443f) covers only `GetScanResult`; accepted in verification.md's Known limits.
- Recurrences: 1 (DFS-031)
- Recommendation: park; one occurrence, and the gap was accepted without a defect.
- Decision:

### DFS-031.L-6: A "same report before and after" done-item needs a pre-change build recipe

- Status: proposed
- Impact: local (one archetype)
- Target: `.claude/archetypes/wiring/archetype.md` (or a future refactor archetype)
- Change: add a recipe for behaviour-preserving done-items: build the pre-change commit in a temporary detached worktree outside the repo, run both builds on the fixtures folder only, compare after stripping timestamps, then `git worktree remove` it.
- Evidence: DFS-031 verifier note "A 'same report before and after' done-item needs the pre-change build" above; verification.md Evidence: detached worktree at 3eed87d, text and JSONL compared. DFS-026.L-6 (applied) allows scratch files outside the tree but gives no recipe.
- Recurrences: 1 (DFS-031)
- Recommendation: park; one occurrence, and the verifier managed without the recipe.
- Decision:
