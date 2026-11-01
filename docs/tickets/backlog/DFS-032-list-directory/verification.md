# DFS-032: List one directory level — verification

## Product owner review

### What you can do now:

The scanner core can now list one folder as it is on disk: every file (including empty, `.DS_Store` and `._*` files), every subfolder, and every symlink, junction or unreadable entry reported as skipped. Nothing is followed and nothing is changed. The UI's folder detail view will be built on this. There is no CLI command or server endpoint for it yet; the server endpoint is a later ticket.

### Acceptance criteria:

- **DFS-032.AC-1** met: one entry per direct child and nothing from subfolders; an empty folder gives an empty listing.
- **DFS-032.AC-2** met: files carry name, path, size and modified time; empty, `.DS_Store` and `._*` files are listed.
- **DFS-032.AC-3** met: subfolders carry name, path and modified time.
- **DFS-032.AC-4** met: a symlink is skipped as `SYMLINK` and a junction as `SPECIAL_FILE`; their targets' contents never appear (both tested on real links on this machine).
- **DFS-032.AC-5** met: an entry whose details cannot be read is skipped as `UNREADABLE` with a message, and the rest of the folder is still listed.
- **DFS-032.AC-6** met: entries are sorted by name with files and folders mixed (tested on disk and with a fake reader that returns them out of order).
- **DFS-032.AC-7** met: a missing folder, a file, a link or junction, and an unreadable folder each give their own error code; `null` throws.
- **DFS-032.AC-8** met: names, sizes and modified times are the same before and after listing.
- **DFS-032.AC-9** met: `ScannerFactories.listDirectory()` works, the Spring bean exists and works, and an app's own bean replaces it.

### Try it:

Nothing is user-visible yet: no CLI flag and no server endpoint. The behavior shows in the tests:

```
./gradlew :file-scanner:test --tests "*ListDirectory*"
./gradlew :scanner-spring:test --tests "*ScannerAutoConfigurationTest"
```

Look for 18 passing `ListDirectory*` tests (14 on the real disk, 4 on the service) with none skipped on Windows, and 10 passing `ScannerAutoConfigurationTest` tests (2 of them new).

### Decided without you:

- A link or junction given as the folder is reported inside the core through a new exception, `LinkNotFollowedException`. It is internal and never seen by callers, who see `LOCATION_IS_LINK`.
- A reparse-point *file* given as the folder (for example a OneDrive placeholder file) gives `NOT_A_DIRECTORY`. A reparse-point *folder* (junction, cloud folder) gives `LOCATION_IS_LINK`.
- The error messages are fixed: `folder not found: <path>`, `not a directory: <path>` and `folder is a link, not followed: <path>`. `LOCATION_UNREADABLE` carries the operating system's message as is (or the exception's name if it has no message). An unreadable entry's detail follows the same rule.
- Skipped symlinks and junctions carry the scanner's existing detail texts: `symbolic link`, `reparse point or special directory` and `not a regular file`.
- An entry deleted while the folder is being listed is shown as an `UNREADABLE` entry; the listing does not fail. If the folder itself disappears or is replaced by a file mid-listing, the result is the matching error code.
- Sorting uses the platform's path ordering, so it ignores case on Windows and is case-sensitive on Linux.
- `ListDirectory` is its own bean and factory method, not part of `ScanUseCases` (it keeps no scan state).
- The scan's file walker was moved onto the same classification code as the listing, so the two cannot drift apart. Its behavior is unchanged.

### Known limits:

- Entries are not marked against a scan (duplicate, unique, new or missing since the scan). That is a follow-up (ticket decision 1).
- Any folder can be listed. Limiting listings to the scan's roots is the server's job (ui.md U10).
- There is a small race, in theory only. If the folder is replaced by a link between the link check and opening it, the listing reads one level of the link's target. It is still read-only, and every child is still classified without following links. Java offers no portable way to open a folder without following a link on Windows.
- No test makes a folder or entry truly unreadable through operating-system permissions. Both cases are simulated through a test seam, as the ticket allowed.
- No test checks the exact detail texts of skipped symlinks and junctions. They are the walker's existing strings, shared through one enum.

### Evidence:

- `./gradlew build` was re-run by the verifier on 2e5e8c5 and is green. The deterministic gate passed on the same commit: 450 passed, 3 skipped (none of the skips are story tests), plus the disabled, freeze, touches, stubs, traceability and consistency checks.
- `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/ListDirectoryRealDiskTest.java`: 14 tests, 0 skipped. Symlinks and junctions are created at test time.
- `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/ListDirectoryServiceTest.java`: 4 tests.
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`: 2 new tests, 10 in total.
- `NioFileTreeWalkerTest` (17 tests, 1 skipped: the case-sensitivity test, which is expected on Windows), `NioProgressTest` and `ScanPipelineEndToEndTest` are unchanged since the base commit 2574b67, and all pass.

### How it went:

- Design: 1 attempt (opus). It hit the 25-turn cap with the stubs written but no design.md, was resumed once, and took 40 turns in total.
- Tests: 1 attempt (sonnet, 18 turns). All 20 tests were red against the stubs.
- Implement: 1 attempt (sonnet, 10 turns). The gate was green first time.
- Verify: 1 attempt. No defects were found and no retries or escalations were needed.

Ready for the user's approval.

## Criteria in detail

| Criterion | Verdict | Evidence |
|---|---|---|
| AC-1 | met | `listsOneLevel` asserts exactly `[a.txt, b.jpg, sub]` and no `c.txt`; `emptyFolder` asserts an empty `Success`. `NioDirectoryReader` iterates one `DirectoryStream` and never descends. |
| AC-2 | met | `listsOneLevel` checks path `tmp.resolve("a.txt")`, size 3 and the mtime against `Files.getLastModifiedTime`; `listsFilesTheScanLeavesOut` checks three `FileItem`s, `empty.txt` with size 0. `ScanConfig` is not read anywhere in the new code. |
| AC-3 | met | `FolderItem` path and mtime are asserted in `listsOneLevel`. |
| AC-4 | met | `symlinkChild` (`SYMLINK`, path, no `secret.txt`) and `junctionChild` (`SPECIAL_FILE`, no `secret.txt`) ran on real links on Windows. Children are classified from `NOFOLLOW_LINKS` attributes only. |
| AC-5 | met | `unreadableChild` injects `AccessDeniedException` for one child through `DirectoryAccess`; the other child stays a real `FileItem` and the bad one is `UNREADABLE` with a non-blank detail. |
| AC-6 | met | `orderedByName` (real disk) and `sortsReaderOutput` (a fake reader returning entries in reverse order; this proves the sort is in the service, not an accident of the file system). |
| AC-7 | met | `missingAndFile`, `linkAsFolder`, `junctionAsFolder`, `unopenableFolder` (the message equals the exception's message), `nullFolder` (try/catch), and the service-level mapping tests for all four codes. |
| AC-8 | met | `listingChangesNothing` snapshots names, sizes and mtimes (one mtime set to a fixed past instant) before and after. Code inspection: the reader and the service only read. |
| AC-9 | met | `factoryLists`; `providesListDirectoryBean` (one bean, which lists `a.txt`); `appListDirectoryReplacesDefault` (lists through the factory first, then `assertSame` on the app's bean, count 1). |

## Test quality

- The tests assert on observable output (names, kinds, paths, sizes, mtimes, codes, messages), not on implementation calls. The fake `DirectoryAccess` delegates to real NIO except for the one injected failure, so AC-5 and AC-7 UNREADABLE exercise the real adapter code path.
- AC-6 has a fake-reader test that would catch a missing sort even on a file system that happens to return names in order.
- Gaps (minor, not blocking):
  - `junctionChild` does not assert the skipped item's `path`.
  - No test pins the `detail` strings of SYMLINK and SPECIAL skips (design decision d).
  - Nothing covers the record checks (`size >= 0`, nulls). They are in the design but not in any AC.
- No new `Thread.sleep`. `ScannerAutoConfigurationTest.await` has a `Thread.sleep(10)` poll that predates this ticket (from DFS-031) and is outside its diff. Every disk access in the new tests is under `@TempDir`; `ListDirectoryServiceTest` uses `Path.of("folder")` only as a value passed to a fake reader, which never touches disk.

## Design conformance

- The diff matches design.md: the public API, the layer placement, the `EntryKind.of` order and table, the `NioDirectoryReader.read` steps (folder pre-check, `DirectoryIteratorException` unwrapped, child attributes read on the stream's own `Path`, `describe`), the `ListDirectoryService` exception table and sort, the record checks, and the factory and bean one-liners.
- Checklist dispositions: items 1, 3, 4, 6, 7, 8, 9, 11 and 12 are marked not applicable with reasons, matching ticket decision 6. Items 2, 5, 10, 13 and 14 are addressed and the code honors them: failures are values and NPE is a bug; the folder is checked before it is opened; the listing is never partial on failure; the tests are untimed; and the exported surface is right, with `module-info.java` unchanged.

## `NioFileTreeWalker` refactor (behavior must be identical)

| Callback | Before | After | Same? |
|---|---|---|---|
| `preVisitDirectory` | skip if `!root && isOther()`, `SPECIAL_FILE` / `"reparse point or special directory"` | skip if `!root && EntryKind.of == SPECIAL_DIRECTORY`, same reason and detail | Yes. `preVisitDirectory` is only called with `isDirectory()` true, and `walkFileTree` is called without `FOLLOW_LINKS`, so `isSymbolicLink()` is false there (on Windows `isDirectory()` returns false for a symlink; on Unix a NOFOLLOW symlink is not a directory). So `of` returns SPECIAL_DIRECTORY exactly when `isOther()` holds. |
| `visitFile` symlink | `SYMLINK` / `"symbolic link"` | `EntryKind.SYMLINK` reason and detail, the same values | Yes, checked first in both. |
| `visitFile` regular | `FileFound` | `FileFound` when `kind == REGULAR_FILE` | Yes. `EntryKind` checks `isOther()` before `isRegularFile()`, but in `WindowsFileAttributes` and Unix attributes (and JimFS) `isRegularFile()` and `isOther()` are mutually exclusive, so the order makes no difference. |
| `visitFile` other | `SPECIAL_FILE` / `"not a regular file"` | `EntryKind.SPECIAL_FILE` reason and detail, the same values | Yes. |
| `resolveRoot`, `visitFileFailed` | — | untouched | Yes. |

`NioFileTreeWalkerTest`, `NioProgressTest` and `ScanPipelineEndToEndTest` have no diff since 2574b67 and pass.

## Safety invariants

- **No following of symlinks or junctions:** held. The folder pre-check and every child use `NOFOLLOW_LINKS`. A link or reparse folder is refused before `newDirectoryStream`. Link targets are never read. Residual TOCTOU: see Known limits.
- **Read-only:** held. No write, move or delete call in the new code; AC-8 also checks this.
- **Cloud placeholders:** a placeholder child is a reparse point, so `isOther()` makes it a `SPECIAL_FILE` skip. Only its attributes are read, so no download is triggered.
- **Never delete the last copy, re-verify at execution, preview is pure, hard links:** not applicable (no deletion, no duplicate grouping).
- **Empty files excluded by default:** this is a scan rule. The listing deliberately shows empty files (ticket decision 2) and does not group them.

## Summary

- DFS-032.AC-1 met: one entry per direct child, nothing from subfolders, and an empty folder gives an empty Success.
- DFS-032.AC-2 met: FileItem name, path, size and mtime; empty, `.DS_Store` and `._*` files listed.
- DFS-032.AC-3 met: FolderItem name, path and mtime.
- DFS-032.AC-4 met: symlink is SYMLINK, junction is SPECIAL_FILE, targets never read (real links on Windows).
- DFS-032.AC-5 met: an unreadable child is UNREADABLE with a detail, and the listing still succeeds (seam-injected).
- DFS-032.AC-6 met: sorted by `Path.compareTo` of the names in the service, proven with an out-of-order fake reader.
- DFS-032.AC-7 met: NOT_FOUND, NOT_A_DIRECTORY, IS_LINK (symlink and junction), UNREADABLE with the exception's message, and NPE.
- DFS-032.AC-8 met: names, sizes and mtimes unchanged; the code only reads.
- DFS-032.AC-9 met: the factory works, the bean works and backs off.
- Walker refactor: behavior identical (reasoned per callback from JDK attribute semantics), and the walker's tests are unchanged and green.
- Design conformance: full, including every checklist disposition. `module-info.java` is unchanged.
- Minor test gaps: the junction path and the skip detail strings are not asserted. There is a theoretical TOCTOU if the folder is swapped for a link between the check and the open (read-only).
- `./gradlew build` was re-run green on 2e5e8c5.
- Ready for the human gate.
