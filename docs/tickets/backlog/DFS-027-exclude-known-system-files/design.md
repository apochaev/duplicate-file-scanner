# DFS-027: Exclude known system files by default — design

## Summary

- **Placement:** a new filter `DefaultExcludeFilter` in `application` (not exported); `ScanConfig` and `ScanResult` in `domain.model` each gain one component (already exported, so no `module-info.java` change). No new port, no adapter change. `cli` gains no new type.
- **AC-1** (`.DS_Store` exact, `._*` prefix, case-sensitive): `DefaultExcludeFilter.keep(FileEntry)` checks the last path element with `equals(".DS_Store")` / `startsWith("._")`; `ScanPipeline.walkLocations` calls it before `SizeIndex.add`.
- **AC-2** (`--no-default-excludes`, twice is exit 2): `ScanCommand.run` parses it and builds `ScanConfig` with `defaultExcludes=false`; usage line names it. Today `run` passes `defaults.defaultExcludes()` as a placeholder; it reaches the `ScanConfig.defaults()` stub first.
- **AC-3** (`excluded` in JSONL summary, `, <n> excluded` in text): new `ScanResult.excluded` (non-negative `long`), filled from `DefaultExcludeFilter.excluded()`; `ScanCommand.write` appends `"excluded"` as the last summary key, `TextReport` appends `, <n> excluded` after `skipped`.
- **AC-4** (excluded still counted in `Walking:`): `walkLocations` now emits `ScanEvent.FileWalked` first, then filters (stub wiring already in place).
- **AC-5** (`defaults()` has the flag on; flag off scans everything): `ScanConfig(long, int, long, boolean defaultExcludes)`, `defaults()` = `(1, 65536, 131072, true)`, pinned to `DefaultExcludeFilter.DEFAULT_ENABLED`; a disabled filter keeps every entry.
- **AC-6** (name before size): filter runs before `SizeIndex.add`, which owns `minSize`, so a 0-byte `.DS_Store` counts as excluded; with the flag off it falls to the size filter and is counted nowhere.
- **Not applicable:** checklist items 1 (no accumulation beyond one count), 2 and 4 (passes `FileEntry` through; only the `ScanResult` count invariant), 5, 7, 8, 9, 10, 11, 12.
- **Stubs:** `ScanConfig.defaults()`, `ScanResult`'s compact constructor, `DefaultExcludeFilter`'s constructor, `keep` and `excluded`. No convenience constructors: every existing `new ScanConfig(a, b, c)` / `new ScanResult(s, k)` in tests stops compiling until the test-author updates it (all in `touches`).
- **Expected breakage:** `ScanServiceTest` only (static `CONFIG = ScanConfig.defaults()` fails class initialization, so later tests fail with `NoClassDefFoundError`).

## Public API

- `ScanConfig` in `domain.model`: `record ScanConfig(long minSize, int sampleSize, long partialHashThreshold, boolean defaultExcludes)`; `static ScanConfig defaults()` returns `(1, 65536, 131072, true)`. The flag needs no validation; existing checks unchanged.
- `ScanResult` in `domain.model`: `record ScanResult(List<DuplicateSet> sets, List<WalkEvent.Skipped> skipped, long excluded)`; compact constructor keeps the DFS-005 null checks and copies, and throws `IllegalArgumentException` for `excluded < 0`.
- `DefaultExcludeFilter` in `application`: `public static final boolean DEFAULT_ENABLED = true`; package-private constants `DS_STORE = ".DS_Store"`, `APPLE_DOUBLE_PREFIX = "._"`; `DefaultExcludeFilter(boolean enabled)`, `DefaultExcludeFilter()` (delegates with `DEFAULT_ENABLED`); `boolean keep(FileEntry entry)` (false and count +1 when excluded; NPE on null; a path with no file name is kept); `long excluded()`.
- `ScanPipeline` (changed, not stubbed): builds one `DefaultExcludeFilter(config.defaultExcludes())` per `scan`, passes it to `walkLocations`, and returns `new ScanResult(sets, skipped, excludeFilter.excluded())`.
- `ScanCommand` (cli, to implement): `--no-default-excludes` flag, `"given more than once"` usage error, usage line `... [--progress=live|log|off] [--no-default-excludes]`, summary key `"excluded"` after `"reclaimableBytes"` (purely additive to DFS-020's schema). `TextReport`: last line `Summary: ..., <n> skipped, <n> excluded`.

## Layer placement

- `application`: `DefaultExcludeFilter` (the two names and the matching rule live here, per decision 6); `ScanPipeline` wiring.
- `domain.model` (exported, added to `module-info.java`): no new type; `ScanConfig` and `ScanResult` gain one component each (package already exported).
- `infra.port` / `infra.adapter`: none. The walker keeps yielding every regular file (CLAUDE.md: filtering belongs in `application`).

## Checklist disposition

1. Accumulation and merging: not applicable beyond a count: the filter decides per file as the walk yields it and keeps one `long`; no `merge` (the walk is sequential, one filter per scan).
2. Inter-stage currency invariants: not applicable to the filter (it passes `FileEntry` through unchanged); `ScanResult`'s compact constructor rejects a negative `excluded`.
3. Deterministic output order: the filter keeps the walk's order; it only drops entries, never reorders. `SizeIndex` still defines the downstream order.
4. Immutable results: not applicable to the filter (returns a boolean); `ScanResult` still takes immutable copies of its lists and the count is a primitive.
5. Repeated input: not applicable: `ScanPipeline.keepTopLevel` drops nested/equal start locations before walking, so each file reaches the filter once.
6. Configuration and its defaults: `ScanConfig.defaultExcludes`, `true` in `defaults()`, equal to `DefaultExcludeFilter.DEFAULT_ENABLED` (pinned by `ScanConfigTest`); reaches the filter as a constructor argument built inside `ScanPipeline.scan`, so no caller can skip it.
7. Memory or performance, measured: not applicable: one counter, no per-file state; a name check per file.
8. Groups of one are dropped: not applicable: the filter runs before grouping; `SizeIndex` still drops singletons.
9. Path-order processing: not applicable: reads names only, no content.
10. Failure mid-stage: not applicable: a name comparison does no I/O and cannot fail; nothing goes to the skip sink.
11. New secondary ports: not applicable: no port.
12. Large and near-duplicate fixtures: not applicable: behavior does not depend on content or size.
13. Reused across concurrent scans: the filter (and its count) is a local of one `ScanPipeline.scan` call, never a field; `ScanPipeline` stays stateless.
14. Input/output currency named: consumes `FileEntry` (from `WalkEvent.FileFound`), outputs the same `FileEntry` to `SizeIndex.add` or drops it; the count leaves through `ScanResult.excluded`. Placement: after `ScanEvent.FileWalked`, before `SizeIndex.add` (decision 4).

## Configuration

- `ScanConfig.defaultExcludes` (default `true`, `ScanConfig.defaults()`); enforced inside `DefaultExcludeFilter.keep`. `cli`: `--no-default-excludes` sets it `false`. The name list itself is not configurable (decision 2; `--exclude=<glob>` deferred). `minSize` is unchanged and applies only to files the filter keeps (decision 7).

## Expected breakage

- `ScanServiceTest`: its static `CONFIG = ScanConfig.defaults()` throws during class initialization, so after the first test (`ExceptionInInitializerError`) the rest fail with `NoClassDefFoundError`, not directly on the stub.

## Open design notes

- **No convenience constructors** (decision 6 left it to the architect). A 3-argument `ScanConfig` would silently switch the excludes on in tests such as the `--min-size` one, and a 2-argument `ScanResult` would let the pipeline forget the count. So every `new ScanConfig(a, b, c)` and `new ScanResult(sets, skipped)` must gain the new argument: `ScanConfigTest` (lines 29, 39, 50, 61), `ScanPipelineTest` (294, 609), `ScanServiceTest` (89, 276, 294, 579), `ScanCommandTest` (265, 595), `TextReportTest` (37, 88, 96). Test compilation stays broken between the design and tests commits (as in DFS-026).
- **`FileWalked` moves before `SizeIndex.add`** inside the walk callback (it was after). Nothing observable changes for kept files; it makes decision 4's order literal.
- **`ScanResult` compact constructor is stubbed whole** (DFS-005 precedent), so its existing null checks and copies are in a comment for the implementer to restore with the new check. Every test that builds a `ScanResult` therefore fails on the stub at red.
- **JSONL key order:** `"excluded"` goes last, after `"reclaimableBytes"`, so no existing key moves.
- **Tests outside `touches`** (`ScannerFactoriesTest`, `ScanProgressSnapshotTest`, `ScannerConfigurationTest`, `ScanRunnerTest`, `CliApplicationTest`, `ScanServiceRealThreadTest`) still compile (they use only `ScanConfig.defaults()`) and fail at red on the `defaults()` stub in the test thread (`CliApplicationTest` via Spring's "Failed to execute ApplicationRunner", root cause the stub); they need no edits.

## Stubs

- Files stubbed: `file-scanner/src/main/java/us/pochaev/dfs/scanner/domain/model/ScanConfig.java` (`defaults()`), `.../domain/model/ScanResult.java` (compact constructor), `.../application/DefaultExcludeFilter.java` (new: constructor, `keep`, `excluded`). Wired but not stubbed: `.../application/ScanPipeline.java`, `cli/src/main/java/us/pochaev/dfs/cli/ScanCommand.java` (compile fix only).
- Each throws `new UnsupportedOperationException("not implemented: DFS-027")` per D2.
- Commit: to be filled in after the design commit.
