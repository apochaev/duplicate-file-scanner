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
