# DFS-052: Skip quarantine folders when scanning — design

## Summary

Two name checks, no new port or state. `NioFileTreeWalker` prunes any plain directory below the root named exactly `.dfs-quarantine` and reports it once as `Skipped(dir, QUARANTINE, "quarantine folder, not scanned")`; `StartScan` (`ScanService`) refuses a location with that name among its absolute path's elements as `Failure(LOCATION_IN_QUARANTINE)`, before any disk lookup. The name is one constant, `QuarantineFolder.NAME` in `domain.model`; `NioQuarantineDeleter.QUARANTINE_FOLDER` now points to it.

- **DFS-052.AC-1** (prune at any depth, reported once): `NioFileTreeWalker.Visitor.preVisitDirectory` → stub `isQuarantine(Path)`, then `SKIP_SUBTREE`.
- **DFS-052.AC-2** (exact name, plain directories only): same stub; the `SPECIAL_DIRECTORY` check stays first and links go to `visitFile`, so junctions and symlinks keep `SPECIAL_FILE`/`SYMLINK`. Each scenario has a plain directory `A`, so every walk reaches the stub.
- **DFS-052.AC-3** (scan leaves quarantined files out, also with `defaultExcludes` off): no pipeline change; reaches the walker stub through the pipeline, or the `ScanService` stub first through `StartScan`.
- **DFS-052.AC-4** (refusal by path names, before disk): `ScanService.begin` → stub `firstInQuarantine(List<Path>)`, one pass over all locations after `NO_LOCATIONS` and before the resolve loop; message `location is in a quarantine folder: <location>`.
- **DFS-052.AC-5** (CLI JSONL/text skipped line; refusal on stderr, stdout empty): no CLI source change; reaches the `ScanService` stub, as `ScanCommandTest` already does. **Deviation:** the ticket says exit 2, but a refused start exits `ScanCommand.ERROR` = 1 (DFS-020.AC-13; 2 is `USAGE`), and `cli/src/main` is outside `touches`; the design keeps exit 1 (open note 6).
- **DFS-052.AC-6** (server `400 LOCATION_IN_QUARANTINE`): no server source change; reaches the `ScanService` stub, as `ScanApiTest` already does.

Placement: `QuarantineFolder`, `SkipReason.QUARANTINE`, `ErrorCode.LOCATION_IN_QUARANTINE` in `domain.model` (already exported, `module-info.java` unchanged); `StartScan` Javadoc in `domain.usecases.command`; stub in `application.ScanService`; stub in `infra.adapter.NioFileTreeWalker`.

Not applicable: checklist items 1, 3, 4, 6, 8, 9, 10, 11 and 12 (no ID, state, lifecycle, executor or query change).

Expected breakage: `LaunchScriptTest` (child-process script, fails on exit code). All other broken classes fail directly on the stub.

Look closely at: open notes 6 (exit 1, not the ticket's 2), 1 (a link location resolving into a quarantine is not refused) and 3 (quarantine refusal wins over an earlier missing location).

No tests read.

## Public API

- `QuarantineFolder` in `domain.model` (new): `public static final String NAME = ".dfs-quarantine"`, private constructor. The one shared constant (decision 4); not stubbed (a constant, no behavior).
- `SkipReason` in `domain.model`: new last value `QUARANTINE`.
- `ErrorCode` in `domain.model`: new value `LOCATION_IN_QUARANTINE` (after `LOCATION_IS_LINK`).
- `StartScan` in `domain.usecases.command`: signatures unchanged; its Javadoc documents the new failure and its place in the check order.
- `NioQuarantineDeleter.QUARANTINE_FOLDER` (infra.adapter, unchanged public field): now defined as `QuarantineFolder.NAME`, same value, so existing references still compile.

No first-slice type is replaced. No new package, so `module-info.java` is unchanged.

## Layer placement

- `domain.usecases.command` (exported): `StartScan` (Javadoc only).
- `domain.model` (exported): `QuarantineFolder`, `SkipReason.QUARANTINE`, `ErrorCode.LOCATION_IN_QUARANTINE`.
- `application`: `ScanService.firstInQuarantine(List<Path>)`, a private static stub, called in `begin`.
- `infra.port` / `infra.adapter`: no port change. `NioFileTreeWalker.Visitor.isQuarantine(Path)`, a private static stub, and the constant `NioFileTreeWalker.QUARANTINE_DETAIL`; `NioQuarantineDeleter` uses `QuarantineFolder.NAME`.

## Behavior (the contract the implementer follows)

**`NioFileTreeWalker` (AC-1, AC-2; stub `Visitor.isQuarantine`).** `preVisitDirectory(dir, attrs)` keeps its order: (1) a non-root `SPECIAL_DIRECTORY` (junction, reparse point) is `Skipped(dir, SPECIAL_FILE, ...)` and `SKIP_SUBTREE` as before, so a junction named `.dfs-quarantine` stays `SPECIAL_FILE`; (2) new, already wired: a non-root plain directory for which `isQuarantine(dir)` is true emits `Skipped(dir, SkipReason.QUARANTINE, QUARANTINE_DETAIL)` (`"quarantine folder, not scanned"`) and returns `SKIP_SUBTREE`, or `TERMINATE` when the listener returns `false`; no `DirectoryEntered` is emitted for it; (3) otherwise `DirectoryEntered` as before. `isQuarantine(dir)` is `dir.getFileName() != null && dir.getFileName().toString().equals(QuarantineFolder.NAME)`: exact, case-sensitive, no disk access. The walked root itself is never pruned (`StartScan` refuses such a root, below). `visitFile` is unchanged: a regular file named `.dfs-quarantine` is yielded, a symlink of that name is `SYMLINK` (walkFileTree without `FOLLOW_LINKS` hands links to `visitFile`).

**`ScanPipeline` / `ScanResult` (AC-3).** No change: the walker's `Skipped` event already becomes a `ScanResult.skipped` entry and a `ScanEvent.Skipped`; nothing under the pruned folder is yielded, so no folder total, set or `excluded` count sees it. `DefaultExcludeFilter` is not involved, so `defaultExcludes` off changes nothing. AC-3 tests reach the walker stub through the pipeline (or the `ScanService` stub first, when they go through `StartScan`).

**`ScanService.begin` (AC-4; stub `firstInQuarantine`).** Order: null checks (NPE) → empty list `Failure(NO_LOCATIONS)` → new, already wired: `firstInQuarantine(locations)`; when present, `Failure(LOCATION_IN_QUARANTINE, "location is in a quarantine folder: " + <location as given>)` → the existing resolve loop (`LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY`, `LOCATION_UNREADABLE`) → record and submit. `firstInQuarantine` walks the list in order and returns the first location whose `toAbsolutePath().normalize()` has any name element (`for (Path name : abs)`) whose `toString()` equals `QuarantineFolder.NAME`. It is one pass over all locations before any is resolved, so it touches no disk, a non-existent `.dfs-quarantine/missing` is refused with this code rather than `LOCATION_NOT_FOUND`, and a quarantine location later in the list wins over a missing one earlier. A refused start creates no record and no ID.

**CLI (AC-5) and server (AC-6).** No source change. `ScanCommand` prints `error: <code>: <message>` on stderr and returns `ScanCommand.ERROR` (exit 1, not 2: open note 6) for a `Failure` from `start`, stdout untouched; `ScanCommand` and `TextReport` print `reason().name()` for skipped entries. `ScanController` maps core error codes to `400 {"code", "message"}`.

### For the test-author

- Fixtures in a `@TempDir`: `A/photo.jpg` (2 MB, generated in code) and `.dfs-quarantine/run-1/A/photo.jpg` with the same bytes; create the quarantine copy at test time (never commit it under `fixtures/`).
- AC-1, AC-2: `NioFileTreeWalker` directly with a collecting `WalkListener`. Every scenario has at least one non-root plain directory (`A`), so each walk reaches `isQuarantine` and fails red on it. Assert the detail by `NioFileTreeWalker.QUARANTINE_DETAIL` (package-private; the walker test is in `infra.adapter`) or only the reason. The link case: create a symbolic link with `Files.createSymbolicLink` (skip by `Assumptions` when refused); on Windows a junction (`cmd /c mklink /J`), skipped where the platform refuses; expect `SYMLINK` or `SPECIAL_FILE`, never `QUARANTINE`.
- AC-3: through `ScannerFactories`/`StartScan` with a real executor and a bounded wait, or the pipeline; assert `sets()` empty, no `FolderTotal` path at or under `<tmp>/.dfs-quarantine`, `skipped` holding `<tmp>/.dfs-quarantine` with `QUARANTINE`, `excluded` 0; repeat with `defaultExcludes` false.
- AC-4: `ScanService` through the seam constructor with a `LocationResolver` fake that fails the test (or counts calls) when called, to prove no disk lookup, and a repository you can inspect for "no scan created"; the four outline rows plus the `.DFS-Quarantine/C` row (expect `Success`, or at least not `LOCATION_IN_QUARANTINE`).
- AC-5: the existing CLI test style (in-process `ScanCommand`/application run with captured stdout/stderr); JSONL `skipped` line `"reason":"QUARANTINE"`, text report line `<path>  QUARANTINE` under `Skipped (1):`, summary skipped 1; refused location: exit code `ScanCommand.ERROR` (1, the code every refused start uses, DFS-020.AC-13; open note 6), stderr containing the location and `LOCATION_IN_QUARANTINE`, stdout empty.
- AC-6: in `ScanApiTest`, `POST /api/scans` with `["<tmp>/.dfs-quarantine/run-1"]` → `400`, `code` `LOCATION_IN_QUARANTINE`; `GET /api/scans` lists no new scan.
- DFS-030.L-2: also search the test trees for assertions on the full list of `SkipReason` values or `ErrorCode` values (for example a test iterating `values()`), and update any that break.

## State and transitions

Not applicable: no new state. `StartScan`'s refusal happens before a record exists, so the existing scan lifecycle (DFS-018) is unchanged.

## Concurrency and atomicity

Not applicable: both checks are pure functions of path names, run on the caller's thread (`StartScan`) or the scanning thread (the walker), with no shared state.

## Checklist disposition

1. Unknown or stale IDs: not applicable: no operation taking an ID changes.
2. Results as values, bugs as exceptions: a location in a quarantine is a user-fixable input, so `Failure(LOCATION_IN_QUARANTINE)` naming the location; a null list or element is still an NPE (checked before).
3. Status model: not applicable: no lifecycle change; a refused start never creates a record.
4. Races and who wins: not applicable: no shared state touched by the new checks.
5. Validation before side effects: the quarantine check runs on the caller's thread before the resolve loop and before any record, ID or task; it uses only path names, so it throws nothing besides the `toAbsolutePath` `IOError`/`SecurityException` cases, which are bugs-as-exceptions as before. The walker does not re-check locations; a location that is a link resolving into a quarantine is not refused (open note 1).
6. State placement: not applicable: no per-request state; `ScanService` gains no field.
7. Progress and observation: the prune is pushed as the existing `ScanEvent.Skipped` and recorded in `ScanResult.skipped`; no snapshot field changes.
8. Caller-supplied callbacks: not applicable: unchanged; the walker's listener contract (return `false` to stop) applies to the new `Skipped` event as to the others.
9. Failures off the caller's thread: not applicable: the prune cannot fail (a name comparison); walker failures are handled as before.
10. Query results: not applicable: no query changes; `GetScanResult`'s `skipped` simply carries the new reason.
11. Executor contract: not applicable: refusal is before submission; unchanged otherwise.
12. Retention: not applicable: no new state.
13. Testability: all pure, no timing; AC-4 pins "no disk lookup" with a resolver fake through the existing `ScanService` seam constructor; the walker tests use a `@TempDir`.
14. Exported surface: `QuarantineFolder`, `SkipReason.QUARANTINE`, `ErrorCode.LOCATION_IN_QUARANTINE`, all in the already exported `domain.model`; `StartScan` Javadoc in the exported `domain.usecases.command`. `module-info.java` unchanged.

## Configuration

- None. Always on, not tied to `defaultExcludes` or `ExecutionConfig.quarantineAnchors` (decisions 1, 2).

## Expected breakage

- `LaunchScriptTest`: runs the installed `dfs` script in a child process; the stub ends that JVM with a nonzero exit code, so the test fails on its exit-code assertion and the stub text appears only in captured stderr.

Every other class that fails at design (`ScanServiceTest`, `ScanServiceRealThreadTest`, `ScannerFactoriesTest`, `ScanProgressSnapshotTest`, `ScanPipelineEndToEndTest`, `NioFileTreeWalkerTest`, `NioProgressTest`, `ScannerAutoConfigurationTest`, `ScanCommandTest`, `ScanRunnerTest`, `CliApplicationTest`, `ScanApiTest`, `ResultApiTest`, `OverlapApiTest`) fails directly with `UnsupportedOperationException: not implemented: DFS-052` (checked in each class's test-results XML), so needs no entry.

## Open design notes (as of the design phase)

1. A location that is itself a symlink or junction whose target lies in a quarantine is not refused: decision 3 checks the names of the path as given, with no disk access. The walker resolves such a root and walks it, and never prunes the root itself. Rare (the user must create such a link) and outside the ticket's rule; noted, not handled.
2. The check uses `toAbsolutePath().normalize()`, so `<tmp>/.dfs-quarantine/..` (which names `<tmp>`) is not refused; `normalize` is name-only and keeps "no disk access".
3. All locations are checked for quarantine before any is resolved (one pass), so `LOCATION_IN_QUARANTINE` takes precedence over a `LOCATION_NOT_FOUND` earlier in the list. The ticket says "checked before the location is looked up on disk"; this is the stricter reading.
4. `NioQuarantineDeleter.QUARANTINE_FOLDER` is kept (now `= QuarantineFolder.NAME`) instead of removed, so references in DFS-051's tests keep compiling; it was changed at design since the value is identical and nothing can break. The implementer may switch the deleter's own use to `QuarantineFolder.NAME`.
5. CLAUDE.md edits (implementer): add `QUARANTINE` to the walker's skip reasons in "Provisional port sketch" (`WalkEvent.Skipped(path, SkipReason, detail)` list), and add a safety invariant: scans never enter a directory named exactly `.dfs-quarantine` (reported as skipped `QUARANTINE`) and refuse a location inside one (`LOCATION_IN_QUARANTINE`).
6. **Exit code deviation.** Decision 3 and AC-5 say the CLI "exits 2" on a location in a quarantine, and the Scope says "no CLI source change". Both cannot hold: `ScanCommand` returns `ERROR` (1) for every `Failure` from `StartScan` (DFS-020.AC-13 asserts it for `LOCATION_NOT_FOUND` and `NOT_A_DIRECTORY`), and 2 is `USAGE` (bad arguments). The design keeps the existing contract, exit 1, since changing it would need `cli/src/main` (outside `touches`) and make this one refusal differ from the others. The product-owner review should confirm; if exit 2 is really wanted, the ticket needs `cli/src/main/java/us/pochaev/dfs/cli/ScanCommand.java` in `touches`.

## Stubs (as of the design phase)

- Files stubbed: `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/ScanService.java` (`firstInQuarantine`), `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/adapter/NioFileTreeWalker.java` (`Visitor.isQuarantine`).
- Each throws `new UnsupportedOperationException("not implemented: DFS-052")` per D2.
- Commit: to be filled in after the design commit.
