## design (architect, attempt 1) — 2026-10-13

- Wrote `design.md` (Summary, API, placement, checklist 1-14, configuration, notes for implementer and test-author).
- Reshaped `FileIdentity` to the single method `Object key(Path) throws IOException`; stubbed `NioFileIdentity.key`, new package-private `WindowsFileId.key` (with private `record Key`), and `HardLinkDetector.regroup`; all throw `UnsupportedOperationException("not implemented: DFS-026")`.
- `./gradlew :file-scanner:compileJava :cli:compileJava` passes. `compileTestJava` does not until the test-author rewrites the four `FileIdentity` fakes (expected; see design.md).
- Build files and CLAUDE.md left to the implementer (AC-7, decision 5).
- Expected commit: `feat(file-scanner): design key-based hard-link detection [DFS-026]` (orchestrator commits).

### Design gate feedback (user, 2026-10-13), for the next design attempt

Make every story test reach a stub first. `tools/story-gate/RedCheck.java` rejects a story test that passes or is skipped against the stubs; each must fail with `not implemented:`. Concretely:

- AC-3: the test calls `NioFileIdentity.key` on a temporary file before the "fileKey() not null" assumption, so it fails on the stub on Windows too.
- AC-6: the test reads a key twice first, then checks the declared methods.
- AC-7: add the three build settings in this design phase (test `jvmArgs("--enable-native-access=ALL-UNNAMED")` in the root `build.gradle.kts`, `applicationDefaultJvmArgs` and the `bootJar` manifest attribute `Enable-Native-Access: ALL-UNNAMED` in `cli/build.gradle.kts`). Its story test in `file-scanner` first calls `NioFileIdentity.key` on a temporary file, then asserts `isNativeAccessEnabled()`. The `cli` manifest check becomes a plain test with no story ID (`cli/src/test/java/us/pochaev/dfs/cli/NativeAccessTest.java`), which passes at the red gate because the settings are already in place.
- Record all of this in design.md for the test-author.

## design (architect, attempt 1 rework after the design gate) — 2026-10-13

- Added the native-access build settings: root `build.gradle.kts` (`jvmArgs("--enable-native-access=ALL-UNNAMED")` on every `Test` task); `cli/build.gradle.kts` (`applicationDefaultJvmArgs`, `bootJar` manifest `Enable-Native-Access: ALL-UNNAMED`, and `tasks.test` depending on `bootJar` with `-Ddfs.bootJar=<path>` for `NativeAccessTest`). Checked the built jar's manifest.
- Reworked `design.md`: Summary (per-criterion red-gate test order, settled gate points removed), Configuration (settings now in place), Open design notes (test order per AC, `NativeAccessTest` as a plain test without a story ID, open red-gate regression question).
- Stubs unchanged; `./gradlew :file-scanner:compileJava :cli:compileJava :cli:bootJar` passes.
- Flagged for the gate: pre-existing tests that form duplicate sets reach the stubs and `RedCheck` counts them as regressions (see design.md and learnings.md).
- Expected commit: `feat(file-scanner): rework DFS-026 design for the red gate [DFS-026]` (orchestrator commits).

## tests (test-author, attempt 1) — 2026-10-14

- Rewrote the FileIdentity fakes (HardLinkDetectorTest, ScanPipelineTest, StageDropReportingTest) and NioFileIdentityTest to keys; added DFS-026 story tests, NativeAccessProbe (child JVM main), cli NativeAccessTest (plain, green).
- Red gate: story tests fail with the DFS-026 stub; ~24 existing end-to-end/cli tests fail indirectly (scan status FAILED caused by the stub), accepted by the gate decision.
- Expected commit: test(file-scanner): add DFS-026 tests [DFS-026]

## implement (implementer, attempt 1) — 2026-10-14

- `HardLinkDetector.regroup`: one `identity.key` per file in path order, grouped in a `LinkedHashMap` (copies in first-link order); `IOException` -> `onDrop` `UNREADABLE` "hard-link check failed: ..."; fewer than two copies -> set dropped.
- `NioFileIdentity.key`: `requireNonNull`; Windows + default file system -> `WindowsFileId.key`; else `fileKey()`, `IOException("no file key: <path>")` when null.
- `WindowsFileId.key`: FFM `CreateFileW` (FILE_READ_ATTRIBUTES, share all, BACKUP_SEMANTICS, `\?\`/`\?\UNC\` prefix) + `GetFileInformationByHandleEx(FileIdInfo)` + `CloseHandle` in `finally`, `GetLastError` captured; Win32 2/3 -> `NoSuchFileException`, 5 -> `AccessDeniedException`, else `FileSystemException`. Handles linked lazily in a holder that keeps an `IllegalCallerException`; every call then throws `IllegalStateException` naming `--enable-native-access`. Under `--illegal-native-access=deny` the refusal comes from `SymbolLookup.libraryLookup` (link time); downcalls map it too.
- CLAUDE.md: decision 5's invariant wording and the native-access sentence in Commands. The `FileTreeWalker` paragraph and Current state did not name `isSameFile`, so they are unchanged.
- Added `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/NioFileIdentityKeyBenchmark.java` (new manual benchmark, plain `main`, no `@Test`, as `SizeIndexHeapBenchmark`; no existing test file changed). Measured 2026-10-14 on this Windows machine, JDK 25.0.4.1, 10,000 files of 4,096 bytes in a temp dir: pass 1 374-378 ms (~37.6 us/file, linking included), pass 2 209-213 ms (~21 us/file); 10,000 distinct keys.
- Real run (decision 6), synthetic folder regenerated (7,993 identical 4,096-byte files `._f00000`..`._f07992` in `d00`..`d49`, scratch dir on C:): `--format=text --progress=log`, exit 0; `Hashing: ... 0:04`, `Hard links: 1 / 1 sets  0:04`, `Done in 0:04: 7,993 files found, 7,993 compared, 1 duplicate sets`; `Summary: 1 sets, 7,993 files, 31.2 MB reclaimable, 0 skipped` (same as baseline). Hard-link phase under 1 s (baseline 20:55). Stderr carries only Gradle's own `java.lang.System` restricted-method warning, none from the scanner.
- `./gradlew build` green: file-scanner 191 tests (2 skipped: DFS-026.AC-3 on Windows, case-sensitivity), cli 63 tests, 0 failures. No `not implemented:` left under `src/main`.
- Expected commit: `fix(file-scanner): detect hard links by file key in linear time [DFS-026]`
