# DFS-061 verification

## Product owner review

### What you can do now

Scan a whole drive without recycle-bin or system-folder files showing up as duplicates. Folders like `$RECYCLE.BIN`, `System Volume Information`, `.Trashes` and `lost+found` are left out, and each one is listed once in the report as skipped with reason `SYSTEM_FOLDER`. `--no-default-excludes` scans them again.

### Acceptance criteria

- **DFS-061.AC-1** met: all 13 names are skipped at any depth. The Windows names match in any letter case, the macOS and Linux names only when spelled exactly, and look-alikes (`my.Trash`, `lost+found2`, `.Trash-abc`, `LOST+FOUND`) are still scanned.
- **DFS-061.AC-2** met: each skipped folder appears once as a `skipped` entry (`SYSTEM_FOLDER`, the folder path, "operating-system folder, not scanned") in the JSONL and the text report, and is counted in the summary's `skipped`. Files under it are not found, counted or hashed.
- **DFS-061.AC-3** met: `--no-default-excludes` (or `defaultExcludes=false`) scans the folders and reports no `SYSTEM_FOLDER`. The skip is on by default.
- **DFS-061.AC-4** met: a scan location that is a system folder, or sits inside one, is walked. A system folder below it is still skipped.
- **DFS-061.AC-5** met: `.dfs-quarantine` is still skipped as `QUARANTINE` whether the new skip is on or off, and it is never reported as `SYSTEM_FOLDER`.
- **DFS-061.AC-6** met: a regular file named `lost+found` is scanned, and a symbolic link named `.Trashes` keeps its `SYMLINK` reason. The symlink tests ran on this machine and were not skipped.

### Try it

```
./gradlew -q :cli:run --args="D:/ --format=text"
```
Look for a line `    D:\$RECYCLE.BIN  SYSTEM_FOLDER  operating-system folder, not scanned` in the skipped section, and check that no `$R...` file appears in any set. Add `--no-default-excludes` and the folder is scanned again. **The manual done-item (a real scan of a Windows drive root) has not been done.** The verifier cannot do it, so it is left unticked for you. It is the one open item before closing.

### Decided without you

- The port keeps its old two-argument `walk(root, listener)` as a default that means "skip on". Only the scan pipeline calls the walker, and it always passes the setting explicitly.
- The name table is a separate package-private class, `SystemFolderNames`, in `infra.adapter`.
- The skip is checked after the special-directory (junction) check and after the quarantine check. A junction named `$RECYCLE.BIN` is therefore still reported as `SPECIAL_FILE`.
- Tests: the AC-5 core test went into a new `SystemFolderEndToEndTest` instead of `ScanQuarantineEndToEndTest`. The progress scenario checks "1 files, 15 B found" (the test's 15-byte content) instead of "6.0 KB". The two Scenario Outlines run at walker level, not through the CLI. The lower-case Windows scenario runs one folder per test row instead of both folders in one scan.

### Known limits

- `Thumbs.db`, `desktop.ini`, `ehthumbs.db` and `.localized` are still scanned (decision 5).
- A user folder named exactly `.Trash` or `lost+found` is skipped too, unless you use `--no-default-excludes` (accepted in decision 1).
- The server's folder browser (`NioDirectoryReader`) does not mark system folders (out of scope).
- The manual Windows drive-root check is still to do (see above).

### Evidence

- `./gradlew build` green.
- From the JUnit XML: `SystemFolderWalkerTest` 61 tests, 0 skipped. `SystemFolderEndToEndTest` 5 tests, 0 skipped. `ScanCommandTest` 89 tests, 1 skipped, which is the older DFS-029.AC-3 test, not a DFS-061 one; its 10 DFS-061 tests all passed.
- `./gradlew :server:e2eTest` (`BrowserFlowTest`): 8 tests, 0 failures, 0 skipped. Run because the ticket changes the scan flow.
- Deterministic gate at implement: green 1049 passed, 3 skipped; freeze, touches, stubs, traceability and consistency all PASS.

### How it went

- Design: 1 attempt (opus).
- Tests: 2 attempts. The first failed the red gate for two reasons. Five broken test classes had not been declared, so that went back to the architect, who amended `design.md`. The 10 CLI tests failed with an assertion error rather than the stub's exception, so that went back to the test-author, who added a `sysScan` rethrow helper.
- Implement: 1 attempt, gate green.
- Both defects are closed. No escalations.

Ready for your approval once you have done the manual Windows drive-root check (the one unticked done-item).

## Summary

- **DFS-061.AC-1** met: `SystemFolderNames` matches the 13 names; Windows names are case-insensitive and the rest exact. Walker tests cover every name, depth, case variants and look-alikes; the name-table tests cover 16 positives and 11 negatives.
- **DFS-061.AC-2** met: one `Skipped(dir, SYSTEM_FOLDER, detail)` per folder plus `SKIP_SUBTREE`, so nothing under it is entered. Proven in the JSONL, text, summary-count and `--progress=log` tests (`ScanCommandTest`) and by the count of `FileWalked` events (`SystemFolderEndToEndTest`).
- **DFS-061.AC-3** met: `ScanPipeline` passes `config.defaultExcludes()` to the walker. Proven with the real walker through the pipeline, the CLI flag and the walker switch.
- **DFS-061.AC-4** met: only directories other than the walk's start are tested. Covered at walker and CLI level.
- **DFS-061.AC-5** met: the quarantine check comes first and does not depend on the switch. Covered at walker, pipeline and CLI level, with the switch on and off.
- **DFS-061.AC-6** met: the check sits in `preVisitDirectory` after the special-directory check, and `visitFile` is unchanged. The regular-file and symlink tests passed (not skipped).
- Build green, e2e green. The design is followed, including every checklist disposition. No safety-invariant concern.
- Minor: leftover test scaffolding (the `sysScan` stub rethrow, an unused `createCase` parameter). It does no harm.
- Ready for the human gate. The manual Windows drive-root done-item is still open for the user.

## Criteria

- **AC-1: met.** `SystemFolderNames.matches` follows design steps 1 to 5 exactly. `SystemFolderWalkerTest` covers each of the 13 names (one walker test each), `deep/er/.Trashes`, four lower-case or mixed-case Windows variants, and eight look-alikes (with `.Trash-12a` added), plus direct name-table tests. `ScanCommandTest` covers depth and a look-alike through the CLI. The Windows-case scenario's two folders are split into separate parameterized rows. They are equivalent, since each row asserts exactly one skip.
- **AC-2: met.** `ScanCommandTest` asserts exactly one `skipped` line with its path, reason and detail, `summary.skipped == 1`, no `set` line, the text report line `    <path>  SYSTEM_FOLDER  ` with the tail `, 1 skipped, 0 excluded`, and that the `Walking:` line ends with `1 files, 15 B found`. Without the skip that line would read 3 files, 31 B, so the assertion discriminates. `SystemFolderEndToEndTest` counts the `FileWalked` events (1) and the folders (only `docs`). The walker test asserts that no `DirectoryEntered` occurs under the skipped folder.
- **AC-3: met.** The pipeline test uses the real `NioFileTreeWalker`, as the design required, so the flag is seen to reach the walker. It also checks `ScanConfig.defaults().defaultExcludes()`. The CLI `--no-default-excludes` test asserts the set holding the `$R...` copy and no skipped lines.
- **AC-4: met.** The walker and the CLI each have both scenarios: a start inside `$RECYCLE.BIN/S-1...`, and a start at `$RECYCLE.BIN` with `System Volume Information` below it still skipped.
- **AC-5: met.** Walker (parameterized on/off), pipeline (on/off, exact skipped counts 2 and 1) and CLI (with and without `--no-default-excludes`).
- **AC-6: met.** A regular file `lost+found` / `.Trash` is found (walker and CLI). A symlink `.Trashes` is reported as `SYMLINK` (walker and CLI). The XML shows neither symlink test was skipped.

## Test quality

- The tests assert on observable output: walk events, `ScanResult`, JSONL fields, text lines and progress lines. None of them restates the implementation.
- The red gate showed 76 story tests failing on the stubs, so none of them is vacuous.
- Negative cases are strong. The look-alike table includes `.Trash-`, `.Trash-abc` and `.Trash-12a`, covering every branch of the digit-suffix rule, and the exact skipped counts guard against double reporting.
- Minor: `ScanCommandTest.sysScan` remains as stub-phase scaffolding (it looks for "not implemented: DFS-061" in the output). It is now a no-op but is dead code.
- Minor: `SystemFolderWalkerTest.assertSkippedOnly` has an unused `createCase` parameter.
- Declared deviations (test-plan.md): the progress byte figure (15 B), and AC-5 placed in `SystemFolderEndToEndTest` instead of `ScanQuarantineEndToEndTest`. Both are acceptable.

## Design conformance

- The public API matches design.md:
  - `SkipReason.SYSTEM_FOLDER` comes after `QUARANTINE`.
  - `FileTreeWalker.walk(Path, boolean, WalkListener)` is abstract, and the two-argument form is a default delegating with `true`.
  - The two-argument override in `NioFileTreeWalker` is removed.
  - `SYSTEM_FOLDER_DETAIL` matches the ticket text.
  - `SystemFolderNames` is package-private, final and has no instances.
- `preVisitDirectory` checks in this order: special directory, then quarantine (unconditional), then system folder (behind the switch and only below the start), then `DirectoryEntered`.
- Checklist dispositions hold:
  - Item 6: the switch is a per-call argument and the adapter stays stateless.
  - Item 14: no `module-info` change.
  - Item 15: both fakes override the three-argument method.
  - Item 17: the check order is as above.
- `ScanConfig` Javadoc and CLAUDE.md are updated: the invariant entry, the Commands comment and Current state.
- No other main-source caller of `walk` exists: `ScanPipeline.walkLocations` is the only one.

## Safety invariants

- **Never delete the last copy / re-verify at execution / preview is pure:** untouched. Skipping more folders can only remove candidates from sets; a set still needs two physical copies.
- **Hard links are not duplicates:** unchanged.
- **No symlink/junction following:** the special-directory check still runs first, so a junction named like a system folder stays `SPECIAL_FILE`. A symlink stays `SYMLINK` (tested).
- **Quarantine left out:** stays unconditional and runs before the new check (AC-5 tested both ways).
- **Empty files excluded:** unchanged. `excluded` still counts file names only.

## Done-items

- [x] Every scenario is an automated test and passes. The Outlines run at walker level; the declared deviations are listed above.
- [x] `./gradlew build` green. `:server:e2eTest` is also green.
- [x] No files changed outside Touches (story-gate touches PASS; the diff confirms main changes only in the listed packages, plus `CLAUDE.md` and the tests).
- [x] CLAUDE.md names the folders and `--no-default-excludes`'s extra effect.
- [ ] Manual Windows drive-root run: not done by the verifier, left for the user.
