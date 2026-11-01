# DFS-032: List one directory level — design

## Summary

There is one query port, `ListDirectory.list(Path) -> Result<DirectoryListing>`, in `domain.usecases.query`. It is implemented by `application.ListDirectoryService`, whose only field is the new secondary port `infra.port.DirectoryReader`. That port is implemented by `infra.adapter.NioDirectoryReader`, which classifies entries through the package-private `infra.adapter.EntryKind`, the classification it shares with `NioFileTreeWalker`. New `domain.model` types (the package is already exported, so `module-info.java` is unchanged): `DirectoryListing` and the sealed `DirectoryEntry` with `FileItem`, `FolderItem` and `SkippedItem`. `ErrorCode` gains `LOCATION_IS_LINK`. State model: none (a synchronous read on the caller's thread).

- AC-1, AC-2, AC-3, AC-4, AC-8: real-disk tests through `ListDirectoryService` over `NioDirectoryReader`. They reach the stub `ListDirectoryService.list` first (and `NioDirectoryReader.read` when they call the adapter directly).
- AC-5: through the package-private seam `DirectoryAccess`. A fake `attributes(path)` throws `AccessDeniedException` for one child. Reaches `list` (or `read`).
- AC-6: ordering is done in the service. Covered by a fake `DirectoryReader` that returns entries unsorted, plus one real-disk test. Reaches `list`.
- AC-7: NOT_FOUND and NOT_A_DIRECTORY on the real disk. LINK uses a symlink or junction made at test time. UNREADABLE uses a fake `DirectoryReader` that throws, or `DirectoryAccess.open` that throws. NPE uses try/catch, not `assertThrows`. All reach `list`.
- AC-9: `ScannerFactories.listDirectory()` and the `ScannerAutoConfiguration` bean are real one-liners, as in DFS-022. The tests list a folder through the factory and through the bean before any back-off assertion, so they reach `list`.
- `EntryKind.of` is stubbed. `NioFileTreeWalker` is left unchanged at design; the implementer moves it onto `EntryKind` without changing its behavior or its tests.
- Checklist items not applicable: 1, 3, 4, 6, 7, 8, 9, 11, 12 (no ID, no state, no callbacks, no executor; ticket decision 6).
- Decisions settled here, since this ticket has no design gate:
  - (a) A link given as the folder is reported through a new port exception, `infra.port.LinkNotFollowedException extends FileSystemException`.
  - (b) A folder of kind SYMLINK or SPECIAL_DIRECTORY gives LOCATION_IS_LINK. A folder of kind REGULAR_FILE or SPECIAL_FILE gives NOT_A_DIRECTORY; this covers a reparse-point file such as a OneDrive placeholder.
  - (c) Failure messages are fixed (listed below). LOCATION_UNREADABLE carries exactly the exception's message, or `toString()` when the message is null or blank. The same rule gives an UNREADABLE child's `detail`.
  - (d) SYMLINK and SPECIAL skips carry the walker's own detail strings.
  - (e) A child removed while listing is an UNREADABLE child. The folder disappearing or turning into a file after the pre-check maps by exception type.
  - (f) Ordering compares `folder.getFileSystem().getPath(name)` with `Path.compareTo`.
- Expected breakage: none.

## Public API

- `ListDirectory` in `domain.usecases.query`: `Result<DirectoryListing> list(Path folder)`.
- `DirectoryListing` in `domain.model`: `(Path folder, List<DirectoryEntry> entries)`.
- `DirectoryEntry` in `domain.model`: sealed; `String name()`, `Path path()`. Variants:
  - `DirectoryEntry.FileItem(String name, Path path, long size, Instant lastModified)`
  - `DirectoryEntry.FolderItem(String name, Path path, Instant lastModified)`
  - `DirectoryEntry.SkippedItem(String name, Path path, SkipReason reason, String detail)`
- `ErrorCode` in `domain.model`: adds `LOCATION_IS_LINK`. The Javadoc of `LOCATION_NOT_FOUND`, `NOT_A_DIRECTORY` and `LOCATION_UNREADABLE` is widened to cover a listed folder (done in the stub).
- `ScannerFactories.listDirectory()` in `domain.factory`: `static ListDirectory listDirectory()`, which returns `new ListDirectoryService(new NioDirectoryReader())`.
- Secondary (not exported):
  - `DirectoryReader` in `infra.port`: `List<DirectoryEntry> read(Path folder) throws IOException`.
  - `LinkNotFollowedException` in `infra.port`: `(String file)`.

No first-slice type is replaced. `ScanUseCases`, `FileTreeWalker` and `SkipReason` are unchanged.

Record checks the implementer adds (left unchecked at design, per the archetype):

- `DirectoryListing`: `folder` non-null; `entries = List.copyOf(entries)` (NPE on a null list or element).
- `FileItem`: `name`, `path`, `lastModified` non-null; `size >= 0` (IAE otherwise).
- `FolderItem`: `name`, `path`, `lastModified` non-null.
- `SkippedItem`: `name`, `path`, `reason`, `detail` non-null.

## Layer placement

- `domain.usecases.query` (exported): `ListDirectory`.
- `domain.model` (exported): `DirectoryListing`, `DirectoryEntry` and its three records, `ErrorCode.LOCATION_IS_LINK`.
- `application`: `ListDirectoryService`, public final. Its public constructor takes `(DirectoryReader)`, and its only field is that reader. It needs no seam.
- `infra.port` / `infra.adapter`:
  - `infra.port`: `DirectoryReader` and `LinkNotFollowedException`.
  - `infra.adapter`: `NioDirectoryReader`, public final. Its public no-arg constructor uses `DirectoryAccess.NIO`; a package-private constructor takes `(DirectoryAccess)`.
  - `infra.adapter`, package-private: `DirectoryAccess`, an interface with `DirectoryStream<Path> open(Path)` and `BasicFileAttributes attributes(Path)` (NOFOLLOW_LINKS), plus its real-NIO constant `NIO`.
  - `infra.adapter`, package-private: `EntryKind`, an enum with `of(BasicFileAttributes)`, `reason()` and `detail()`.
- `scanner-spring`: `ScannerAutoConfiguration.listDirectory()`, `@Bean @ConditionalOnMissingBean`, returns the factory's result directly. No delegate is needed, because the service implements this one port only.

## Behavior (the contract the implementer follows)

**`EntryKind.of(attrs)`** checks, in this order:

1. `isSymbolicLink()`: SYMLINK
2. `isOther()`: SPECIAL_DIRECTORY when `isDirectory()`, otherwise SPECIAL_FILE
3. `isDirectory()`: DIRECTORY
4. `isRegularFile()`: REGULAR_FILE
5. anything else: SPECIAL_FILE

`reason()` and `detail()` per kind:

| Kind | `reason()` | `detail()` |
|---|---|---|
| SYMLINK | `SkipReason.SYMLINK` | `"symbolic link"` |
| SPECIAL_DIRECTORY | `SkipReason.SPECIAL_FILE` | `"reparse point or special directory"` |
| SPECIAL_FILE | `SkipReason.SPECIAL_FILE` | `"not a regular file"` |
| REGULAR_FILE, DIRECTORY | `null` | `null` |

These are the walker's current strings, already written in the stub.

**`NioFileTreeWalker` refactor (implementer).** Behavior stays identical and `NioFileTreeWalkerTest` stays unchanged:

- `preVisitDirectory`: skip when `!dir.equals(root) && EntryKind.of(attrs) == SPECIAL_DIRECTORY`, using that kind's reason and detail.
- `visitFile`:
  - SYMLINK: skip with its reason and detail.
  - REGULAR_FILE: `FileFound`.
  - Every other kind: skip with `SPECIAL_FILE` and `"not a regular file"`. DIRECTORY and SPECIAL_DIRECTORY cannot reach `visitFile` without maxDepth.
- `resolveRoot` is unchanged.

**`NioDirectoryReader.read(folder)`:**

1. Run `access.attributes(folder)` and classify the result with `EntryKind.of`. Exceptions propagate (`NoSuchFileException` and the rest).
   - SYMLINK or SPECIAL_DIRECTORY: throw `new LinkNotFollowedException(folder.toString())`.
   - REGULAR_FILE or SPECIAL_FILE: throw `new NotDirectoryException(folder.toString())`.
   - DIRECTORY: carry on to step 2.
2. Open `access.open(folder)` in try-with-resources. An exception from opening or from closing propagates.
3. Iterate the stream. A `DirectoryIteratorException` is unwrapped and its `IOException` cause is thrown. For each `child` the stream yields:
   - `name = child.getFileName().toString()` and `path = folder.resolve(name)`.
   - Call `access.attributes(child)` on the stream's own `Path` object, so Windows reuses the attributes cached by the listing.
   - If that throws an `IOException` `e`: `SkippedItem(name, path, UNREADABLE, describe(e))`.
   - Otherwise switch on `EntryKind.of(attrs)`:
     - REGULAR_FILE: `FileItem(name, path, attrs.size(), attrs.lastModifiedTime().toInstant())`.
     - DIRECTORY: `FolderItem(name, path, attrs.lastModifiedTime().toInstant())`.
     - Any other kind: `SkippedItem(name, path, kind.reason(), kind.detail())`.
4. Return the entries in stream order. Nothing below a child is read, and no link target is read.

`describe(e)` is `e.getMessage()`, or `e.toString()` if that message is null or blank, so the detail is never empty.

**`DirectoryAccess.NIO`:** `open` is `Files.newDirectoryStream(folder)`; `attributes` is `Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)`.

**`ListDirectoryService.list(folder)`:**

1. `Objects.requireNonNull(folder, "folder")`.
2. Call `reader.read(folder)` and map its exceptions:

   | Exception | Failure |
   |---|---|
   | `NoSuchFileException` | `Failure(LOCATION_NOT_FOUND, "folder not found: " + folder)` |
   | `NotDirectoryException` | `Failure(NOT_A_DIRECTORY, "not a directory: " + folder)` |
   | `LinkNotFollowedException` | `Failure(LOCATION_IS_LINK, "folder is a link, not followed: " + folder)` |
   | any other `IOException` `e` | `Failure(LOCATION_UNREADABLE, describe(e))`, the same rule as above |

   Non-`IOException` throwables propagate.
3. Copy the entries and sort them, stably, with `Comparator.comparing(e -> folder.getFileSystem().getPath(e.name()))`, which is `Path.compareTo` and so follows the platform's case rule.
4. Return `Success(new DirectoryListing(folder, sorted))`.

## State and transitions

None: this use case has no lifecycle.

## Concurrency and atomicity

- Which thread runs what: everything runs synchronously on the caller's thread.
- Where per-request state lives: only in local variables. The service's only field is the reader, and the adapter's only field is the access seam.
- Races: the disk can change while it is being read. That is handled per child (an UNREADABLE child) and per folder (mapped by exception type). No shared state means no race to settle.

## Checklist disposition

1. Unknown or stale IDs: not applicable. The query takes no ID.
2. Results as values, bugs as exceptions: the four expected folder outcomes are `Failure` values, one of them the new `LOCATION_IS_LINK`. A `null` folder throws `NullPointerException`. Every `IOException` maps to a code; anything else is a bug and propagates. There are no optional arguments, so no overloads.
3. Status model: not applicable, no lifecycle.
4. Races and who wins: not applicable, no shared state. A disk change during the read is item 5's case.
5. Validation before side effects: there are no side effects. The folder is checked (attributes, without following links) before it is opened. If the folder changes between the check and the open (it is removed, or replaced by a file), the open's exception maps by type, so the outcome is still one of the four codes.
6. State placement: not applicable, no per-request state. The service holds only its port.
7. Progress and observation: not applicable. A one-level read is synchronous and reports no progress.
8. Caller-supplied callbacks: not applicable, no caller code is called.
9. Failures off the caller's thread: not applicable, nothing runs off the caller's thread.
10. Query results: the whole listing, unpaged (paging is the UI's), ordered by `Path.compareTo` of the names with kinds mixed. It is never partial on failure: a folder that cannot be read to the end is a `Failure`. One bad child is a `SkippedItem`, not a missing entry.
11. Executor contract: not applicable, no executor.
12. Retention: not applicable, nothing is retained.
13. Testability: there is no timing involved.
    - Real `@TempDir` trees, with symlinks and junctions made at test time through `Fixtures.createSymlinkOrSkip` and `Fixtures.createJunction` (with `assumeTrue(Fixtures.isWindows())`). Put these tests in the `infra.adapter` test package, where `Fixtures` is visible.
    - AC-5: `new ListDirectoryService(new NioDirectoryReader(fake))`, where `fake` is a `DirectoryAccess` whose `open` delegates to real NIO and whose `attributes` throws `new AccessDeniedException(p.toString(), null, "denied")` for one child name. Assert the other children come back as real items.
    - AC-7 LOCATION_UNREADABLE, either way:
      - a fake `DirectoryReader` that throws a known `IOException`, given to `ListDirectoryService` (an `application` test);
      - a `DirectoryAccess` whose `open(folder)` throws (an adapter test).

      Assert the code and that the message equals `thrown.getMessage()`. No `icacls` or chmod is needed.
    - AC-6: a fake `DirectoryReader` returning entries out of order, plus the real-disk scenario.
14. Exported surface: the port and the new types are in the already-exported `domain.usecases.query` and `domain.model`, and `listDirectory()` is in exported `domain.factory`. The port, exception, adapter, seam and helper are in non-exported `infra`. `module-info.java` is unchanged, and no first-slice type is replaced.

## Configuration

- None. Hidden, empty and default-excluded names are all listed; `ScanConfig` is not read.

## Expected breakage

None.

## Open design notes (as of the design phase)

- **Port exception for a link** (decision a): the JDK has no exception for this case, and the port has one method (ticket decision 5), so `LinkNotFollowedException` lives in `infra.port`.
- **Special-file folder** (decision b): ticket decision 4 does not say which code a reparse-point *file* given as the folder gets. It gets NOT_A_DIRECTORY, because it is not listable even if followed. A OneDrive or cloud reparse-point folder gets LOCATION_IS_LINK, which is consistent with the walker skipping it.
- **For the test-author:**
  - The NPE test uses `try { list(null); fail(); } catch (NullPointerException e) {}`, never `assertThrows`.
  - The AC-9 back-off test first lists through `ScannerFactories.listDirectory()` (DFS-022.L-1 pattern). The bean test calls `list` on the bean.
  - Exact message checks: LOCATION_UNREADABLE equals the thrown exception's message; the other three codes need only the code plus the folder as a substring.
  - Expected child paths are `folder.resolve(name)` on the folder as passed. Do not use `toRealPath()`: the reader resolves nothing.
  - AC-8 records names, sizes and mtimes before and after through `Files` calls in the test.
  - A test that builds a record with a null element (for example in `DirectoryListing`) builds it inside the throwing block, together with the `list` call.
- **Walker**: `NioFileTreeWalker` is not edited at design, so nothing breaks before implementation. The implementer's refactor must keep `NioFileTreeWalkerTest`, `NioProgressTest` and `ScanPipelineEndToEndTest` green unchanged.

## Stubs (as of the design phase)

- Files stubbed:
  - `application/ListDirectoryService.java` (`list`)
  - `infra/adapter/NioDirectoryReader.java` (`read`)
  - `infra/adapter/EntryKind.java` (`of`)
  - `infra/adapter/DirectoryAccess.java` (`NIO.open`, `NIO.attributes`)
- Written without checks or bodies:
  - `domain/usecases/query/ListDirectory.java`
  - `domain/model/{DirectoryListing,DirectoryEntry}.java`
  - `infra/port/{DirectoryReader,LinkNotFollowedException}.java`
- Edited:
  - `domain/model/ErrorCode.java`
  - `domain/factory/ScannerFactories.java` (`listDirectory()`, real)
  - `scanner-spring/.../ScannerAutoConfiguration.java` (`listDirectory` bean, real)
- Each stub throws `new UnsupportedOperationException("not implemented: DFS-032")` per D2.
- Commit: filled in after the design commit.
