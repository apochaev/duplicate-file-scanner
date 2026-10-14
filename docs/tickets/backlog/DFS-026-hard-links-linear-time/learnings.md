## design (architect) — 2026-10-13

- **Frontmatter `touches: []` vs Scope.** The ticket's frontmatter `touches` is empty while its Scope section lists the paths. A touches check that reads frontmatter would see nothing allowed (or everything); the refine step should fill `touches` from Scope.
- **Port reshape breaks test compilation between design and tests.** Changing an existing port's method (`isSameFile` -> `key`) is the design itself, so the architect's stub commit leaves `compileTestJava` broken until the test-author rewrites the fakes. The archetype only covers new ports and deferred methods; it has no guidance for reshaping an existing port of an existing stage.
- **Build-config criteria cannot be stubbed.** AC-7 (JVM flags, manifest attribute) has no code path through a `not implemented:` stub, so its tests fail on assertions at the red gate; and AC-6's "only method" assertion is already green after design. RedCheck may need an allowance for such criteria, or the ticket should mark them.
- **AC-7 needs a cli test file not listed in Scope's touches** (e.g. `cli/src/test/java/us/pochaev/dfs/cli/NativeAccessTest.java`); flagged for the design gate rather than assumed.

## design (architect, rework) — 2026-10-13

- **Red gate vs reshaping a wired stage.** `RedCheck` fails any non-story test that fails, including with the current ticket's own `not implemented:` stub. When a ticket reshapes a stage that is already wired into the pipeline (here `HardLinkDetector.regroup` and `NioFileIdentity.key`), every existing test that forms a duplicate set (stage, pipeline, end-to-end and cli tests) hits the stub and is reported as a regression. The pipeline has no rule for this; `RedCheck` could accept a non-story failure whose cause is the current ticket's stub, or the archetype could tell the architect how to stage such a change.
- **Story tests and assumptions.** A story test whose platform assumption comes first is skipped at the red gate on the other platform; the archetype's test guidance should say "reach the stub before any `Assumptions` call" by default.
