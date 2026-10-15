# DFS-027: Exclude known system files by default — verification

## Summary

- **DFS-027.AC-1** met: `DefaultExcludeFilter.keep` matches the last path element with `equals(".DS_Store")` / `startsWith("._")`, case-sensitive; proven at filter, pipeline and CLI level (real disk, incl. `.ds_store`/`.DS_STORE`, `x._photo.jpg`, `.DS_Store.bak`).
- **DFS-027.AC-2** met: `--no-default-excludes` builds `ScanConfig(..., false)` (asserted via the recorded config); twice gives exit 2, the message, the usage line naming the option, empty stdout, no scan started.
- **DFS-027.AC-3** met: `ScanResult.excluded` (non-negative) reaches JSONL `"excluded"` (last key) and text `, <n> excluded`; 4 / 2 / 4 / 0 / 0 cases tested on real disk; no excluded path appears in stdout.
- **DFS-027.AC-4** met: `FileWalked` is now emitted before the filter; pipeline test asserts `FileWalked(6, 32768)`, CLI test asserts `6 files, 32.0 KB found`.
- **DFS-027.AC-5** met: `defaults()` = `(1, 65536, 131072, true)`, pinned to `DefaultExcludeFilter.DEFAULT_ENABLED`; a flag-off config gives three sets and 0 excluded.
- **DFS-027.AC-6** met: filter runs before `SizeIndex.add`, so 0-byte `.DS_Store`/`._empty` count as excluded (5); with the flag off they are dropped by size and counted nowhere.
- Build re-run: `./gradlew build` green. Diff stays inside `touches` plus the story folder.
- `design.md` checklist dispositions all match the code (no convenience constructors, filter is a per-scan local, no port, no module-info change).
- No CLAUDE.md safety invariant is affected adversely (no deletion path; filter sits before hard-link detection; walker unchanged; empty files still dropped by `minSize`).
- Done-items ticked: scenarios automated and passing, build green, no files outside Touches, CLAUDE.md updated. Left unticked: the two real-scan items (E:/F: drives), for the user's real run.
- **Verdict: ready for the human gate**, pending the user's two real scans.

## Per-criterion detail

### AC-1
- Code: `application/DefaultExcludeFilter.java` (`keep`), wired in `ScanPipeline.walkLocations` before `SizeIndex.add`.
- Tests: `DefaultExcludeFilterTest` (parameterized excluded/kept names, including `._`, `._.DS_Store`, `.DS_Stor`, `photo._`; folder-name not matched; root path with no file name kept; null rejected), `ScanPipelineTest.defaultExcludesLeaveMetadataOutAndCountIt`, `nameMatchIsCaseSensitiveAndAnchored`, `ScanCommandTest.metadataFilesAreLeftOutAndCounted`, `matchIsCaseSensitive`, `onlyExactNamesMatch`.
- `matchIsCaseSensitive` was reworked (901c596) to delete and recreate rather than `Files.move`, which is a no-op on NTFS for a case-only rename. The test now runs (rather than skips) on Windows, and exercises a real case-variant name on disk, which is a stronger check than skipping.

### AC-2
- `ScanCommand.run` parses the flag, duplicate check returns `usage(...)` before any `start`; `USAGE_LINE` extended.
- Tests: `noDefaultExcludesScansMetadataFiles` (three sets, 0 excluded), `noDefaultExcludesTwiceIsUsageError` (exit 2, message, usage line, empty stdout, `fake.starts == 0`), `flagReachesTheScanConfig` (both polarities of `ScanConfig.defaultExcludes`).

### AC-3
- `ScanResult` gains `long excluded`, compact constructor rejects negatives; `ScanCommand.write` puts `"excluded"` after `"reclaimableBytes"`; `TextReport` appends `, <n> excluded` via the existing `count` formatter (thousands separators: `29,565`).
- Tests: `ScanResultTest`, `TextReportTest.summaryEndsWithExcludedCount` / `summaryShowsZeroExcluded`, `ScanCommandTest.textReportEndsWithExcludedCount` (exact scenario line), `nothingMatchedGivesZeroExcluded` (both formats), `summaryReportsResultCount` (fake result 29565 passes through), `ScanPipelineTest.excludedCountIsPerScan`. Existing DFS-020/DFS-023 summary assertions updated to include `excluded: 0` / `, 0 excluded`.

### AC-4
- `walkLocations` emits `FileWalked` first, then filters. Tests assert exact counts and bytes at both levels.

### AC-5
- `ScanConfigTest.defaultConfigHasDefaultExcludesOn` and the existing pin test gain the flag; `ScanPipelineTest.flagOffScansMetadataFiles`; `DefaultExcludeFilterTest.disabledFilterKeepsEverything` / `noArgumentFilterIsEnabled`.

### AC-6
- `ScanPipelineTest.emptyMetadataFileIsCountedAsExcluded` (6 excluded, `empty.txt` dropped by size, 1 set), `emptyMetadataFileWithFlagOffIsDroppedBySize`; `ScanCommandTest.emptyMetadataFilesAreCounted` (5), `emptyMetadataFilesWithFlagOffAreDropped`.

## Test quality

- Tests assert on observable output (set membership, JSONL fields, exact text lines, progress events, recorded config), not on implementation internals. The CLI tests run the real scanner on a `@TempDir` tree built to the ticket's Background, so they cover the end-to-end path including the NIO walker.
- Boundary cases go beyond the scenarios (`._` alone, `.DS_Stor`, `photo._`, folder names in the path, root path, per-scan count isolation, two independent filter instances).
- Minor weakness, not blocking: `ScanPipelineTest.emptyMetadataFileWithFlagOffIsDroppedBySize` asserts "no 0-byte file in any set" rather than naming the two paths; given `minSize = 1` this is equivalent. `nameMatchIsCaseSensitiveAndAnchored` relies on all kept 100-byte files sharing one fake digest and asserts names only, as test-plan.md notes.

## Design conformance

- Public API matches `design.md` exactly: `ScanConfig(long, int, long, boolean)`, `ScanResult(List, List, long)` with negative check, `DefaultExcludeFilter` with `DEFAULT_ENABLED`, package-private `DS_STORE` / `APPLE_DOUBLE_PREFIX`, two constructors, `keep`, `excluded`.
- Checklist items 1-14 as disposed: the filter is a local of `ScanPipeline.scan` (item 13), order preserved (item 3), no I/O (item 10), no port (item 11), config default pinned (item 6).
- No `module-info.java` change, no new exported type; ArchUnit and module-export tests green.
- Nit: the `ScanPipeline` class Javadoc's first paragraph now has one over-long line; cosmetic only.

## Safety invariants

- Never delete the last copy, re-verify at execution, preview is a pure query: not touched by this ticket (no deletion code). Note for DFS-021/022: excluded `._*` sidecars are never in a `DuplicateSet`, so they can never be planned for deletion on their own, which is the intent.
- Hard links: the filter runs before `HardLinkDetector`, only removes entries; identity grouping unchanged.
- Symlinks/junctions: walker untouched.
- Empty files: still excluded by `minSize` (>= 1); AC-6 only changes which counter a 0-byte metadata file lands in, never lets it into a set.

## Definition of done

- Ticked: every scenario automated and passing; `./gradlew build` green (re-run by the verifier); no files outside Touches (checked `git diff --stat dfa6421 HEAD`); `CLAUDE.md` names `--no-default-excludes` (Commands block) and the `excluded` summary field (Current state).
- Not ticked, left for the user's real run: the real scan of the five E:/F: folders with `--format=text` (expects no `._*`/`.DS_Store` set, non-zero `excluded`, fewer than 573 sets, 3.2 GB reclaimable), and the same scan with `--no-default-excludes` (expects 573 sets, 1,633 files, `0 excluded`). The verifier did not run them (long scans on the user's drives, per the orchestrator's instruction).
