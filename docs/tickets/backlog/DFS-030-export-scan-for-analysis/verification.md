# DFS-030: Export a complete scan for analysis: verification

## Summary

- **DFS-030.AC-1** met. `ScanPipeline` passes its `keepTopLevel` output (`kept`) into `ScanResult`. Tests cover nested, repeated and unsorted roots.
- **DFS-030.AC-2** met. `FolderCounter` keys on `path.getParent()` and `totals()` sorts by `Path`. There is no entry for a folder holding only subfolders, and the totals do not depend on arrival order.
- **DFS-030.AC-3** met. `add` is called only after `excludeFilter.keep` and ignores files below `minSize`. Walker `Skipped` events never reach it.
- **DFS-030.AC-4** met. Files are counted during the walk, before hashing. A test with a hasher that fails confirms the file is both counted and in `skipped`.
- **DFS-030.AC-5** met. Every path counts. Tested through the pipeline with a fake identity that reports one inode.
- **DFS-030.AC-6** met. `FolderTotal` and `ScanResult` validate their input and `ScanResult` copies its lists (`List.copyOf`). `totals()` returns a snapshot.
- **DFS-030.AC-7** met. The header has every field, and the `Clock` is read before `start` and after COMPLETED. A test with a queued clock pins both reads and the order.
- **DFS-030.AC-8** met. The `folder` lines come in `folders()` order, holding `path`, `files` and `bytes`.
- **DFS-030.AC-9** met. Each copy is a list of `{path, modified}` objects. Times are full precision with trailing fraction zeros trimmed, which matches the ticket's own example (see notes).
- **DFS-030.AC-10** met. Line order is scan, folder, set, skipped, summary. The summary fields are unchanged, and an empty result still writes the header and the summary.
- **DFS-030.AC-11** met. The text header is written first. Tests cover the same-day form, the form that crosses midnight, MB sizes and excludes off.
- Build: `./gradlew build` re-run, green (up to date at HEAD 56b5667).
- Done-items: all 7 ticked.
- **One defect outside the ACs:** `tools/analysis/overlaps.sql` stops roll-up at the roots with `anc = root OR starts_with(anc, root || '/')`. When the root is a drive root (`F:\` becomes `F:/`) or `/`, nothing matches, so `files_anc` and the `folders` view come out **empty**. I reproduced this: the fixtures export with its root rewritten to `C:\` gives 0 rows, against 5 with the real root. Scanning a whole drive is the use case named in the ticket's context.
- **Recommendation:** go back to **implement** for a small fix to `overlaps.sql`: trim a trailing `/` from the root before comparing, or compare on the root with its trailing slash. The ACs and tests need no change. The human gate may instead accept as is and file a follow-up.

## Per-criterion detail

| AC | Verdict | Evidence |
|---|---|---|
| AC-1 | met | `ScanPipeline.scan` returns `new ScanResult(kept, ...)`, and `kept` is `keepTopLevel`'s sorted, deduplicated output. `ScanFolderTotalsTest`: in/a/b, in/a, in/a gives [real/a]. Resolution through `LocationResolver` is exercised, because the inputs differ from their real paths. Sorting: z, m gives [m, z]. |
| AC-2 | met | `FolderCounter` is a `HashMap<Path,long[]>` sorted in `totals()`. The pipeline test checks root/p (2, 350) and root/p/q (1, 40), with no root entry. A folder without duplicates is totalled and `sets()` is empty. `FolderCounterTest` checks that arrival order does not matter and that empty input gives an empty list. |
| AC-3 | met | `add` sits inside `if (excludeFilter.keep(...))` and checks `size < minSize`. The pipeline tests cover `.DS_Store`, `._x.jpg`, a file below min, an empty file and a kept file (giving 1, 8), excludes off (giving 2, 14), and a walker Skipped (not counted, present in `skipped`). `FolderCounterTest` checks the boundary at exactly minSize. |
| AC-4 | met | The fake hasher's full hash fails for b.bin. The folder total stays (2, 40) and b.bin is in `skipped`. |
| AC-5 | met | A fake `FileIdentity` returns one key for both paths, so the set is collapsed to one physical copy, yet the total is (2, 60). This is the right way to test it, because it proves the totals ignore the hard-link grouping. |
| AC-6 | met | `FolderTotalTest` has 4 cases. `ScanResultTest` covers null roots, null folders, a null root element, a null FolderTotal element, an add to either list throwing UOE, and a later change to the input lists not showing. `FolderCounterTest` checks that `totals()` is immutable and a snapshot. |
| AC-7 | met | `ScanCommandTest` compares the exact header JSON for the scenario's roots, config and clock. A second test checks that `--min-size=10` and `--no-default-excludes` reach the header. A third uses a 3-instant queued clock to show exactly two reads, the start one before the finish one (the 2099 instant is never used). |
| AC-8 | met | Lines 2 and 3 equal the scenario's exact JSON, including the 10485760000 long. |
| AC-9 | met | The exact `copies` JSON from the scenario, with hard-linked paths grouped in one inner array, each holding its own `modified`. |
| AC-10 | met | Line types are scan, folder, folder, set, skipped, summary, and the summary JSON is exact (`sets 1, files 2, skipped 1, reclaimableBytes 4, excluded 7`). The empty result writes scan, summary. `CliApplicationTest`'s line types were updated to match. |
| AC-11 | met | `ScanCommandTest` (through the CLI, with `--format=text`) and `TextReportTest` (direct) check both scenario lines exactly, that `#1` follows on line 3, and that a single root has no separator. |

## Test quality

- The tests assert on behavior: exact `FolderTotal` lists, exact JSON compared as parsed trees, and exact text lines. None restate the implementation. The pipeline tests run the real `ScanPipeline` against hand-written fakes of all four ports, as CLAUDE.md asks for `application`.
- The AC-7 clock test is well built: a queued clock with a spare third instant shows both how many reads happen and in what order.
- The AC-5 test goes through hard-link detection, not just the counter, so it would catch a change that counted physical copies instead of paths.
- Small gap: the test-author dropped the real-scan AC-9 test (learnings.md) because it could not fail with the raw stub exception. Coverage is still complete through the fake ports. A real end-to-end JSONL run was done by hand below and shows correct `modified` values.

## Design conformance

- The placement, public API and checklist items 1 to 14 match `design.md`. `FolderCounter` is a nested class, package-private and not thread-safe, and lives inside one `scan()` call. `ScanPipeline` keeps no state in fields (item 13).
- Deviation 1, timestamps: design.md and ticket decision 6 say `Instant.toString()`. The implementation trims trailing zeros from the fraction (`iso()`), because `Instant.toString()` pads to 3, 6 or 9 digits and would print `...31.451234500Z`, contradicting the ticket's own example `...31.4512345Z`. No precision is lost and the output is still valid ISO-8601. I accept this; the implementer recorded it as a learning.
- Deviation 2, heap benchmark: it is a plain method, not `@Disabled`, because the story gate rejects `@Disabled`. It was recorded by the test-author, and the done-item is ticked with that note.
- The text header uses fully qualified `java.time.*` names inline instead of imports. This is a style point only.

## Safety invariants

- **Hard links are not duplicates:** the `copies` grouping is preserved, one inner array per `PhysicalCopy`. The totals count paths by design (ticket, "Hard links in totals"), and that is documented on `FolderTotal`.
- **Empty files excluded:** they are not counted (the minSize check in `add`, tested).
- **No symlink or junction following:** the walker is unchanged. Skipped paths never reach the counter.
- **Never delete the last copy, re-verify at execution, preview is pure:** not applicable, since the ticket touches no deletion code.
- **No state in singletons, the core stays clock-free:** the `Clock` lives only in `ScanCommand` (composition: `ScanRunner` passes `Clock.systemUTC()`).

## Done-items: how verified

1. Every scenario is an automated test and passes: I mapped all 18 scenarios to tests (table above and test-plan.md), and `./gradlew build` is green.
2. `./gradlew build` green: I re-ran it at HEAD and it was up to date and passing (gate: 363 passed, 3 skipped).
3. No files outside touches: the diff against 8ccbe31 contains only touched paths, the story folder and `CliApplicationTest`, which sits inside the `cli/src/test/java/us/pochaev/dfs/cli/` touch. The gate's touches check passed.
4. Heap benchmark: committed as `FolderCounterHeapBenchmark`, a plain method (see Deviation 2). Figures: the implementer measured about 177 B/folder and 16.9 MB; an earlier verify attempt reproduced 176.6 B/folder and 16.8 MB at 100,000 folders. I did not re-run it, as the orchestrator directed.
5. overlaps.sql under DuckDB: I ran the built CLI jar (`--enable-native-access=ALL-UNNAMED --progress=off --output=...`) over `file-scanner/src/test/resources/fixtures` only. It wrote 11 lines (1 scan, 7 folder, 2 set, 1 summary). I then ran the script with Python DuckDB 2.0.0. It runs without error: `roots` has 1 row, `folder_totals` has 7 rows, and `folders` has 5 rows with `total_files`/`total_bytes` (fixtures root: 23 files, 329 B; `folders`: 5, 127; `folder1`: 2, 52; `folder2`: 3, 75; `basic`: 3, 67). Roll-up stops at the scanned root. The drive-root defect is above.
6. CLAUDE.md: decision 1 now says the analysis tool computes `LocationOverlap` from the export, and the JSONL description has the line order, the version rule and the pointer to DFS-030. Checked by reading.
7. README.md: the text report example shows the two-line header, and a JSONL section was added. Checked by reading.
