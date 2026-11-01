# DFS-032: List one directory level — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-032.AC-1` | Files and subfolders of one level are listed, not their contents | `DFS-032.AC-1, DFS-032.AC-2, DFS-032.AC-3 Files and subfolders of one level are listed, not their contents` |
| `DFS-032.AC-1` | An empty folder gives an empty listing | `DFS-032.AC-1 An empty folder gives an empty listing` |
| `DFS-032.AC-2` | Files and subfolders of one level are listed, not their contents | (same test as AC-1 above) |
| `DFS-032.AC-2` | Files the scan leaves out are still listed | `DFS-032.AC-2 Files the scan leaves out are still listed` |
| `DFS-032.AC-3` | Files and subfolders of one level are listed, not their contents | (same test as AC-1 above) |
| `DFS-032.AC-4` | A symlink child is reported, not followed | `DFS-032.AC-4 A symlink child is reported, not followed` |
| `DFS-032.AC-4` | A junction child is reported, not followed | `DFS-032.AC-4 A junction child is reported, not followed` |
| `DFS-032.AC-5` | A child that cannot be read does not fail the listing | `DFS-032.AC-5 A child that cannot be read does not fail the listing` |
| `DFS-032.AC-6` | Entries are ordered by name, kinds mixed | `DFS-032.AC-6 Entries are ordered by name, kinds mixed` (real disk); `DFS-032.AC-6 Entries the reader returns out of order are sorted by name, kinds mixed` (fake reader) |
| `DFS-032.AC-7` | A missing folder or a file is refused | `DFS-032.AC-7 A missing folder or a file is refused` |
| `DFS-032.AC-7` | A link given as the folder is refused | `DFS-032.AC-7 A link given as the folder is refused`; `DFS-032.AC-7 A junction given as the folder is refused` |
| `DFS-032.AC-7` | A folder that cannot be read is refused, and null is a caller bug | `DFS-032.AC-7 A folder that cannot be opened is refused with the exception's message`; `DFS-032.AC-7 A null folder is a caller bug`; service-level: `The reader's failures map to the four error codes`, `LOCATION_UNREADABLE carries the exception's message`, `A not-found failure names the folder` |
| `DFS-032.AC-8` | Listing changes nothing | `DFS-032.AC-8 Listing changes nothing` |
| `DFS-032.AC-9` | The factory and the Spring wiring expose the listing | `DFS-032.AC-9 ScannerFactories.listDirectory lists a folder`; `DFS-032.AC-9 The autoconfiguration provides one working ListDirectory bean`; `DFS-032.AC-9 An app's own ListDirectory bean replaces the default` |

Files: `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/ListDirectoryRealDiskTest.java`, `.../application/ListDirectoryServiceTest.java`, and two added tests in `scanner-spring/.../ScannerAutoConfigurationTest.java`.

## Fakes and fixtures used

- `DirectoryAccess` (package-private seam): a test class that delegates to real NIO and injects an `AccessDeniedException` for one child's attributes (AC-5) or for `open` (AC-7 LOCATION_UNREADABLE).
- `DirectoryReader`: a lambda returning unsorted entries (AC-6) or throwing a chosen `IOException` (AC-7 mapping).
- Executor: none (synchronous).
- Fixture files: generated in code in a `@TempDir`; symlinks via `Fixtures.createSymlinkOrSkip`, junctions via `Fixtures.createJunction` behind `assumeTrue(Fixtures.isWindows())`. No committed fixtures.

## Notes for the implementer

- The NPE test uses try/catch, not `assertThrows`.
- Expected paths are `folder.resolve(name)` on the folder as passed (raw `@TempDir`, no `toRealPath()`).
- The symlink tests skip where symlink creation is not permitted; junction tests skip off Windows.
