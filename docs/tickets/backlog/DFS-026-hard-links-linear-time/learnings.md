## design (architect) — 2026-10-13

- **Frontmatter `touches: []` vs Scope.** The ticket's frontmatter `touches` is empty while its Scope section lists the paths. A touches check that reads frontmatter would see nothing allowed (or everything); the refine step should fill `touches` from Scope.
- **Port reshape breaks test compilation between design and tests.** Changing an existing port's method (`isSameFile` -> `key`) is the design itself, so the architect's stub commit leaves `compileTestJava` broken until the test-author rewrites the fakes. The archetype only covers new ports and deferred methods; it has no guidance for reshaping an existing port of an existing stage.
- **Build-config criteria cannot be stubbed.** AC-7 (JVM flags, manifest attribute) has no code path through a `not implemented:` stub, so its tests fail on assertions at the red gate; and AC-6's "only method" assertion is already green after design. RedCheck may need an allowance for such criteria, or the ticket should mark them.
- **AC-7 needs a cli test file not listed in Scope's touches** (e.g. `cli/src/test/java/us/pochaev/dfs/cli/NativeAccessTest.java`); flagged for the design gate rather than assumed.

## design (architect, rework) — 2026-10-13

- **Red gate vs reshaping a wired stage.** `RedCheck` fails any non-story test that fails, including with the current ticket's own `not implemented:` stub. When a ticket reshapes a stage that is already wired into the pipeline (here `HardLinkDetector.regroup` and `NioFileIdentity.key`), every existing test that forms a duplicate set (stage, pipeline, end-to-end and cli tests) hits the stub and is reported as a regression. The pipeline has no rule for this; `RedCheck` could accept a non-story failure whose cause is the current ticket's stub, or the archetype could tell the architect how to stage such a change.
- **Story tests and assumptions.** A story test whose platform assumption comes first is skipped at the red gate on the other platform; the archetype's test guidance should say "reach the stub before any `Assumptions` call" by default.

## implement (implementer) — 2026-10-14

- **Measured done-items need a test-tree file the tests phase did not write.** The impl-guide (L-023) asks for a committed manual benchmark in the test tree; that is a new test-tree file in the implement phase. `FreezeCheck` is scoped to the tests phase's artifacts, so it passes, but the role text ("never edit a test file") reads ambiguously for a new non-test benchmark. The archetype could say explicitly that the implementer adds such benchmarks (plain `main`, no `@Test`/`@Disabled`).
- **Error mapping beyond the design.** `WindowsFileId` maps Win32 2/3/5 to `NoSuchFileException`/`AccessDeniedException` (else `FileSystemException`), so the drop detail reads like the JDK's own (`<path>: CreateFileW failed, Win32 error 5`). Still `IOException`, so AC-5's path is unchanged.

## verify (verifier) — 2026-10-14

- **"Recorded in this ticket" done-items vs the verifier's write rule.** Two done-items (the 10,000-file key timing and the after-change scan) are only met once figures are written into the ticket, but the implementer may not tick or edit done-items and the verifier is told to edit the ticket only to tick. The verifier appended its own reproduced figures to those two done-item lines when ticking them. The prompt or the ticket template should say who writes measured figures into the ticket (for example the verifier, from its own runs).
- **Reshaping a port can silently retire an older criterion's test name.** The DFS-004.AC-9 tests in `NioFileIdentityTest` were replaced by DFS-026.AC-4, which covers the same facts, so no test carries `DFS-004.AC-9` any more. The traceability check only looks at the current ticket. The test-guide could ask the test-author to keep or re-tag a closed ticket's criterion ID when rewriting its tests, or to list the retired IDs in test-plan.md.
- **Real-run done-items need files outside the tree.** Reproducing the synthetic-folder scan means generating 7,993 files, which conflicts with the verifier's "Bash only to read" rule. This verifier wrote them under the session scratchpad, outside the repo. The verifier prompt could explicitly allow scratch files outside the worktree for real-run done-items.
