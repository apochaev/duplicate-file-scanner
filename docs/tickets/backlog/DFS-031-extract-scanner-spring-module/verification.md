# DFS-031 verification

## Product owner review

### What you can do now

The scanner's Spring setup now lives in its own shared module, `scanner-spring`. Any Spring Boot app that depends on it (today `cli`, later the planned `server`) gets the scan beans automatically, with no copied configuration. An app can swap in its own executor, its own `ScanUseCases` or any single port, for example a fake in a test. The CLI itself works exactly as before.

### Acceptance criteria

- **DFS-031.AC-1: met.** With the module on the classpath, the context has exactly one bean for each of the four scan ports, and they share one scan repository: a scan started through one is seen by the others. The module is registered through Spring Boot's `AutoConfiguration.imports` file.
- **DFS-031.AC-2: met.** The default `scanExecutor` runs tasks on virtual threads. An app's own `scanExecutor` replaces it, and scans then run on the app's executor.
- **DFS-031.AC-3: met.** An app's own port bean, or its own `ScanUseCases`, replaces only that bean, and the module still supplies the rest. The test replaces only `GetScanResult`. The other three ports carry the same annotation, which I checked by reading the code (see Known limits).
- **DFS-031.AC-4: met.** An ArchUnit test in `scanner-spring` forbids any dependency on the scanner's `application` and `infra` packages. A runtime check also confirms that no bean the module provides is an internal object.
- **DFS-031.AC-5: met.** `cli` no longer has a `ScannerConfiguration` class, and the real CLI still scans. Two identical files produce one `set` line and exit code 0.

### Try it

There is no new user-facing behaviour. The report is unchanged, and seeing that it is unchanged is the check:

```
dfs.bat C:\Users\apoch\Projects\us.pochaev\dfs\duplicate-file-scanner-DFS-031-extract-scanner-spring-module\file-scanner\src\test\resources\fixtures --format=text
```

Look for two sets (`photo.jpg`/`photo-copy.jpg` at 27 B and `basic\a.txt`/`basic\b.txt` at 18 B) and the line `Summary: 2 sets, 4 files, 45 B reclaimable, 0 skipped, 0 excluded`. To see the wiring tests: `./gradlew :scanner-spring:test :cli:test`.

### Decided without you

- `scanUseCases` accepts any `Executor` named `scanExecutor`, not only an `ExecutorService`, so an app's override is not restricted to `ExecutorService` (design open note 1).
- `cli`'s old `ScannerConfiguration` and its test were deleted in the design phase rather than the implement phase, so the tests could fail against the stubs first (design open note 2).
- The AC-4 test does more than the ArchUnit rule: it also checks at runtime that no provided bean comes from `application`/`infra` (design open note 3).
- The Spring Boot version `4.1.1` is now written in two places (`cli`'s plugin and `scanner-spring`'s BOM). A comment asks to keep them in step (design open note 4).
- `CLAUDE.md` now says "Three Gradle subprojects", and calls the planned `server` the "fourth" subproject.

### Known limits

- `@ConfigurationProperties` is not added yet (ticket decision 2). It comes with the first app-wide setting.
- The AC-3 test replaces only `GetScanResult`. `StartScan`, `CancelScan` and `GetScanStatus` back off through the same `@ConditionalOnMissingBean`, but no test covers them.
- When an app supplies its own `ScanUseCases`, the default `scanExecutor` is still created (unused) and closed at shutdown. This is harmless.
- A port the app replaces no longer shares the scan repository with the others. The class Javadoc documents this.

### Evidence

- `./gradlew build` green at `c4210d3`. The gate reported 370 passed and 3 skipped. I re-ran the build, and it was green.
- `scanner-spring` `ScannerAutoConfigurationTest`: 6 tests, 0 failures (AC-1 x2, AC-2 x2, AC-3 x2).
- `scanner-spring` `ArchitectureTest`: 1 test, 0 failures (AC-4).
- `cli` `CliApplicationTest`: 2 tests, 0 failures (AC-5 plus the existing JSONL test).
- `cli`'s `ArchitectureTest`: no change between `3eed87d` and `c4210d3`.
- Before/after: I built the pre-change commit `3eed87d` in a temporary detached worktree and ran `dfs.bat` from both trees on the 25-file fixtures folder, in text and JSONL. The text reports differ only in the timestamp line. The JSONL files are identical once the `startedAt`/`finishedAt` values are removed. Both runs exited with 0. The temporary worktree is removed.
- Deterministic gate on `c4210d3`: green, disabled, freeze, touches (18 files, all inside touches), stubs, traceability (5 criteria, 8 scenarios) and consistency all passed.

### How it went

Design (architect, opus): 1 attempt, passed. Tests (test-author, sonnet): 1 attempt, passed the red gate (8 story tests red on the stubs). Implement (implementer, sonnet): 1 attempt, passed every gate check. No defects were routed back, and there were no retries or escalations.

Ready for the user's approval.

## Summary

- DFS-031.AC-1: met. There is one bean per port and a shared repository (a scan reaches COMPLETED, and `GetScanResult` returns Success). Registration through `AutoConfiguration.imports` is loaded with `ImportCandidates` and started.
- DFS-031.AC-2: met. The default `scanExecutor` runs tasks on virtual threads. A user `scanExecutor` replaces it (one `ExecutorService` bean, same instance, tasks counted).
- DFS-031.AC-3: met. A user `GetScanResult` and a user `ScanUseCases` each replace only that bean. The other three ports' back-off is verified by reading the code, not by a test.
- DFS-031.AC-4: met. The ArchUnit rule plus a runtime package check of the beans.
- DFS-031.AC-5: met. The real `Main` writes one `set` line with exit code 0, and `ScannerConfiguration` is absent.
- Design conformance: the public API, bean bodies, qualifiers, `shutdownNow` and Javadoc match `design.md`. All 11 wiring checklist items are disposed of and match the diff.
- Test quality: the tests check behaviour (bean counts, identity, scan outcome, thread kind, executor usage, CLI output), not the implementation's text.
- Invariants: no deletion, walking or hashing code changed. The byte-identical before/after report shows that scanning is unaffected. Symlink, hard-link and empty-file behaviour stay in `file-scanner`, untouched.
- Done-items: all six verified and ticked.
- Ready for the human gate.

## Per-criterion detail

| Criterion | Scenario(s) | Test | Verdict |
|---|---|---|---|
| AC-1 | four ports; registration | `ScannerAutoConfigurationTest.providesTheFourPorts`, `registeredForSpringBoot` | met |
| AC-2 | virtual threads; app's executor | `defaultExecutorIsVirtual`, `appExecutorReplacesDefault` | met |
| AC-3 | app's port; app's ScanUseCases | `appPortReplacesOnlyThatPort`, `appScanUseCasesBacksPorts` | met (one port type exercised) |
| AC-4 | depends only on domain | `ArchitectureTest.dependsOnlyOnScannerDomain` | met |
| AC-5 | CLI scans through autoconfigured beans | `CliApplicationTest.scansThroughAutoconfiguredBeans` | met |

## Test-quality notes

- The tests touch the context with `getBean` before the size and identity assertions, so a startup failure surfaces with its root cause (the design's note to the test-author).
- The await loop has a 30 s cap and then asserts COMPLETED, so a hang fails instead of blocking the suite.
- `appExecutorReplacesDefault` asserts that `getBeansOfType(ExecutorService.class)` has size 1. This would also catch a default executor wrongly created alongside the user's.
- The AC-4 runtime half is a weak check by nature (package names), but it is the right complement to the static rule. It would catch a bean that returns the factory's service object undelegated.
- Gap (minor): AC-3 exercises one port override out of four.

## Invariant and design concerns

- None blocking. The `CLAUDE.md` safety invariants (last copy, re-verify, pure preview, hard links, symlinks/junctions, empty files) are not touched by this wiring move. The identical before/after scan output confirms that scanner behaviour is preserved.
- `CLAUDE.md`'s `cli` bullet still says only "Depends on `file-scanner` via `implementation(project(":file-scanner"))`". The new `scanner-spring` bullet states that `cli` depends on it, so this is cosmetic.
