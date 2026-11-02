# DFS-048 learnings

- **design / AC-8 seam:** the ticket places the memoization test in `server/src/test/.../result`, assuming the per-scan state lives there, but DFS-047 put a generic, already-implemented memo in `ScanRegistry.completedState` (package `scan`, constructor and `start` package-private). A test of it could not go red nor be written from `result`. The design adds a lazy `OverlapState` holder (built instantly by `completedState`, computing the analysis once in its stubbed `index()`), a second memo layer that exists for the red gate and the test's location. Refinement of a ticket that reuses a predecessor's state mechanism should check where that mechanism lives and whether a criterion on it can still fail red.
- **design / spec:** a nullable nested record (`@Schema(nullable = true)` on a record-typed component) comes out of springdoc's OpenAPI 3.1 as `"type": "null"` beside the `$ref`, and `ng-openapi-gen` turns it into `folder?: FolderSummaryView` without `| null`, while Jackson sends `null`. Harmless with `?.`, but the endpoint archetype's checklist item 5 could mention it for screens reading such a field.
- **design / tooling:** a multi-file heredoc in one Bash call failed to parse (`unexpected EOF while looking for matching '`) after the first files were written, leaving one file missing; the Write tool is the safer route for Java files with apostrophes in comments.
- **tests / traceability:** the ticket marks AC-11 as "read, recorded in `verification.md`" and AC-10 as covered by `OpenApiSpecTest`, so attempt 1 wrote no story test for them and the gate's traceability check failed (it needs a `DFS-048.AC-n` display name for every live criterion). The test-author definition and the endpoint `test-guide.md` could say explicitly that spec and docs criteria still get a named story test (precedent DFS-047.AC-9/AC-10: call the endpoints first so it is red, then read `openapi.json`, `CLAUDE.md`, `ui.md` from the root derived from `dfs.openapi.file`), and refinement could stop writing "read" for a criterion the gate requires a test for.
- **tests / tooling:** a Python script passed through a Bash heredoc turned the Java escape `'\n'` into a real newline in the written file (caught by reading it back); the Edit tool fixed it.
- **verify / by-hand check:** stopping a `bootRun` server started for a done-item needs killing the app's own JVM by the PID it logs (`taskkill //PID <pid> //F`), which sits awkwardly with the verifier rule "never kill Java processes" (meant for Gradle daemons). The verifier definition could say: kill only the forked application PID from the server log, never a daemon; or the endpoint archetype could offer a `bootJar` + `java -jar` recipe whose process is unambiguous.
- **verify / AC-9 scope:** the foreign-`Host` refusal is tested on one of three new endpoints, as the ticket asked; fine because one filter covers all of `/api`, but the endpoint test guide could state that one Host row per controller suffices.

## Entries

### DFS-048.L-1: A reused predecessor mechanism can leave a criterion unable to fail red

- Status: applied
- Impact: local
- Target: `.claude/archetypes/endpoint/archetype.md`, edge-case checklist
- Change: add an item: when a ticket reuses a predecessor's state mechanism (for example `ScanRegistry.completedState`), confirm which package it lives in and whether a criterion on it can fail red from the test location the ticket names. If it cannot, decide in refinement where the test goes, rather than leaving the architect to add a layer only so the test can go red.
- Evidence: DFS-048 `learnings.md` "design / AC-8 seam": the memo lives in package `scan`, already built; the design added an `OverlapState` holder (design commit `62afd5d`) as a second memo layer so AC-8 could go red from `result`.
- Recurrences: 1 (DFS-048)
- Recommendation: apply; one checklist line, and it saves code made only for the red gate.
- Decision: applied 2026-11-02 by the user; commit `docs: apply DFS-047.L-1, DFS-047.L-2, DFS-047.L-3, DFS-048.L-1 and DFS-048.L-2`

### DFS-048.L-2: A nullable nested record is generated without `| null`

- Status: applied
- Impact: local
- Target: `.claude/archetypes/endpoint/archetype.md`, checklist item 5
- Change: note that `@Schema(nullable = true)` on a record-typed component comes out as `"type": "null"` beside the `$ref`, which `ng-openapi-gen` turns into an optional field without `| null` while Jackson sends `null`. Screens reading such a field use `?.`.
- Evidence: DFS-048 `learnings.md` "design / spec" (`folder?: FolderSummaryView` in the generated client).
- Recurrences: 1 (DFS-048)
- Recommendation: apply; a one-line note.
- Decision: applied 2026-11-02 by the user; commit `docs: apply DFS-047.L-1, DFS-047.L-2, DFS-047.L-3, DFS-048.L-1 and DFS-048.L-2`

### DFS-048.L-3: The verifier's "never kill Java processes" leaves no way to stop its own `bootRun`

- Status: applied
- Impact: shared (touches an agent prompt: `.claude/agents/verifier.md`)
- Target: `.claude/agents/verifier.md`, line 28
- Change: reword the rule to "never run `gradlew --stop` or kill a Gradle daemon; to stop a server you started for a by-hand check, kill only the application PID it logged". Alternatively, point to running the built `bootJar` with `java -jar`, whose process is unambiguous (see DFS-047.L-6).
- Evidence: DFS-048 `learnings.md` "verify / by-hand check": the verifier stopped the server with `taskkill //PID <pid> //F`, which the rule as written (from DFS-031.L-4) forbids.
- Recurrences: 1 (DFS-048)
- Recommendation: apply; the rule should keep protecting other sessions' daemons without making a required done-item impossible to clean up.
- Decision: applied 2026-11-02 by the user; commit `docs: apply DFS-047.L-4, DFS-047.L-5 and DFS-048.L-3`
