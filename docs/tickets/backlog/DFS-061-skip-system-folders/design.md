# DFS-061: Skip known operating-system folders in scans — design

## Summary

The walker leaves out plain directories below a scan root whose name is a known operating-system folder, reporting each once as `Skipped(dir, SYSTEM_FOLDER, "operating-system folder, not scanned")`. A per-call switch on the port turns this off; `ScanPipeline` fills it from `ScanConfig.defaultExcludes()`. No new use case, no new exported type besides one enum constant; `cli` main is unchanged.

- **AC-1** (names, any depth, Windows names case-insensitive): `SystemFolderNames.matches(String)` (new, package-private, `infra.adapter`, stub), called from `NioFileTreeWalker`'s three-argument `walk` (stub).
- **AC-2** (one `SYSTEM_FOLDER` entry, files not found or counted): the walker emits `Skipped` and returns `SKIP_SUBTREE`; `ScanPipeline` already routes `Skipped` to the result and never sees the files. Reached through `NioFileTreeWalker.walk(Path, boolean, WalkListener)` (stub).
- **AC-3** (`defaultExcludes` false walks them; defaults on): `ScanPipeline.walkLocations` now passes `config.defaultExcludes()` to `walker.walk(root, skipSystemFolders, listener)` (wiring written at design), so every scan reaches the walker stub.
- **AC-4** (a root inside or named like a system folder is walked): only directories other than the walk's start are tested, as for `QUARANTINE`; same stub.
- **AC-5** (`QUARANTINE` always, never reported as `SYSTEM_FOLDER`): quarantine check stays unconditional and runs first; same stub.
- **AC-6** (plain directories only; a link keeps `SYMLINK`): the name check sits after the `SPECIAL_DIRECTORY` check in `preVisitDirectory`, `visitFile` is unchanged; same stub.

Placement: `SkipReason.SYSTEM_FOLDER` in `domain.model` (already exported); `FileTreeWalker` gains abstract `walk(Path, boolean skipSystemFolders, WalkListener)`; the two-argument `walk` becomes a default meaning "skip on". No `module-info.java` change.

Checklist items not applicable: 1-5, 8-12, 16 (no IDs, results, validation, lifecycle, callbacks, executor or held resources change), and 7 (no new progress event).

Expected breakage (every scan, through the real walker or the old fakes, now hits the stub; 173 existing tests red): `ScanCommandTest`, `ScanRunnerTest`, `ScanServiceRealThreadTest`, `ScanProgressSnapshotTest`, `ScannerFactoriesTest`, `ScannerAutoConfigurationTest`, `ScanApiTest`. The other red classes (`ScanPipelineTest`, `ScanFolderTotalsTest`, `ScanPipelineEndToEndTest`, `ScanQuarantineEndToEndTest`, `CliApplicationTest`, `LaunchScriptTest`, `OverlapApiTest`, `PlanApiTest`, `ResultApiTest`) fail with the stub message directly.

Look closely at: the port keeps a two-argument default (skip on) so the existing `NioFileTreeWalkerTest` stays as it is; the test-author must call the three-argument overload in every new walker test, and the AC-3 pipeline test must use the real walker, not a fake (see "For the test-author").

No tests read.

## Public API

- `SkipReason` in `domain.model`: gains `SYSTEM_FOLDER` (after `QUARANTINE`).
- `FileTreeWalker` in `infra.port` (not exported):
  - `void walk(Path root, boolean skipSystemFolders, WalkListener listener) throws IOException` — the one abstract method once implemented. At design it is a `default` that throws the stub, so the two existing test fakes still compile; the implementer removes the default body.
  - `default void walk(Path root, WalkListener listener) throws IOException` — `walk(root, true, listener)`: the default setting, as `ScanConfig.defaults()`. Kept so existing walker tests and fakes need no change beyond decision 6's fakes.
- `NioFileTreeWalker` in `infra.adapter`: overrides the three-argument `walk` (stub); constant `SYSTEM_FOLDER_DETAIL = "operating-system folder, not scanned"`. Its pre-DFS-061 two-argument override stays at design and is deleted by the implementer.
- `SystemFolderNames` in `infra.adapter` (package-private, final, no instances): `static boolean matches(String name)` (stub).

## Layer placement

- `domain.usecases.command` / `domain.usecases.query` (exported): none.
- `domain.model` (exported): `SkipReason.SYSTEM_FOLDER`; the implementer also updates `ScanConfig`'s `@param defaultExcludes` Javadoc to say it covers the folders too.
- `application`: `ScanPipeline.walkLocations` takes `boolean skipSystemFolders` and passes it to the walker (done at design).
- `infra.port` / `infra.adapter`: `FileTreeWalker` (changed), `NioFileTreeWalker` (changed), `SystemFolderNames` (new).

## Behavior (the contract the implementer follows)

`SystemFolderNames.matches(name)`:
1. `Objects.requireNonNull(name, "name")`.
2. True if `name.equalsIgnoreCase` one of `$RECYCLE.BIN`, `System Volume Information`, `Recycled`, `RECYCLER`.
3. True if `name.equals` one of `.Trashes`, `.Spotlight-V100`, `.fseventsd`, `.TemporaryItems`, `.DocumentRevisions-V100`, `.Trash`, `lost+found`.
4. True if `name` starts with `.Trash-` and the rest is one or more ASCII digits `0-9` (no regex needed; `.Trash-` alone and `.Trash-abc` are false).
5. Otherwise false. So `my.Trash`, `lost+found2`, `Trash`, `.trashes`, `LOST+FOUND` are false.

`NioFileTreeWalker.walk(root, skipSystemFolders, listener)`:
1. `resolveRoot(root)` as today (link roots resolved, `NotDirectoryException` etc. unchanged).
2. `Files.walkFileTree(start, new Visitor(start, listener, skipSystemFolders))`.
3. `preVisitDirectory(dir, attrs)`, in this order: (a) `dir` not the start and `SPECIAL_DIRECTORY` → `Skipped(SPECIAL_FILE)` as today; (b) `dir` not the start and `isQuarantine(dir)` → `Skipped(dir, QUARANTINE, QUARANTINE_DETAIL)`, `SKIP_SUBTREE`, regardless of the switch (AC-5); (c) `skipSystemFolders`, `dir` not the start and `SystemFolderNames.matches(dir.getFileName().toString())` → `Skipped(dir, SYSTEM_FOLDER, SYSTEM_FOLDER_DETAIL)`, `SKIP_SUBTREE` (AC-1, AC-2); (d) otherwise `DirectoryEntered`, `CONTINUE`. A skipped system folder emits no `DirectoryEntered`, so it is not counted in `DirectoryWalked` either. Because only directories below the start are tested, the root and its parents are never matched (AC-4); a matching folder below a root named like one is still skipped.
4. `visitFile` is unchanged: a regular file named `lost+found` is a `FileFound`, a symlink named `.Trashes` is `Skipped(SYMLINK)` (AC-6).
5. Delete the two-argument override so the port's default (`skipSystemFolders = true`) applies; make the port's three-argument method abstract.

`ScanPipeline`: unchanged beyond the design-time wiring. A `SYSTEM_FOLDER` `Skipped` goes through `sink` like any other, so it lands in `ScanResult.skipped`, the JSONL `skipped` line, the text report and the summary's `skipped` count; files under the folder are never `FileFound`, so `FileWalked` (the progress "found" figures), `SizeIndex` and `FolderCounter` never see them (AC-2). `excluded` stays the file-name count only.

Stub each criterion reaches: every criterion reaches `NioFileTreeWalker.walk(Path, boolean, WalkListener)`, directly or through `ScanPipeline`; AC-1 can also reach `SystemFolderNames.matches`.

### For the test-author

- Every new walker test calls the three-argument `walk(root, skipSystemFolders, listener)` on `NioFileTreeWalker` explicitly. The two-argument form runs the pre-DFS-061 code until implementation and would pass at red.
- AC-3's "core setting reaches the walker" runs `ScanPipeline` (or the scan use cases from `ScannerFactories`) with the real `NioFileTreeWalker` over a `@TempDir`, not with a fake: a fake that records the flag passes at red, since the wiring already exists.
- Change `ScanPipelineTest.FakeFileTreeWalker` and `ScanFolderTotalsTest.FakeWalker` to override `walk(Path, boolean, WalkListener)` instead of `walk(Path, WalkListener)` (decision 6). Their existing tests fail with the stub until then, and pass afterwards.
- Name-table tests (the two Scenario Outlines) can be walker tests over one folder per name, or `SystemFolderNames.matches` tests in the same package; both are red on a stub. Keep at least one walker test per outline row type (Windows case variant, exact macOS/Linux name, `.Trash-<digits>`).
- Windows forbids no name in the tables; `$` in a path is literal in Java. The AC-1 lower-case scenario creates `$recycle.bin` and `system volume information` as separate folders, so it runs on every platform. AC-6's symlink test uses `Assumptions` when `createSymbolicLink` is refused, as existing tests do.
- AC-5 goes in `ScanQuarantineEndToEndTest` (scan with `defaultExcludes` true and false); the CLI scenarios go in `ScanCommandTest`.
- The progress scenario (`--progress=log`, "1 files, 6.0 KB found") goes in `ScanCommandTest` too.

## State and transitions

Not applicable: synchronous, stateless walk.

## Concurrency and atomicity

Not applicable: synchronous, stateless walk. The switch is a per-call argument; `NioFileTreeWalker` stays a stateless shared bean (decision 6).

## Checklist disposition

1. Unknown or stale IDs: not applicable: no ID-taking operation is added or changed.
2. Results as values, bugs as exceptions: not applicable: no new `Result` or `ErrorCode` (decision 4: no error for a location inside a system folder); `matches(null)` throws `NullPointerException`.
3. Status model: not applicable: no lifecycle change.
4. Races and who wins: not applicable: nothing shared is mutated.
5. Validation before side effects: not applicable: `StartScan` validation is unchanged; no new refusal (decision 4).
6. State placement: the switch is a per-call argument, not a field of the shared walker (decision 6).
7. Progress and observation: not applicable: no new event; a skipped folder is an existing `ScanEvent.Skipped` and its files are never `FileWalked`.
8. Caller-supplied callbacks: not applicable: the listener contract is unchanged.
9. Failures off the caller's thread: not applicable: unchanged.
10. Query results: not applicable: no query changes; `ScanResult.skipped` simply gains entries.
11. Executor contract: not applicable: unchanged.
12. Retention: not applicable: no state kept.
13. Testability: walker tests on a `@TempDir`, no timing; the stub is the three-argument walk (see "For the test-author").
14. Exported surface: only `SkipReason.SYSTEM_FOLDER` in the already exported `domain.model`; `FileTreeWalker` is in unexported `infra.port`; no `module-info.java` change.
15. Port fakes: `ScanPipelineTest.FakeFileTreeWalker` and `ScanFolderTotalsTest.FakeWalker` implement `FileTreeWalker`; listed above. The two-argument `walk` becomes a default so they compile through the transition.
16. Held resources: not applicable: none held.
17. Side-effect order: the system-folder check comes after the special-directory and quarantine checks and before `DirectoryEntered`; nothing else moves.

## Configuration

- `ScanConfig.defaultExcludes` (default `true` in `ScanConfig.defaults()`, `false` with `--no-default-excludes` and with the server's `"defaultExcludes": false`): now also switches the folder skip, enforced in `NioFileTreeWalker` via the argument `ScanPipeline` passes. No new setting.

## Expected breakage

- `ScanCommandTest`: every scan now reaches the walker stub, so the CLI exits with an error and assertions on output and exit codes fail.
- `ScanRunnerTest`: the CLI scan fails on the stub, so the exit code is 1 instead of 0.
- `ScanServiceRealThreadTest`: the scan ends FAILED instead of COMPLETED.
- `ScanProgressSnapshotTest`: the scan fails on the stub before the expected events are sent.
- `ScannerFactoriesTest`: scans through the factories end FAILED.
- `ScannerAutoConfigurationTest`: scans through the auto-configured beans end FAILED.
- `ScanApiTest`: server scans end FAILED, so status, stream and settings assertions fail.

## Open design notes (as of the design phase)

- The two-argument `walk` is kept as a default ("skip on") rather than removed, so the many existing `NioFileTreeWalkerTest` calls stay unchanged; decision 6 left the signature to the architect.
- `ScanPipeline`'s one-line wiring was written at design, not left to the implementer: without it AC-3's tests would pass at red against the old walk.
- `BrowserFlowTest` (`:server:e2eTest`, not part of `test`) breaks the same way until implementation; it must pass again at verify, since the ticket changes the scan flow.
- The implementer also updates `CLAUDE.md` (the quarantine invariant entry, Current state, and the `--no-default-excludes` comment in Commands) and `ScanConfig`'s `defaultExcludes` Javadoc.
- `NioDirectoryReader` (the directory listing of DFS-032) does not mark system folders; out of scope.

## Stubs (as of the design phase)

- Files stubbed: `FileTreeWalker.walk(Path, boolean, WalkListener)` (default body), `NioFileTreeWalker.walk(Path, boolean, WalkListener)`, `SystemFolderNames.matches(String)`.
- Each throws `new UnsupportedOperationException("not implemented: DFS-061")` per D2.
- Commit: filled in after the design commit.
