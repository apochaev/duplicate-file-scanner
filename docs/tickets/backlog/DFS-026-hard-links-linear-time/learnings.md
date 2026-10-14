## design (architect) — 2026-10-13

- **Frontmatter `touches: []` vs Scope.** The ticket's frontmatter `touches` is empty while its Scope section lists the paths. A touches check that reads frontmatter would see nothing allowed (or everything); the refine step should fill `touches` from Scope.
- **Port reshape breaks test compilation between design and tests.** Changing an existing port's method (`isSameFile` -> `key`) is the design itself, so the architect's stub commit leaves `compileTestJava` broken until the test-author rewrites the fakes. The archetype only covers new ports and deferred methods; it has no guidance for reshaping an existing port of an existing stage.
- **Build-config criteria cannot be stubbed.** AC-7 (JVM flags, manifest attribute) has no code path through a `not implemented:` stub, so its tests fail on assertions at the red gate; and AC-6's "only method" assertion is already green after design. RedCheck may need an allowance for such criteria, or the ticket should mark them.
- **AC-7 needs a cli test file not listed in Scope's touches** (e.g. `cli/src/test/java/us/pochaev/dfs/cli/NativeAccessTest.java`); flagged for the design gate rather than assumed.
